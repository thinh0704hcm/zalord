package events

import (
	"context"
	"encoding/json"

	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/handler"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/presence"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

// PresenceRelay subscribes to Redis presence:events and pushes each transition
// to the local clients that asked to watch that specific user. Runs as a
// long-lived goroutine started from main.
type PresenceRelay struct {
	pres   *presence.Registry
	router *handler.Router
}

func NewPresenceRelay(pres *presence.Registry, router *handler.Router) *PresenceRelay {
	return &PresenceRelay{pres: pres, router: router}
}

func (p *PresenceRelay) Run(ctx context.Context) {
	sub, ch := p.pres.Subscribe(ctx)
	defer func() { _ = sub.Close() }()

	for {
		select {
		case <-ctx.Done():
			return
		case ev, ok := <-ch:
			if !ok {
				return
			}
			watchers := p.router.Watchers().Watchers(ev.UserId)
			if len(watchers) == 0 {
				continue
			}
			out := handler.Frame{Type: handler.OutPresence}
			out.Data, _ = json.Marshal(handler.PresenceEventPayload{
				UserId: ev.UserId,
				Status: ev.Status,
				At:     ev.At,
			})
			frameBytes, _ := json.Marshal(out)
			for _, c := range watchers {
				select {
				case c.Send <- frameBytes:
				default:
					logger.Log.Debug("presence push dropped (slow consumer)",
						zap.String("watcher", c.UserID.String()))
				}
			}
		}
	}
}
