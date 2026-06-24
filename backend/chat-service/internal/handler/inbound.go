package handler

import (
	"context"
	"encoding/json"
	"time"

	"github.com/google/uuid"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/membership"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/presence"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

// Frame is the envelope for every client→server and server→client WS message.
// Type tags the kind; Data is the kind-specific payload (raw to defer parse).
type Frame struct {
	Type string          `json:"type"`
	Data json.RawMessage `json:"data,omitempty"`
}

// Inbound types we accept from clients:
const (
	TypeTyping          = "typing"            // client says "I'm typing in conv X"
	TypePresenceQuery   = "presence.query"    // client asks for status of N users
	TypePresenceWatch   = "presence.watch"    // client asks to be pushed transitions
	TypePresenceUnwatch = "presence.unwatch"  // ... and cancel
)

// Outbound types (kept in one place to match what frontend will key on):
const (
	OutTyping        = "typing"
	OutPresence      = "presence"
	OutPresenceState = "presence.state" // batch reply to presence.query
	OutMessageRead   = "message.read"
	OutMessageCreate = "message.created"
)

// TypingPayload — both directions. isTyping=false signals manual stop;
// recipients normally also auto-clear after 5s of no further events.
type TypingPayload struct {
	ConversationId uuid.UUID `json:"conversationId"`
	IsTyping       bool      `json:"isTyping"`
	UserId         uuid.UUID `json:"userId,omitempty"` // server-side only on outbound
}

type PresenceQueryPayload struct {
	UserIds []uuid.UUID `json:"userIds"`
}

type PresenceStatePayload struct {
	States map[string]presence.Status `json:"states"` // userId.String() → status
}

type PresenceEventPayload struct {
	UserId uuid.UUID       `json:"userId"`
	Status presence.Status `json:"status"`
	At     time.Time       `json:"at"`
}

// Router dispatches inbound frames. State (registry + caches) is kept on the
// router so we can plug in more handlers without juggling deps in WsHandler.
type Router struct {
	registry   *session.Registry
	membership *membership.Cache
	presence   *presence.Registry
	watchers   *watcherIndex // map of watchee → set of watchers
}

func NewRouter(reg *session.Registry, mem *membership.Cache, pres *presence.Registry) *Router {
	return &Router{
		registry:   reg,
		membership: mem,
		presence:   pres,
		watchers:   newWatcherIndex(),
	}
}

// Handle runs in the readPump goroutine — keep it non-blocking. Anything
// slow (Redis, fan-out) is fine as long as we don't lock waiting on the
// outbound queue of another client. Membership lookups are sub-ms cache hits.
func (r *Router) Handle(ctx context.Context, sender *session.Client, raw []byte) {
	var f Frame
	if err := json.Unmarshal(raw, &f); err != nil {
		logger.Log.Debug("ws: bad inbound frame", zap.Error(err))
		return
	}
	switch f.Type {
	case TypeTyping:
		r.handleTyping(ctx, sender, f.Data)
	case TypePresenceQuery:
		r.handlePresenceQuery(ctx, sender, f.Data)
	case TypePresenceWatch:
		r.handlePresenceWatch(sender, f.Data, true)
	case TypePresenceUnwatch:
		r.handlePresenceWatch(sender, f.Data, false)
	default:
		logger.Log.Debug("ws: unknown frame type", zap.String("type", f.Type))
	}
}

// ── typing ────────────────────────────────────────────────────────────────

func (r *Router) handleTyping(ctx context.Context, sender *session.Client, raw json.RawMessage) {
	var p TypingPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return
	}
	if p.ConversationId == uuid.Nil {
		return
	}
	// Authorize: caller must be a member of the conv they're claiming to type in.
	if !r.membership.IsMember(ctx, p.ConversationId, sender.UserID) {
		logger.Log.Debug("ws: typing from non-member",
			zap.String("userId", sender.UserID.String()),
			zap.String("convId", p.ConversationId.String()))
		return
	}
	members := r.membership.Members(ctx, p.ConversationId)
	out := Frame{Type: OutTyping}
	out.Data, _ = json.Marshal(TypingPayload{
		ConversationId: p.ConversationId,
		IsTyping:       p.IsTyping,
		UserId:         sender.UserID,
	})
	frameBytes, _ := json.Marshal(out)
	r.fanOut(members, sender.UserID, frameBytes)
}

// ── presence query / watch ────────────────────────────────────────────────

func (r *Router) handlePresenceQuery(ctx context.Context, sender *session.Client, raw json.RawMessage) {
	var p PresenceQueryPayload
	if err := json.Unmarshal(raw, &p); err != nil || len(p.UserIds) == 0 {
		return
	}
	if len(p.UserIds) > 200 {
		p.UserIds = p.UserIds[:200]
	}
	statuses := r.presence.Batch(ctx, p.UserIds)
	stringified := make(map[string]presence.Status, len(statuses))
	for id, s := range statuses {
		stringified[id.String()] = s
	}
	out := Frame{Type: OutPresenceState}
	out.Data, _ = json.Marshal(PresenceStatePayload{States: stringified})
	frameBytes, _ := json.Marshal(out)
	select {
	case sender.Send <- frameBytes:
	default:
	}
}

func (r *Router) handlePresenceWatch(sender *session.Client, raw json.RawMessage, add bool) {
	var p PresenceQueryPayload
	if err := json.Unmarshal(raw, &p); err != nil {
		return
	}
	for _, watchee := range p.UserIds {
		if add {
			r.watchers.add(watchee, sender)
		} else {
			r.watchers.remove(watchee, sender)
		}
	}
}

func (r *Router) Watchers() *watcherIndex { return r.watchers }

// ── helpers ───────────────────────────────────────────────────────────────

// fanOut sends a frame to every member EXCEPT the sender. Bounded per
// connection — slow consumers drop the frame, never block fan-out.
func (r *Router) fanOut(members []uuid.UUID, exclude uuid.UUID, frame []byte) int {
	delivered := 0
	for _, uid := range members {
		if uid == exclude {
			continue
		}
		for _, c := range r.registry.Get(uid) {
			select {
			case c.Send <- frame:
				delivered++
			default:
			}
		}
	}
	return delivered
}
