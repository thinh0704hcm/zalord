package membership

import (
	"context"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"github.com/thinh0704hcm/zalord/backend/chat-service/pkg/logger"
	"go.uber.org/zap"
)

// Cache is read-only here — message-service is the writer. Schema:
//   conv:{conversationId}:members  →  SET<userId string>
//
// Used by typing + read-receipt fan-out to know whom to push to (we can't use
// the message.created event's recipientIds list because typing/read flow
// doesn't ride on that event).
type Cache struct {
	rdb *redis.Client
}

func New(rdb *redis.Client) *Cache {
	return &Cache{rdb: rdb}
}

// Members returns all member UUIDs of a conversation, parsed. On Redis error
// we return nil + log; callers treat that as "fan out to no-one" rather than
// failing the request — typing/seen are best-effort signals.
func (c *Cache) Members(ctx context.Context, conversationId uuid.UUID) []uuid.UUID {
	raw, err := c.rdb.SMembers(ctx, key(conversationId)).Result()
	if err != nil {
		logger.Log.Warn("membership SMEMBERS failed",
			zap.String("convId", conversationId.String()), zap.Error(err))
		return nil
	}
	out := make([]uuid.UUID, 0, len(raw))
	for _, s := range raw {
		if id, err := uuid.Parse(s); err == nil {
			out = append(out, id)
		}
	}
	return out
}

// IsMember is cheaper than fetching the full set when we just need a yes/no.
func (c *Cache) IsMember(ctx context.Context, conversationId, userId uuid.UUID) bool {
	ok, err := c.rdb.SIsMember(ctx, key(conversationId), userId.String()).Result()
	if err != nil {
		logger.Log.Warn("membership SISMEMBER failed",
			zap.String("convId", conversationId.String()), zap.Error(err))
		return false
	}
	return ok
}

func key(id uuid.UUID) string { return "conv:" + id.String() + ":members" }
