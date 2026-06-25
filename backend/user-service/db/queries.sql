-- name: CreateProfile :exec
INSERT INTO profiles (
    user_id, display_name, phone_number
) VALUES ($1, $2, $3)
ON CONFLICT (user_id) DO NOTHING;

-- name: GetProfileByUserID :one
SELECT id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at
  FROM profiles
 WHERE user_id = $1 AND deleted_at IS NULL;

-- name: GetProfileByID :one
SELECT id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at
  FROM profiles
 WHERE id = $1 AND deleted_at IS NULL;

-- name: GetProfileByPhone :one
SELECT id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at
  FROM profiles
 WHERE phone_number = $1 AND deleted_at IS NULL;

-- name: ListProfiles :many
SELECT id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at
  FROM profiles
 WHERE deleted_at IS NULL
 ORDER BY created_at DESC
 LIMIT $1 OFFSET $2;

-- name: CountProfiles :one
SELECT COUNT(*) FROM profiles WHERE deleted_at IS NULL;

-- name: SearchProfilesByName :many
SELECT id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at
  FROM profiles
 WHERE deleted_at IS NULL
   AND display_name ILIKE '%' || $1 || '%'
 ORDER BY display_name ASC, created_at DESC
 LIMIT $2;


-- name: UpdateMyProfile :one
UPDATE profiles
   SET display_name = $2,
       gender = $3,
       date_of_birth = $4,
       avatar_url = COALESCE($5, avatar_url),
       notifications_enabled = COALESCE($6, notifications_enabled)
 WHERE user_id = $1 AND deleted_at IS NULL
 RETURNING id, user_id, display_name, phone_number, avatar_url, gender, date_of_birth, notifications_enabled, created_at, deleted_at;
