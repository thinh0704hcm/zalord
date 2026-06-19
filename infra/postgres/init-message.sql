-- ─────────────────────────────────────────────────────────────────────────────
-- message-service schema
--
-- Owns:  conversations, conversation_members, direct_lookup, messages, outbox_events
-- NOTE:  user_id columns are UUIDs sourced from auth-service. They are NOT
--        foreign keys — cross-service references aren't enforced at the DB level
--        (each service owns its own DB; identity comes via events / X-User-Id).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('DIRECT', 'GROUP')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_conv_members_user ON conversation_members (user_id);

-- Deterministic dedup for 1-1 chats. pair_key = sorted(userA, userB) joined by '|'.
-- Ensures POST /conversations with type=DIRECT is idempotent: opening the
-- same chat twice returns the same conversation_id.
CREATE TABLE direct_lookup (
    pair_key         VARCHAR(80) PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE
);

CREATE TABLE messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        UUID NOT NULL,
    content          TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC);

-- Transactional outbox — events are written here in the SAME tx as the
-- business write, then a scheduler publishes them to RabbitMQ.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_exchange  VARCHAR(255) NOT NULL,
    routing_key     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
