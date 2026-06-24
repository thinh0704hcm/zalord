package presence

import (
	"context"
	"encoding/json"
	"time"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

// Redis schema:
//   presence:online                 →  SET<userId string>  (for batch queries)
//   presence:hb:{userId}            →  TTL key (90s); refreshed every 30s by writePump
//   pubsub channel "presence:events"→  JSON {userId, status, at}
//
// Cross-instance: each chat-service instance OWNS the presence of its own
// connected clients (Add/Remove only fired from its own WS goroutines) but
// LISTENS to other instances' transitions over pub/sub so a watcher on
// instance A is notified when its watchee comes online on instance B.

const (
	OnlineSetKey     = "presence:online"
	HeartbeatPrefix  = "presence:hb:"
	HeartbeatTTL     = 90 * time.Second
	HeartbeatRefresh = 30 * time.Second
	EventsChannel    = "presence:events"
)

type Status string

const (
	StatusOnline  Status = "online"
	StatusOffline Status = "offline"
)

type Event struct {
	UserId uuid.UUID `json:"userId"`
	Status Status    `json:"status"`
	At     time.Time `json:"at"`
}

type Registry struct {
	rdb *redis.Client
	now func() time.Time
}

func New(rdb *redis.Client) *Registry {
	return &Registry{rdb: rdb, now: time.Now}
}

// MarkOnline is called when a user's FIRST WS connection lands on this
// instance. Idempotent SADD — if the user is already online (e.g. they have
// another connection elsewhere), SADD is a no-op and we skip the broadcast.
func (r *Registry) MarkOnline(ctx context.Context, userId uuid.UUID) {
	added, err := r.rdb.SAdd(ctx, OnlineSetKey, userId.String()).Result()
	if err != nil {
		logger.Log.Warn("presence SADD failed", zap.String("userId", userId.String()), zap.Error(err))
		return
	}
	if err := r.rdb.Set(ctx, HeartbeatPrefix+userId.String(), "1", HeartbeatTTL).Err(); err != nil {
		logger.Log.Warn("presence HB set failed", zap.Error(err))
	}
	if added > 0 {
		r.publish(ctx, Event{UserId: userId, Status: StatusOnline, At: r.now().UTC()})
	}
}

// MarkOffline is called when the user's LAST connection on this instance
// closes. We don't check "are they still online on another instance" because
// that requires global coordination — the heartbeat sweep does it cheaper.
// Trade-off: a multi-device user closing one tab may briefly broadcast
// offline; the next heartbeat re-marks them online. Acceptable for v1.
func (r *Registry) MarkOffline(ctx context.Context, userId uuid.UUID) {
	removed, err := r.rdb.SRem(ctx, OnlineSetKey, userId.String()).Result()
	if err != nil {
		logger.Log.Warn("presence SREM failed", zap.String("userId", userId.String()), zap.Error(err))
		return
	}
	_ = r.rdb.Del(ctx, HeartbeatPrefix+userId.String()).Err()
	if removed > 0 {
		r.publish(ctx, Event{UserId: userId, Status: StatusOffline, At: r.now().UTC()})
	}
}

// Heartbeat refreshes the TTL so a crashed instance's users eventually expire
// out of the online set instead of being stuck "online" forever.
func (r *Registry) Heartbeat(ctx context.Context, userId uuid.UUID) {
	_ = r.rdb.Set(ctx, HeartbeatPrefix+userId.String(), "1", HeartbeatTTL).Err()
}

// Batch returns online status for a list of users. Used by the presence.query
// inbound frame so the client can render initial state for a conversation.
func (r *Registry) Batch(ctx context.Context, userIds []uuid.UUID) map[uuid.UUID]Status {
	out := make(map[uuid.UUID]Status, len(userIds))
	if len(userIds) == 0 {
		return out
	}
	pipe := r.rdb.Pipeline()
	cmds := make(map[uuid.UUID]*redis.BoolCmd, len(userIds))
	for _, id := range userIds {
		cmds[id] = pipe.SIsMember(ctx, OnlineSetKey, id.String())
	}
	if _, err := pipe.Exec(ctx); err != nil && err != redis.Nil {
		logger.Log.Warn("presence batch failed", zap.Error(err))
	}
	for id, cmd := range cmds {
		if cmd.Val() {
			out[id] = StatusOnline
		} else {
			out[id] = StatusOffline
		}
	}
	return out
}

// Subscribe returns a channel of all presence events from any instance. The
// caller is responsible for closing the Subscription when done.
func (r *Registry) Subscribe(ctx context.Context) (*redis.PubSub, <-chan Event) {
	sub := r.rdb.Subscribe(ctx, EventsChannel)
	out := make(chan Event, 64)
	go func() {
		defer close(out)
		ch := sub.Channel()
		for msg := range ch {
			var e Event
			if err := json.Unmarshal([]byte(msg.Payload), &e); err != nil {
				continue
			}
			select {
			case out <- e:
			default:
				// Slow consumer — drop. Watchers don't need every transition.
			}
		}
	}()
	return sub, out
}

func (r *Registry) publish(ctx context.Context, e Event) {
	payload, _ := json.Marshal(e)
	if err := r.rdb.Publish(ctx, EventsChannel, payload).Err(); err != nil {
		logger.Log.Warn("presence publish failed", zap.Error(err))
	}
}
