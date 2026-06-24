-- name: CreateProfile :exec
INSERT INTO profiles (
    user_id, display_name, phone_number
) VALUES ($1, $2, $3)
ON CONFLICT (user_id) DO NOTHING;

-- name: GetProfileByUserID :one
SELECT id, user_id, display_name, phone_number, avatar_url, created_at, deleted_at
  FROM profiles
 WHERE user_id = $1 AND deleted_at IS NULL;

-- name: GetProfileByID :one
SELECT id, user_id, display_name, phone_number, avatar_url, created_at, deleted_at
  FROM profiles
 WHERE id = $1 AND deleted_at IS NULL;

-- name: GetProfileByPhone :one
SELECT id, user_id, display_name, phone_number, avatar_url, created_at, deleted_at
  FROM profiles
 WHERE phone_number = $1 AND deleted_at IS NULL;

-- name: ListProfiles :many
SELECT id, user_id, display_name, phone_number, avatar_url, created_at, deleted_at
  FROM profiles
 WHERE deleted_at IS NULL
 ORDER BY created_at DESC
 LIMIT $1 OFFSET $2;

-- name: CountProfiles :one
SELECT COUNT(*) FROM profiles WHERE deleted_at IS NULL;
