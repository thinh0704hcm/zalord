package service

import (
	"context"
	"encoding/json"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	queries "github.com/thinh0704hcm/zalord/backend/user-service/db/sqlc"
	"github.com/thinh0704hcm/zalord/backend/user-service/internal/event"
)

// MockProfileRepository is a manual mock for the ProfileRepository interface
type MockProfileRepository struct {
	CreateProfileFn   func(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error
	UpdateMyProfileFn func(ctx context.Context, userId uuid.UUID, displayName string, gender *string, dateOfBirth *time.Time, avatarUrl *string, notificationsEnabled *bool) (*queries.Profile, error)
	GetByUserIDFn     func(ctx context.Context, userId uuid.UUID) (*queries.Profile, error)
	GetByPhoneFn      func(ctx context.Context, phone string) (*queries.Profile, error)
	SearchByNameFn    func(ctx context.Context, name string, limit int32) ([]queries.Profile, error)
	ListFn            func(ctx context.Context, limit, offset int32) ([]queries.Profile, error)
	CountFn           func(ctx context.Context) (int64, error)
}

func (m *MockProfileRepository) CreateProfile(ctx context.Context, userId uuid.UUID, displayName, phoneNumber string) error {
	return m.CreateProfileFn(ctx, userId, displayName, phoneNumber)
}

func (m *MockProfileRepository) UpdateMyProfile(ctx context.Context, userId uuid.UUID, displayName string, gender *string, dateOfBirth *time.Time, avatarUrl *string, notificationsEnabled *bool) (*queries.Profile, error) {
	return m.UpdateMyProfileFn(ctx, userId, displayName, gender, dateOfBirth, avatarUrl, notificationsEnabled)
}

func (m *MockProfileRepository) GetByUserID(ctx context.Context, userId uuid.UUID) (*queries.Profile, error) {
	return m.GetByUserIDFn(ctx, userId)
}

func (m *MockProfileRepository) GetByPhone(ctx context.Context, phone string) (*queries.Profile, error) {
	return m.GetByPhoneFn(ctx, phone)
}

func (m *MockProfileRepository) SearchByName(ctx context.Context, name string, limit int32) ([]queries.Profile, error) {
	return m.SearchByNameFn(ctx, name, limit)
}

func (m *MockProfileRepository) List(ctx context.Context, limit, offset int32) ([]queries.Profile, error) {
	return m.ListFn(ctx, limit, offset)
}

func (m *MockProfileRepository) Count(ctx context.Context) (int64, error) {
	return m.CountFn(ctx)
}

func TestProfileService_ConsumeProfileCreated(t *testing.T) {
	userId := uuid.New()
	payload := event.UserCreatedPayload{
		UserID:      userId.String(),
		DisplayName: "Test User",
		PhoneNumber: "0987654321",
	}
	body, _ := json.Marshal(payload)

	mockRepo := &MockProfileRepository{
		CreateProfileFn: func(ctx context.Context, uid uuid.UUID, displayName, phoneNumber string) error {
			assert.Equal(t, userId, uid)
			assert.Equal(t, "Test User", displayName)
			assert.Equal(t, "0987654321", phoneNumber)
			return nil
		},
	}

	svc := NewProfileService(mockRepo)
	err := svc.ConsumeProfileCreated(context.Background(), body)

	assert.NoError(t, err)
}

func TestProfileService_UpdateMyProfile(t *testing.T) {
	userId := uuid.New()
	displayName := "Updated User"
	
	expectedProfile := &queries.Profile{
		UserID:      userId,
		DisplayName: displayName,
	}

	mockRepo := &MockProfileRepository{
		UpdateMyProfileFn: func(ctx context.Context, uid uuid.UUID, dName string, gender *string, dateOfBirth *time.Time, avatarUrl *string, notifs *bool) (*queries.Profile, error) {
			assert.Equal(t, userId, uid)
			assert.Equal(t, displayName, dName)
			return expectedProfile, nil
		},
	}

	svc := NewProfileService(mockRepo)
	prof, err := svc.UpdateMyProfile(context.Background(), userId, displayName, nil, nil, nil, nil)

	assert.NoError(t, err)
	assert.Equal(t, expectedProfile, prof)
}

func TestProfileService_GetByUserID(t *testing.T) {
	userId := uuid.New()
	expectedProfile := &queries.Profile{
		UserID:      userId,
		DisplayName: "Test User",
	}

	mockRepo := &MockProfileRepository{
		GetByUserIDFn: func(ctx context.Context, uid uuid.UUID) (*queries.Profile, error) {
			assert.Equal(t, userId, uid)
			return expectedProfile, nil
		},
	}

	svc := NewProfileService(mockRepo)
	prof, err := svc.GetByUserID(context.Background(), userId)

	assert.NoError(t, err)
	assert.Equal(t, expectedProfile, prof)
}

func TestProfileService_List(t *testing.T) {
	profiles := []queries.Profile{
		{DisplayName: "User 1"},
		{DisplayName: "User 2"},
	}

	mockRepo := &MockProfileRepository{
		ListFn: func(ctx context.Context, limit, offset int32) ([]queries.Profile, error) {
			assert.Equal(t, int32(20), limit)
			assert.Equal(t, int32(0), offset)
			return profiles, nil
		},
		CountFn: func(ctx context.Context) (int64, error) {
			return 2, nil
		},
	}

	svc := NewProfileService(mockRepo)
	items, total, err := svc.List(context.Background(), 1, 20)

	assert.NoError(t, err)
	assert.Equal(t, 2, len(items))
	assert.Equal(t, int64(2), total)
}
