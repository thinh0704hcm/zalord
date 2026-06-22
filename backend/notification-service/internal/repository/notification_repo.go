package repository

import (
	"context"

	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/notification-service/db/sqlc"
)

type NotificationRepository interface {
	Insert(ctx context.Context, userId uuid.UUID, ntype string, title, body string, payload []byte) error
	List(ctx context.Context, userId uuid.UUID, limit, offset int32) ([]queries.Notification, error)
	CountByUser(ctx context.Context, userId uuid.UUID) (int64, error)
	CountUnread(ctx context.Context, userId uuid.UUID) (int64, error)
	MarkRead(ctx context.Context, id, userId uuid.UUID) (int64, error)
	MarkAllRead(ctx context.Context, userId uuid.UUID) (int64, error)
}

type notificationRepository struct {
	q *queries.Queries
}

func New(q *queries.Queries) NotificationRepository {
	return &notificationRepository{q: q}
}

func (r *notificationRepository) Insert(ctx context.Context, userId uuid.UUID, ntype, title, body string, payload []byte) error {
	titlePtr := &title
	bodyPtr := &body
	if title == "" {
		titlePtr = nil
	}
	if body == "" {
		bodyPtr = nil
	}
	return r.q.InsertNotification(ctx, queries.InsertNotificationParams{
		UserID:  userId,
		Type:    ntype,
		Title:   titlePtr,
		Body:    bodyPtr,
		Payload: payload,
	})
}

func (r *notificationRepository) List(ctx context.Context, userId uuid.UUID, limit, offset int32) ([]queries.Notification, error) {
	return r.q.ListByUser(ctx, queries.ListByUserParams{UserID: userId, Limit: limit, Offset: offset})
}

func (r *notificationRepository) CountByUser(ctx context.Context, userId uuid.UUID) (int64, error) {
	return r.q.CountByUser(ctx, userId)
}

func (r *notificationRepository) CountUnread(ctx context.Context, userId uuid.UUID) (int64, error) {
	return r.q.CountUnreadByUser(ctx, userId)
}

func (r *notificationRepository) MarkRead(ctx context.Context, id, userId uuid.UUID) (int64, error) {
	return r.q.MarkOneRead(ctx, queries.MarkOneReadParams{ID: id, UserID: userId})
}

func (r *notificationRepository) MarkAllRead(ctx context.Context, userId uuid.UUID) (int64, error) {
	return r.q.MarkAllRead(ctx, userId)
}
