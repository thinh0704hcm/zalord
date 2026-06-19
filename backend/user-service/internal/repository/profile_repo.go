package repository

import (
	"context"

	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
)

type ProfileRepository interface {
	CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error
	GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error)
}

type profileRepository struct {
	queries *queries.Queries
}

func NewProfileRepository(queries *queries.Queries) ProfileRepository {
	return &profileRepository{queries: queries}
}

func (r *profileRepository) CreateProfile(ctx context.Context, userId uuid.UUID, displayName string) error {
	return r.queries.CreateProfile(ctx, queries.CreateProfileParams{
		UserID:      userId,
		DisplayName: displayName,
	})
}

func (r *profileRepository) GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error) {
	prof, err := r.queries.GetProfileByUserID(ctx, userId)
	if err != nil {
		return nil, err
	}
	return &prof, nil
}
