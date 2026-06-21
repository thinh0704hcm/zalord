CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    type        VARCHAR(50) NOT NULL,
    title       VARCHAR(200),
    body        TEXT,
    payload     JSONB,
    is_read     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
