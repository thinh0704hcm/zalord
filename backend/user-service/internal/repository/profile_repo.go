package repository

import (
	"context"

	"github.com/google/uuid"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
)

type ProfileRepository interface {
	CreateProfile(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error
	GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error)
	GetByPhone(ctx context.Context, phone string) (*queries.Profile, error)
	List(ctx context.Context, limit, offset int32) ([]queries.Profile, error)
	Count(ctx context.Context) (int64, error)
}

type profileRepository struct {
	queries *queries.Queries
}

func NewProfileRepository(queries *queries.Queries) ProfileRepository {
	return &profileRepository{queries: queries}
}

func (r *profileRepository) CreateProfile(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error {
	return r.queries.CreateProfile(ctx, queries.CreateProfileParams{
		UserID:      userId,
		DisplayName: displayName,
		PhoneNumber: phoneNumber,
	})
}

func (r *profileRepository) GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error) {
	prof, err := r.queries.GetProfileByUserID(ctx, userId)
	if err != nil {
		return nil, err
	}
	return &prof, nil
}

func (r *profileRepository) GetByPhone(ctx context.Context, phone string) (*queries.Profile, error) {
	prof, err := r.queries.GetProfileByPhone(ctx, phone)
	if err != nil {
		return nil, err
	}
	return &prof, nil
}

func (r *profileRepository) List(ctx context.Context, limit, offset int32) ([]queries.Profile, error) {
	return r.queries.ListProfiles(ctx, queries.ListProfilesParams{Limit: limit, Offset: offset})
}

func (r *profileRepository) Count(ctx context.Context) (int64, error) {
	return r.queries.CountProfiles(ctx)
}
