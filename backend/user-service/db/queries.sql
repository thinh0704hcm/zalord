-- name: CreateProfile :one
INSERT INTO profiles (
    user_id, display_name
) VALUES ($1, $2)
    RETURNING *;