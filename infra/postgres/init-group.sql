-- ─────────────────────────────────────────────────────────────────────────────
-- group-service schema
--
-- Owns:  groups, group_members, outbox_events
-- Convention: group.id IS REUSED as message-service.conversation.id (same UUID)
--            so the chat layer doesn't need a separate mapping table — it
--            consumes group.created and inserts a Conversation with the same id.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE groups (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    avatar_url   TEXT,
    created_by   UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ
);

CREATE TABLE group_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL,
    role        VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT group_members_unique UNIQUE (group_id, user_id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_exchange  VARCHAR(255) NOT NULL,
    routing_key     VARCHAR(255) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
