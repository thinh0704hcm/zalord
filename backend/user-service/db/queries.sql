-- name: CreateProfile :exec
INSERT INTO profiles (
    user_id, display_name
) VALUES ($1, $2)
ON CONFLICT (user_id) DO NOTHING;