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
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL,
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT conv_members_unique UNIQUE (conversation_id, user_id)
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
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id             UUID NOT NULL,
    content               TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Recall ("thu hồi cho tất cả"): non-null = the sender retracted this message.
    -- We keep the row so pagination ordering + foreign references (replies that
    -- snapshot this message) don't break; clients render a placeholder.
    recalled_at           TIMESTAMPTZ,
    -- Reply: denormalized snapshot of the quoted message captured at send time.
    -- We snapshot instead of joining at read time so quotes survive recall +
    -- avoid an extra DB roundtrip on the history hot path.
    reply_to_message_id   UUID,
    reply_to_sender_id    UUID,
    reply_to_preview      VARCHAR(200)
);

CREATE INDEX idx_messages_conv_created ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_reply_to     ON messages (reply_to_message_id) WHERE reply_to_message_id IS NOT NULL;

-- Join table for media attachments. media_id is NOT a FK — media-service owns
-- its own database. Ownership/state of each media_id is validated synchronously
-- via media-service's ValidateAttachments gRPC before the message row lands.
CREATE TABLE message_attachments (
    message_id  UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    media_id    UUID NOT NULL,
    position    SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (message_id, media_id)
);

CREATE INDEX idx_msg_att_message ON message_attachments (message_id);

-- First-read receipts per message/user. A row means the user has read this
-- specific message; read_at is preserved as the first time that happened.
CREATE TABLE message_read_receipts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id       UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL,
    read_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT message_read_receipts_unique UNIQUE (message_id, user_id)
);

CREATE INDEX idx_msg_reads_message ON message_read_receipts (message_id, read_at DESC);
CREATE INDEX idx_msg_reads_conversation_user ON message_read_receipts (conversation_id, user_id);

-- ─── CQRS READ MODEL ─────────────────────────────────────────────────────────
-- conversation_views denormalises the inbox: one row per (user, conversation),
-- with cached last-message preview + unread_count. Updated by InboxProjector
-- consuming the message.created event (async, eventual consistency).
-- Frontend's inbox list reads from here with ONE indexed query.
CREATE TABLE conversation_views (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL,
    conversation_id       UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    other_user_id         UUID,                                 -- for DIRECT: the other party
    last_message_preview  VARCHAR(200),
    last_message_at       TIMESTAMPTZ,
    last_sender_id        UUID,
    unread_count          INT NOT NULL DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT conv_views_unique UNIQUE (user_id, conversation_id)
);

CREATE INDEX idx_view_user_last ON conversation_views (user_id, last_message_at DESC NULLS LAST);

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
