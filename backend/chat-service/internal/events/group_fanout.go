package events

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/membership"
	"github.com/thinh0704hcm/zalord/backend/chat-service/internal/session"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/mq"
	"go.uber.org/zap"
)

type GroupMemberEvent struct {
	GroupId uuid.UUID `json:"groupId"`
	UserId  uuid.UUID `json:"userId"`
}

type GroupEventFanOut struct {
	registry  *session.Registry
	memCache  *membership.Cache
	eventType string // "group.member.added" or "group.member.removed"
}

func NewGroupEventFanOut(reg *session.Registry, memCache *membership.Cache, eventType string) *GroupEventFanOut {
	return &GroupEventFanOut{
		registry:  reg,
		memCache:  memCache,
		eventType: eventType,
	}
}

func (f *GroupEventFanOut) Handle(ctx context.Context, body []byte) error {
	var event GroupMemberEvent
	if err := json.Unmarshal(body, &event); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal GroupMemberEvent: %w", err)}
	}

	frame, err := json.Marshal(map[string]any{
		"type": f.eventType,
		"data": map[string]any{
			"conversationId": event.GroupId,
			"userId":         event.UserId,
		},
	})
	if err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("marshal frame: %w", err)}
	}

	members := f.memCache.Members(ctx, event.GroupId)
	memberMap := make(map[uuid.UUID]bool)
	for _, m := range members {
		memberMap[m] = true
	}
	// Always push to the affected user (especially if they were just removed from cache)
	memberMap[event.UserId] = true

	delivered := 0
	for m := range memberMap {
		for _, c := range f.registry.Get(m) {
			select {
			case c.Send <- frame:
				delivered++
			default:
				logger.Log.Warn("ws send buffer full for group event",
					zap.String("userId", m.String()))
			}
		}
	}

	logger.Log.Debug("group event fan-out done",
		zap.String("eventType", f.eventType),
		zap.String("groupId", event.GroupId.String()),
		zap.Int("delivered", delivered))
	return nil
}
