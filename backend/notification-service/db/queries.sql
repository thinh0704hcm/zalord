-- name: InsertNotification :exec
INSERT INTO notifications (user_id, type, title, body, payload)
VALUES ($1, $2, $3, $4, $5);

-- name: ListByUser :many
SELECT id, user_id, type, title, body, payload, is_read, created_at, read_at
  FROM notifications
 WHERE user_id = $1
 ORDER BY created_at DESC
 LIMIT $2 OFFSET $3;

-- name: CountByUser :one
SELECT COUNT(*) FROM notifications WHERE user_id = $1;

-- name: CountUnreadByUser :one
SELECT COUNT(*) FROM notifications WHERE user_id = $1 AND is_read = false;

-- name: MarkOneRead :execrows
UPDATE notifications
   SET is_read = true, read_at = now()
 WHERE id = $1 AND user_id = $2 AND is_read = false;

-- name: MarkAllRead :execrows
UPDATE notifications
   SET is_read = true, read_at = now()
 WHERE user_id = $1 AND is_read = false;
