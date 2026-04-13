# Database Schemas

---

## Stage 1 — PostgreSQL (Modular Monolith)

Single PostgreSQL database `zalord`. Three schemas with no cross-schema FK constraints — identity by UUID convention only.

```sql
-- Schema: user
CREATE TABLE user.users (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  full_name    TEXT NOT NULL,
  email        TEXT,
  phone_number TEXT UNIQUE NOT NULL,
  birth_date   DATE,
  gender       TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMPTZ
);

-- Schema: auth
CREATE TABLE auth.credentials (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID UNIQUE,              -- references user.users by convention, no FK
  phone_number  TEXT UNIQUE NOT NULL,
  email         TEXT,
  password_hash TEXT NOT NULL,
  is_active     BOOLEAN NOT NULL DEFAULT true,
  last_login    TIMESTAMPTZ
);

-- Schema: messaging
CREATE TABLE messaging.chats (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_name        TEXT,
  chat_type        TEXT NOT NULL CHECK (chat_type IN ('DIRECT', 'GROUP', 'COMMUNITY')),
  last_activity_at TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ
);

CREATE TABLE messaging.messages (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  chat_id      UUID NOT NULL,
  sender_id    UUID NOT NULL,             -- references user.users by convention, no FK
  content_type TEXT NOT NULL CHECK (content_type IN ('TEXT', 'IMAGE', 'VIDEO', 'FILE', 'DELETED')),
  payload      JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at   TIMESTAMPTZ
);

CREATE TABLE messaging.chat_members (
  chat_id    UUID NOT NULL,
  member_id  UUID NOT NULL,
  role       TEXT NOT NULL CHECK (role IN ('MEMBER', 'ADMIN', 'OWNER')),
  deleted_at TIMESTAMPTZ,
  PRIMARY KEY (chat_id, member_id)
);

-- Indexes
CREATE INDEX messages_chat_id_idx ON messaging.messages(chat_id, created_at DESC);
CREATE INDEX chat_members_member_id_idx ON messaging.chat_members(member_id);
```

**Soft deletes:** `deleted_at` on `users`, `chats`, `messages`. Queries must filter `deleted_at IS NULL`.
**Migrations:** Managed by Flyway (after M1). Pre-M1: applied from `infrastructure/postgres/init.sql` on first container start.

---

## Stage 2 — PostgreSQL (Microservices)

## PostgreSQL — Relational Data

All tables use `UUID` primary keys (`gen_random_uuid()`). Timestamps are `TIMESTAMPTZ`.

---

### Auth & Sessions

```sql
CREATE TABLE users (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number    VARCHAR(20) UNIQUE NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  password_hash   TEXT NOT NULL,
  avatar_url      TEXT,
  is_deleted      BOOLEAN NOT NULL DEFAULT false,
  deleted_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_sessions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id),
  device_type     TEXT NOT NULL,          -- mobile | web | desktop
  refresh_token_jti TEXT NOT NULL UNIQUE,
  fcm_token       TEXT,                   -- nullable, mobile only
  vapid_endpoint  TEXT,                   -- nullable, browser only
  vapid_keys      JSONB,                  -- { p256dh, auth }
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_active_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_user_id ON user_sessions(user_id);
```

---

### User Profiles & Relationships

```sql
CREATE TABLE profiles (
  user_id     UUID PRIMARY KEY REFERENCES users(id),
  bio         TEXT,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE friendships (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_a      UUID NOT NULL REFERENCES users(id),
  user_b      UUID NOT NULL REFERENCES users(id),
  status      TEXT NOT NULL DEFAULT 'pending', -- pending | accepted | blocked
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_a, user_b),
  CHECK (user_a <> user_b)
);

CREATE INDEX idx_friendships_user_a ON friendships(user_a);
CREATE INDEX idx_friendships_user_b ON friendships(user_b);

CREATE TABLE privacy_settings (
  user_id             UUID PRIMARY KEY REFERENCES users(id),
  show_online_status  BOOLEAN NOT NULL DEFAULT true,
  show_last_seen      BOOLEAN NOT NULL DEFAULT true,
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

### Push Notifications

```sql
CREATE TABLE push_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL REFERENCES users(id),
  session_id    UUID NOT NULL REFERENCES user_sessions(id) ON DELETE CASCADE,
  platform      TEXT NOT NULL,     -- fcm | webpush
  token         TEXT,              -- FCM token (mobile)
  endpoint      TEXT,              -- Web Push endpoint (browser)
  vapid_keys    JSONB,             -- { p256dh, auth }
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);

CREATE TABLE notification_prefs (
  user_id       UUID NOT NULL REFERENCES users(id),
  conv_id       UUID NOT NULL,
  muted         BOOLEAN NOT NULL DEFAULT false,
  muted_until   TIMESTAMPTZ,
  show_preview  BOOLEAN NOT NULL DEFAULT true,
  PRIMARY KEY (user_id, conv_id)
);
```

---

### Groups

```sql
CREATE TABLE groups (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,
  avatar_url  TEXT,
  description TEXT,
  owner_id    UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
  group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES users(id),
  role        TEXT NOT NULL DEFAULT 'member',  -- owner | admin | member
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, user_id)
);

CREATE INDEX idx_group_members_user_id ON group_members(user_id);

CREATE TABLE group_settings (
  group_id        UUID PRIMARY KEY REFERENCES groups(id) ON DELETE CASCADE,
  admin_only_send BOOLEAN NOT NULL DEFAULT false,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pinned_messages (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  message_id  UUID NOT NULL,
  pinned_by   UUID NOT NULL REFERENCES users(id),
  pinned_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_announcements (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  content     TEXT NOT NULL,
  created_by  UUID NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_announcements_group_id ON group_announcements(group_id) WHERE deleted_at IS NULL;
```

---

### Conversations & Messages (PostgreSQL — staging + outbox)

```sql
CREATE TABLE conversations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type        TEXT NOT NULL,    -- dm | group
  ref_id      UUID NOT NULL,    -- user_id (DM) or group_id (group)
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
  conv_id     UUID NOT NULL REFERENCES conversations(id),
  user_id     UUID NOT NULL REFERENCES users(id),
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (conv_id, user_id)
);

CREATE INDEX idx_conv_members_user_id ON conversation_members(user_id);

-- Staging table (source of truth until persisted to ScyllaDB via Kafka)
CREATE TABLE messages (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conv_id         UUID NOT NULL REFERENCES conversations(id),
  sender_id       UUID NOT NULL REFERENCES users(id),
  sequence_id     BIGINT NOT NULL,
  content         TEXT,
  type            TEXT NOT NULL,    -- text | image | video | file
  file_id         UUID,
  reply_to        BIGINT,           -- sequence_id of parent message
  is_deleted      BOOLEAN NOT NULL DEFAULT false,
  deleted_at      TIMESTAMPTZ,
  is_edited       BOOLEAN NOT NULL DEFAULT false,
  last_edited_at  TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (conv_id, sequence_id)
);

CREATE INDEX idx_messages_conv_id ON messages(conv_id, sequence_id DESC);

CREATE TABLE message_edits (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id  UUID NOT NULL REFERENCES messages(id),
  old_content TEXT NOT NULL,
  edited_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_edits_message_id ON message_edits(message_id);

-- Transactional Outbox
CREATE TABLE outbox (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id    UUID NOT NULL REFERENCES messages(id),
  event_type    TEXT NOT NULL,   -- MESSAGE_SENT | MESSAGE_EDITED | MESSAGE_DELETED
  payload       JSONB NOT NULL,
  status        TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
  retry_count   INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at  TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox(status, created_at) WHERE status = 'PENDING';
```

---

### Message Search Index (PostgreSQL full-text)

```sql
CREATE TABLE message_search (
  message_id  UUID PRIMARY KEY,
  conv_id     UUID NOT NULL,
  sender_id   UUID NOT NULL,
  content_tsv TSVECTOR NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_message_search_conv ON message_search(conv_id);
CREATE INDEX idx_message_search_tsv  ON message_search USING GIN(content_tsv);
```

Populated by Message Service after persisting each `text` type message to ScyllaDB. Deleted messages remove their row from this table.

---

### Media Files

```sql
CREATE TABLE media_files (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  uploader_id  UUID NOT NULL REFERENCES users(id),
  bucket       TEXT NOT NULL,
  object_key   TEXT NOT NULL,
  filename     TEXT NOT NULL,
  size         BIGINT NOT NULL,
  mime_type    TEXT NOT NULL,
  status       TEXT NOT NULL DEFAULT 'pending',  -- pending | uploaded | deleted
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## ScyllaDB — Message Storage

Messages are the canonical long-term store. Read patterns are always: "give me messages for conversation X, ordered by sequence_id, starting from cursor Y".

```cql
CREATE KEYSPACE zalord WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1   -- single node for course project
};

USE zalord;

-- Main message store
CREATE TABLE messages (
  conv_id        UUID,
  sequence_id    BIGINT,
  message_id     UUID,
  sender_id      UUID,
  content        TEXT,
  type           TEXT,           -- text | image | video | file
  file_id        UUID,
  reply_to       BIGINT,
  is_deleted     BOOLEAN,
  deleted_at     TIMESTAMP,
  is_edited      BOOLEAN,
  last_edited_at TIMESTAMP,
  created_at     TIMESTAMP,
  PRIMARY KEY ((conv_id), sequence_id)
) WITH CLUSTERING ORDER BY (sequence_id DESC);

-- Edit history
CREATE TABLE message_edits (
  message_id  UUID,
  edited_at   TIMESTAMP,
  old_content TEXT,
  PRIMARY KEY ((message_id), edited_at)
) WITH CLUSTERING ORDER BY (edited_at DESC);
```

---

## Redis — Key Design

| Key Pattern | Type | TTL | Description |
|---|---|---|---|
| `refresh:{user_id}:{jti}` | STRING | 30d | Refresh token validity |
| `presence:{user_id}` | HASH | 5 min | `{status, last_seen}` — auto-expire = offline |
| `user:sessions:{user_id}` | HASH | — | `session_id → instance_id` (managed on connect/disconnect) |
| `user:delivery:{session_id}` | PUBSUB | — | Cross-instance WebSocket message routing |
| `conversation:seq:{conv_id}` | STRING | — | Monotonic sequence counter (AOF persisted) |
| `group:member_count:{group_id}` | STRING | 10 min | Cached member count for typing gate |
