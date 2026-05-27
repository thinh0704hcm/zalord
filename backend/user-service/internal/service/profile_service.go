package service

import (
	"context"
	"encoding/json"

	"github.com/google/uuid"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/event"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/repository"
	"github.com/thinh0704hcm/zalord/backend/user-service/pkg/logger"
	"go.uber.org/zap"
)

type ProfileService interface {
	ConsumeProfileCreated(ctx context.Context, body []byte) error
	CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error
}

type profileService struct {
	profileRepo repository.ProfileRepository
}

func (p profileService) ConsumeProfileCreated(ctx context.Context, body []byte) error {
	var payload event.UserCreatedPayload
	if err := json.Unmarshal(body, &payload); err != nil {
		logger.Log.Error("unmarshal UserRegistered failed", zap.Error(err))
		return err
	}
	logger.Log.Info(
		"User registered successfully",
		zap.String("userId", payload.UserID),
		zap.String("displayName", payload.DisplayName))
	return nil
}

func (p profileService) CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error {
	prof, err := p.profileRepo.CreateProfile(ctx, userId, displayName)
	if err != nil {
		logger.Log.Error("create profile failed", zap.Error(err))
		return err
	}
	logger.Log.Info("create profile succeed", zap.Any("prof", prof))
	return nil
}

func NewProfileService(profileRepo repository.ProfileRepository) ProfileService {
	return &profileService{profileRepo: profileRepo}
}
