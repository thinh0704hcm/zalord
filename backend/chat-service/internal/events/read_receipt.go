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

// ReadReceiptFanOut consumes message.read events and pushes them over WS to
// every online member of the conversation (including the reader's other
// devices — multi-device sync of the "Seen" marker).
type ReadReceiptFanOut struct {
	registry   *session.Registry
	membership *membership.Cache
}

func NewReadReceiptFanOut(reg *session.Registry, mem *membership.Cache) *ReadReceiptFanOut {
	return &ReadReceiptFanOut{registry: reg, membership: mem}
}

func (r *ReadReceiptFanOut) Handle(ctx context.Context, body []byte) error {
	var event MessageReadEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal MessageRead: %w", err)}
	}

	members := r.membership.Members(ctx, event.ConversationId)
	if len(members) == 0 {
		// Cache cold or conv has no members in cache — best-effort: drop.
		// The reader has already had their inbox unread reset; sender's
		// "Seen" tick will catch up on the next event or reconnect.
		return nil
	}

	out := handler.Frame{Type: handler.OutMessageRead}
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
	logger.Log.Debug("read-receipt fan-out",
		zap.String("convId", event.ConversationId.String()),
		zap.String("reader", event.ReaderId.String()),
		zap.Int("delivered", delivered))
	return nil
}
