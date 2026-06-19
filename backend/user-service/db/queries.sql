-- name: CreateProfile :exec
INSERT INTO profiles (
    user_id, display_name
) VALUES ($1, $2)
ON CONFLICT (user_id) DO NOTHING;

-- name: GetProfileByUserID :one
SELECT id, user_id, display_name, avatar_url, created_at, deleted_at
  FROM profiles
 WHERE user_id = $1 AND deleted_at IS NULL;
