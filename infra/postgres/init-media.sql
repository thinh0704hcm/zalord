-- ─────────────────────────────────────────────────────────────────────────────
-- media-service schema
-- Holds METADATA only — the bytes live in MinIO buckets.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE media (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL,
    kind            VARCHAR(20) NOT NULL CHECK (kind IN ('AVATAR', 'ATTACHMENT')),
    conversation_id UUID,                    -- only for ATTACHMENT (NULL for AVATAR)
    storage_key     VARCHAR(500) NOT NULL UNIQUE,
    mime_type       VARCHAR(100),
    size_bytes      BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'READY', 'DELETED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_at    TIMESTAMPTZ
);

CREATE INDEX idx_media_owner ON media (owner_id);
CREATE INDEX idx_media_conversation ON media (conversation_id) WHERE conversation_id IS NOT NULL;
