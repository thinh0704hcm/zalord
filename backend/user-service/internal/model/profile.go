package model

import (
	"time"

	"github.com/google/uuid"
)

type Profile struct {
	ID          uuid.UUID  `json:"id"`
	UserID      uuid.UUID  `json:"user_id"`
	DisplayName string     `json:"display_name"`
	AvatarUrl   *string    `json:"avatar_url"`
	CreatedAt   time.Time  `json:"created_at"`
	DeletedAt   *time.Time `json:"deleted_at"`
}
