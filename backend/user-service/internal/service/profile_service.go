package service

import (
	"context"
	"encoding/json"
	"fmt"

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
	CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error
	GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error)
}

type profileService struct {
	profileRepo repository.ProfileRepository
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

	logger.Log.Info("received UserCreated",
		zap.String("user_id", userID.String()),
		zap.String("display_name", payload.DisplayName))

	return p.CreateProfile(ctx, userID, payload.DisplayName)
}

func (p *profileService) CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error {
	if err := p.profileRepo.CreateProfile(ctx, userId, displayName); err != nil {
		logger.Log.Error("create profile failed", zap.Error(err))
		return err
	}
	logger.Log.Info("profile created (or already existed)",
		zap.String("user_id", userId.String()))
	return nil
}

func (p *profileService) GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error) {
	return p.profileRepo.GetByUserID(ctx, userId)
}

func NewProfileService(profileRepo repository.ProfileRepository) ProfileService {
	return &profileService{profileRepo: profileRepo}
}
