package service

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/notification-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/event"
	"github.com/thinh0704hcm/zalord/backend/notification-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/notification-service/pkg/mq"
	"go.uber.org/zap"
)

// NotificationService also doubles as the event handler — it owns the rules
// for "what events become notifications for which users". Kept in service so
// HTTP and consumer paths share the same persistence layer.
type NotificationService interface {
	List(ctx context.Context, userId uuid.UUID, page, size int) ([]queries.Notification, int64, error)
	UnreadCount(ctx context.Context, userId uuid.UUID) (int64, error)
	MarkRead(ctx context.Context, id, userId uuid.UUID) (bool, error)
	MarkAllRead(ctx context.Context, userId uuid.UUID) (int64, error)

	// Event handlers — invoked by the consumer.
	HandleMessageEvent(ctx context.Context, routingKey string, body []byte) error
	HandleGroupEvent(ctx context.Context, routingKey string, body []byte) error
}

type notificationService struct {
	repo repository.NotificationRepository
}

func New(repo repository.NotificationRepository) NotificationService {
	return &notificationService{repo: repo}
}

// ── Query side ───────────────────────────────────────────────────────────────

func (s *notificationService) List(ctx context.Context, userId uuid.UUID, page, size int) ([]queries.Notification, int64, error) {
	if page < 1 {
		page = 1
	}
	if size < 1 {
		size = 20
	}
	if size > 100 {
		size = 100
	}
	offset := (page - 1) * size
	items, err := s.repo.List(ctx, userId, int32(size), int32(offset))
	if err != nil {
		return nil, 0, err
	}
	total, err := s.repo.CountByUser(ctx, userId)
	return items, total, err
}

func (s *notificationService) UnreadCount(ctx context.Context, userId uuid.UUID) (int64, error) {
	return s.repo.CountUnread(ctx, userId)
}

func (s *notificationService) MarkRead(ctx context.Context, id, userId uuid.UUID) (bool, error) {
	n, err := s.repo.MarkRead(ctx, id, userId)
	return n > 0, err
}

func (s *notificationService) MarkAllRead(ctx context.Context, userId uuid.UUID) (int64, error) {
	return s.repo.MarkAllRead(ctx, userId)
}

// ── Projection: events → notifications ───────────────────────────────────────

func (s *notificationService) HandleMessageEvent(ctx context.Context, routingKey string, body []byte) error {
	if routingKey != "message.created" {
		return nil
	}
	var e event.MessageCreatedEvent
	if err := json.Unmarshal(body, &e); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal MessageCreated: %w", err)}
	}

	payload := mustJson(map[string]any{
		"conversationId": e.ConversationId,
		"messageId":      e.MessageId,
		"senderId":       e.SenderId,
		"attachmentIds":  e.AttachmentIds,
	})

	// Plain text wins for the preview; if the message is attachments-only,
	// fall back to a count badge so the bell shows something meaningful.
	preview := messagePreview(e.Content, len(e.AttachmentIds))

	// One notification per recipient. (No "you sent this" notification for the sender.)
	for _, recipient := range e.RecipientIds {
		if err := s.repo.Insert(ctx, recipient, "NEW_MESSAGE", "New message", preview, payload); err != nil {
			// Transient DB error — let consumer requeue.
			return err
		}
	}
	logger.Log.Debug("notif: NEW_MESSAGE fan-out",
		zap.String("messageId", e.MessageId.String()),
		zap.Int("recipients", len(e.RecipientIds)))
	return nil
}

func (s *notificationService) HandleGroupEvent(ctx context.Context, routingKey string, body []byte) error {
	switch routingKey {
	case "group.created":
		var e event.GroupCreatedEvent
		if err := json.Unmarshal(body, &e); err != nil {
			return &mq.PermanentError{Err: fmt.Errorf("unmarshal GroupCreated: %w", err)}
		}
		payload := mustJson(map[string]any{"groupId": e.GroupId, "groupName": e.Name})
		for _, m := range e.MemberIds {
			if m == e.CreatedBy {
				continue // don't notify the creator
			}
			title := "Added to group"
			body := "You were added to '" + e.Name + "'"
			if err := s.repo.Insert(ctx, m, "GROUP_INVITE", title, body, payload); err != nil {
				return err
			}
		}
	case "group.member.added":
		var e event.GroupMemberAddedEvent
		if err := json.Unmarshal(body, &e); err != nil {
			return &mq.PermanentError{Err: fmt.Errorf("unmarshal GroupMemberAdded: %w", err)}
		}
		payload := mustJson(map[string]any{"groupId": e.GroupId})
		if err := s.repo.Insert(ctx, e.UserId, "GROUP_INVITE",
			"Added to group", "You were added to a group", payload); err != nil {
			return err
		}
	default:
		// group.member.removed, group.updated — not notifying for now.
		return nil
	}
	return nil
}

// ── helpers ──────────────────────────────────────────────────────────────────

func truncate(s string, max int) string {
	if len(s) <= max {
		return s
	}
	return s[:max]
}

func messagePreview(content string, attachmentCount int) string {
	if content != "" {
		return truncate(content, 200)
	}
	if attachmentCount > 0 {
		return fmt.Sprintf("📎 %d tệp đính kèm", attachmentCount)
	}
	return ""
}

func mustJson(v any) []byte {
	b, _ := json.Marshal(v)
	return b
}
