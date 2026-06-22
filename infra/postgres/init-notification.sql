-- ─────────────────────────────────────────────────────────────────────────────
-- notification-service schema
--
-- Per-user notification feed (bell icon). Each row = one "thing happened that
-- concerns this user" event. Built by consuming message.created + group.*
-- events. Distinct from CQRS inbox (which is conversation-level unread).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,                          -- recipient
    type        VARCHAR(50) NOT NULL,                   -- NEW_MESSAGE | GROUP_INVITE | ...
    title       VARCHAR(200),
    body        TEXT,
    payload     JSONB,                                  -- extra context for frontend deep-links
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);

-- Hot path: "show me my unread notifications sorted newest first"
CREATE INDEX idx_notif_user_unread ON notifications (user_id, is_read, created_at DESC);
CREATE INDEX idx_notif_user_created ON notifications (user_id, created_at DESC);
