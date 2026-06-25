package events

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/membership"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/mq"
	"go.uber.org/zap"
)

// RecallFanOut consumes message.recalled events and pushes them to every
// online member of the conversation so live UIs blank the recalled bubble
// immediately. Independent consumer group from message.created so a slow
// recall path can't back up message delivery.
type RecallFanOut struct {
	registry   *session.Registry
	membership *membership.Cache
}

func NewRecallFanOut(reg *session.Registry, mem *membership.Cache) *RecallFanOut {
	return &RecallFanOut{registry: reg, membership: mem}
}

func (r *RecallFanOut) Handle(ctx context.Context, body []byte) error {
	var event MessageRecalledEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal MessageRecalled: %w", err)}
	}

	members := r.membership.Members(ctx, event.ConversationId)
	if len(members) == 0 {
		// Membership cache cold — drop. The bubble will be blanked on next
		// history fetch via REST (recalledAt is persistent).
		return nil
	}

	out := handler.Frame{Type: handler.OutMessageRecalled}
	out.Data, _ = json.Marshal(event)
	frame, _ := json.Marshal(out)

	delivered := 0
	for _, uid := range members {
		for _, c := range r.registry.Get(uid) {
			select {
			case c.Send <- frame:
				delivered++
			default:
			}
		}
	}
	logger.Log.Debug("recall fan-out",
		zap.String("convId", event.ConversationId.String()),
		zap.String("messageId", event.MessageId.String()),
		zap.Int("delivered", delivered))
	return nil
}
