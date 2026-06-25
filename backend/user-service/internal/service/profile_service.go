package service

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/event"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/mq"
	"go.uber.org/zap"
)

type ProfileService interface {
	ConsumeProfileCreated(ctx context.Context, body []byte) error
	CreateProfile(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error
	UpdateMyProfile(ctx context.Context, userId uuid.UUID, displayName string, gender *string, dateOfBirth *time.Time, avatarUrl *string, notificationsEnabled *bool) (*queries.Profile, error)
	GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error)
	GetByPhone(ctx context.Context, phone string) (*queries.Profile, error)
	SearchByName(ctx context.Context, name string, limit int) ([]queries.Profile, error)
	List(ctx context.Context, page, size int) ([]queries.Profile, int64, error)
}

type profileService struct {
	profileRepo repository.ProfileRepository
}

func NewProfileService(profileRepo repository.ProfileRepository) ProfileService {
	return &profileService{profileRepo: profileRepo}
}

func (p *profileService) ConsumeProfileCreated(ctx context.Context, body []byte) error {
	var payload event.UserCreatedPayload
	if err := json.Unmarshal(body, &payload); err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("unmarshal UserCreated: %w", err)}
	}

	userID, err := uuid.Parse(payload.UserID)
	if err != nil {
		return &mq.PermanentError{Err: fmt.Errorf("invalid userId %q: %w", payload.UserID, err)}
	}
	if payload.PhoneNumber == "" {
		return &mq.PermanentError{Err: fmt.Errorf("missing phoneNumber in UserCreated for %s", userID)}
	}

	logger.Log.Info("received UserCreated",
		zap.String("user_id", userID.String()),
		zap.String("display_name", payload.DisplayName),
		zap.String("phone_number", payload.PhoneNumber))

	return p.CreateProfile(ctx, userID, payload.DisplayName, payload.PhoneNumber)
}

func (p *profileService) CreateProfile(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error {
	if err := p.profileRepo.CreateProfile(ctx, userId, displayName, phoneNumber); err != nil {
		logger.Log.Error("create profile failed", zap.Error(err))
		return err
	}
	logger.Log.Info("profile created (or already existed)",
		zap.String("user_id", userId.String()))
	return nil
}

func (p *profileService) UpdateMyProfile(ctx context.Context, userId uuid.UUID, displayName string, gender *string, dateOfBirth *time.Time, avatarUrl *string, notificationsEnabled *bool) (*queries.Profile, error) {
	return p.profileRepo.UpdateMyProfile(ctx, userId, displayName, gender, dateOfBirth, avatarUrl, notificationsEnabled)
}

func (p *profileService) GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error) {
	return p.profileRepo.GetByUserID(ctx, userId)
}

func (p *profileService) GetByPhone(ctx context.Context, phone string) (*queries.Profile, error) {
	return p.profileRepo.GetByPhone(ctx, phone)
}

func (p *profileService) SearchByName(ctx context.Context, name string, limit int) ([]queries.Profile, error) {
	if limit < 1 {
		limit = 10
	}
	if limit > 20 {
		limit = 20
	}
	return p.profileRepo.SearchByName(ctx, name, int32(limit))
}

// List returns a page of profiles + total count for pagination metadata.
// page is 1-indexed. size is clamped to [1, 100].
func (p *profileService) List(ctx context.Context, page, size int) ([]queries.Profile, int64, error) {
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

	items, err := p.profileRepo.List(ctx, int32(size), int32(offset))
	if err != nil {
		return nil, 0, err
	}
	total, err := p.profileRepo.Count(ctx)
	if err != nil {
		return nil, 0, err
	}
	return items, total, nil
}
