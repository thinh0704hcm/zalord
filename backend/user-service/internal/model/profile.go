package model

import (
	"time"

	"github.com/google/uuid"
)

type Profile struct {
	ID          uuid.UUID  `json:"id"`
	UserID      uuid.UUID  `json:"user_id"`
	DisplayName string     `json:"display_name"`
	AvatarUrl            *string    `json:"avatar_url"`
	Gender               *string    `json:"gender"`
	DateOfBirth          *time.Time `json:"date_of_birth"`
	NotificationsEnabled *bool      `json:"notifications_enabled"`
	CreatedAt            time.Time  `json:"created_at"`
	DeletedAt            *time.Time `json:"deleted_at"`
}
