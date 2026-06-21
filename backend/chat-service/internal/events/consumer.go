package events

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/mq"
	"go.uber.org/zap"
)

// FanOut consumes message.created events and delivers them to every
// online recipient's WebSocket connection(s). This is the "push" half of
// the chat realtime story — message-service does the writing, this service
// does the pushing.
type FanOut struct {
	registry *session.Registry
}

func NewFanOut(reg *session.Registry) *FanOut {
	return &FanOut{registry: reg}
}

func (f *FanOut) Handle(ctx context.Context, body []byte) error {
	var event MessageCreatedEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal MessageCreated: %w", err)}
	}

	// Frame wraps the event with a type tag so the client can route different
	// kinds of pushes (later: typing, read receipt, presence). Today there's
	// just one type.
	frame, err := json.Marshal(map[string]any{
		"type": "message.created",
		"data": event,
	})
	if err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("marshal frame: %w", err)}
	}

	delivered := 0
	for _, recipientId := range event.RecipientIds {
		clients := f.registry.Get(recipientId)
		for _, c := range clients {
			select {
			case c.Send <- frame:
				delivered++
			default:
				// Send channel full = slow consumer. Drop the frame for THAT
				// connection rather than block the whole projector. Real
				// recovery would be: client reconnect + replay missed history
				// via REST GET /messages.
				logger.Log.Warn("ws send buffer full, dropping frame",
					zap.String("userId", recipientId.String()))
			}
		}
	}

	logger.Log.Debug("fan-out done",
		zap.String("messageId", event.MessageId.String()),
		zap.Int("recipients", len(event.RecipientIds)),
		zap.Int("delivered", delivered))
	return nil
}
