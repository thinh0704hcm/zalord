# Database Schemas — Per Service

zalord uses **database-per-service** with physical isolation: one Postgres container per service, one logical DB per container, no cross-container queries. Redis stays shared by design (it hosts cross-service coordination keys — see `docs/architecture.md`). ScyllaDB hosts the canonical message store (one shared keyspace, used only by message-service; other services never read it).

## Ground rules

1. **No cross-DB foreign keys.** Postgres can't enforce them across containers anyway. Where one service's table references another service's entity (e.g. `user-db.profiles.user_id` → `auth-db.users.id`), the column is a plain `UUID` with no `REFERENCES` clause. We call these **logical FKs**: real semantically, not enforced by the DB.

2. **A service only knows its own DB exists.** `auth-service` connects to `auth-pg`, never to `user-pg`. If `user-service` needs auth data, it gets it via the JWT (which carries `sub = user_id`) or via gRPC/REST/Kafka — never via SQL.

3. **Tables are owned by the service whose database they live in.** Each service ships its own migrations (Flyway for Java, golang-migrate for Go). `infra/postgres/` only enables `pgcrypto`; it doesn't declare service tables.

4. **All tables use `UUID` PKs** (`gen_random_uuid()`), all timestamps are `TIMESTAMPTZ`. Phone numbers are `VARCHAR(20)` in E.164 format (`+<country><number>`).

---

## auth-db — owned by `auth-service` (Week 1)

**Scope: identity only.** Phone number, password hash, soft-delete flag. Everything else about a user (display name, avatar, bio, presence, friendships) lives in `user-db`. Refresh tokens are in Redis only — see § Redis below.

```sql
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_number  VARCHAR(20) UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,                        -- BCrypt cost 12
  deleted_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_phone_active ON users(phone_number) WHERE is_deleted = false;
```

That's it. One table.

**What's intentionally NOT here:**

| Removed | Lives in | Why |
|---|---|---|
| `display_name`, `avatar_url`, `bio` | `user-db.profiles` | Identity vs profile is a clean cut. auth doesn't render UI; user-service does. |
| `device_type`, `last_active_at`, `user_sessions` table | (nowhere) | Multi-device tracking is YAGNI for Week 1. One refresh token per login, in Redis. |
| `fcm_token`, `vapid_endpoint`, `vapid_keys` | `notif-db.push_tokens` (Week 3+) | Push tokens belong to notification-service. auth doesn't push. |
| `refresh_tokens` table | Redis `refresh:{user_id}:{jti}` | Redis with AOF is durable enough for refresh tokens. Wiping Redis just forces re-login — acceptable. Add a DB table later if you need audit / "list my sessions". |

**Upgrade path** — if you later need refresh-token audit or survival across Redis wipes, add:

```sql
-- OPTIONAL, NOT in Week 1
CREATE TABLE refresh_tokens (
  jti         TEXT PRIMARY KEY,
  user_id     UUID NOT NULL,                      -- logical FK to users.id
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_expires_at ON refresh_tokens(expires_at);  -- cleanup cron
```

---

## user-db — owned by `user-service` (Week 1)

**Scope: everything about *who a user is* outside of credentials.** Profiles in Week 1; friendships and privacy settings join in Week 2+.

```sql
-- Week 1: profile (lazy-created on first PUT /users/me from the client)
CREATE TABLE profiles (
  user_id      UUID PRIMARY KEY,                  -- logical FK to auth-db.users.id
  display_name VARCHAR(100) NOT NULL,
  avatar_url   TEXT,
  bio          TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Week 2+ tables** (not part of Week 1 scope):

```sql
CREATE TABLE friendships (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_a      UUID NOT NULL,
  user_b      UUID NOT NULL,
  status      TEXT NOT NULL DEFAULT 'pending',    -- pending | accepted | blocked
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_a, user_b),
  CHECK (user_a <> user_b)
);
CREATE INDEX idx_friendships_user_a ON friendships(user_a);
CREATE INDEX idx_friendships_user_b ON friendships(user_b);

CREATE TABLE privacy_settings (
  user_id            UUID PRIMARY KEY,            -- logical FK to auth-db.users.id
  show_online_status BOOLEAN NOT NULL DEFAULT true,
  show_last_seen     BOOLEAN NOT NULL DEFAULT true,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Profile bootstrap.** Since `register` (auth-service) only takes `phone_number` + `password`, there's no profile row yet right after registration. The client calls `PUT /api/v1/users/me` with `display_name` (and optionally `avatar_url`, `bio`) right after — that creates the profile. `GET /api/v1/users/me` returns 404 if no profile exists yet, so the client can detect and prompt.

---

## chat-db — owned by `chat-service` (Week 2+)

Holds the **outbox** and outbox-staging copies of messages. Canonical messages live in ScyllaDB (see below); `chat-db.messages` exists only as the source rows the Transactional Outbox pattern (`docs/patterns.md`) drains into Kafka.

```sql
CREATE TABLE conversations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type        TEXT NOT NULL,                     -- dm | group
  ref_id      UUID NOT NULL,                     -- peer user_id (DM) or group_id (group)
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_members (
  conv_id     UUID NOT NULL,
  user_id     UUID NOT NULL,                     -- logical FK to auth-db.users.id
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (conv_id, user_id)
);
CREATE INDEX idx_conv_members_user_id ON conversation_members(user_id);

-- Outbox-staging. NOT the canonical store — that's ScyllaDB.
CREATE TABLE messages (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  conv_id         UUID NOT NULL,
  sender_id       UUID NOT NULL,
  sequence_id     BIGINT NOT NULL,
  content         TEXT,
  type            TEXT NOT NULL,                 -- text | image | video | file
  file_id         UUID,
  reply_to        BIGINT,
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
  message_id  UUID NOT NULL,
  old_content TEXT NOT NULL,
  edited_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_edits_message_id ON message_edits(message_id);

-- Transactional Outbox. Written in the same tx as `messages`. Drained by message-relay.
CREATE TABLE outbox (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id    UUID NOT NULL,
  event_type    TEXT NOT NULL,                   -- MESSAGE_SENT | MESSAGE_EDITED | MESSAGE_DELETED
  payload       JSONB NOT NULL,
  status        TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | PROCESSED | FAILED
  retry_count   INT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox(status, created_at) WHERE status = 'PENDING';
```

---

## group-db — owned by `group-service` (Week 2+)

```sql
CREATE TABLE groups (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        VARCHAR(100) NOT NULL,
  avatar_url  TEXT,
  description TEXT,
  owner_id    UUID NOT NULL,                     -- logical FK to auth-db.users.id
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
  group_id    UUID NOT NULL,
  user_id     UUID NOT NULL,
  role        TEXT NOT NULL DEFAULT 'member',    -- owner | admin | member
  joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_group_members_user_id ON group_members(user_id);

CREATE TABLE group_settings (
  group_id        UUID PRIMARY KEY,
  admin_only_send BOOLEAN NOT NULL DEFAULT false,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pinned_messages (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    UUID NOT NULL,
  message_id  UUID NOT NULL,                     -- logical FK to ScyllaDB message
  pinned_by   UUID NOT NULL,
  pinned_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE group_announcements (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id    UUID NOT NULL,
  content     TEXT NOT NULL,
  created_by  UUID NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMPTZ
);
CREATE INDEX idx_announcements_group_id ON group_announcements(group_id) WHERE deleted_at IS NULL;
```

---

## notif-db — owned by `notification-service` (Week 3+)

Push tokens live here, not in `auth-db`. auth-service doesn't need to know about user devices.

```sql
CREATE TABLE push_tokens (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,                    -- logical FK to auth-db.users.id
  platform     TEXT NOT NULL,                    -- fcm | webpush
  token        TEXT,                             -- FCM token (mobile)
  endpoint     TEXT,                             -- Web Push endpoint (browser)
  vapid_keys   JSONB,                            -- { p256dh, auth } — Web Push only
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_used_at TIMESTAMPTZ
);
CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);

CREATE TABLE notification_prefs (
  user_id       UUID NOT NULL,
  conv_id       UUID NOT NULL,
  muted         BOOLEAN NOT NULL DEFAULT false,
  muted_until   TIMESTAMPTZ,
  show_preview  BOOLEAN NOT NULL DEFAULT true,
  PRIMARY KEY (user_id, conv_id)
);
```

---

## media-db — owned by `media-service` (Week 3+)

```sql
CREATE TABLE media_files (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  uploader_id  UUID NOT NULL,                    -- logical FK to auth-db.users.id
  bucket       TEXT NOT NULL,
  object_key   TEXT NOT NULL,
  filename     TEXT NOT NULL,
  size         BIGINT NOT NULL,
  mime_type    TEXT NOT NULL,
  status       TEXT NOT NULL DEFAULT 'pending',  -- pending | uploaded | deleted
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_media_uploader ON media_files(uploader_id);
```

---

## search-db — owned by `message-service` (Week 3+)

Full-text search index. Populated by message-service after each text message hits ScyllaDB. Kept in Postgres because Scylla lacks built-in full-text search.

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

---

## ScyllaDB — single shared keyspace, owned by `message-service` (Week 2+)

Canonical message store. Every message ever sent ends up here, written by message-service after consuming `chat.messages` from Kafka.

```cql
CREATE KEYSPACE zalord WITH replication = {
  'class': 'SimpleStrategy',
  'replication_factor': 1                       -- single node for course project
};

USE zalord;

CREATE TABLE messages (
  conv_id        UUID,
  sequence_id    BIGINT,
  message_id     UUID,
  sender_id      UUID,
  content        TEXT,
  type           TEXT,                          -- text | image | video | file
  file_id        UUID,
  reply_to       BIGINT,
  is_deleted     BOOLEAN,
  deleted_at     TIMESTAMP,
  is_edited      BOOLEAN,
  last_edited_at TIMESTAMP,
  created_at     TIMESTAMP,
  PRIMARY KEY ((conv_id), sequence_id)
) WITH CLUSTERING ORDER BY (sequence_id DESC);

CREATE TABLE message_edits (
  message_id  UUID,
  edited_at   TIMESTAMP,
  old_content TEXT,
  PRIMARY KEY ((message_id), edited_at)
) WITH CLUSTERING ORDER BY (edited_at DESC);
```

---

## Redis — shared coordination layer

Redis is intentionally not split per service — its job is *cross-service* coordination. AOF is on; restarts preserve data, full crashes do not (acceptable for everything except in-flight refresh tokens, where re-login is the recovery path).

| Key Pattern | Type | TTL | Owner | Purpose |
|---|---|---|---|---|
| `refresh:{user_id}:{jti}` | STRING / JSON | `JWT_REFRESH_EXPIRY_DAYS` | auth-service | Active refresh token. Value: `{session_id, created_at}`. `DEL` on logout, missing → 401 on refresh. |
| `presence:{user_id}` | HASH | 5 min | user-service | `{status, last_seen}`. TTL refreshed by heartbeat; auto-expire = offline. |
| `user:sessions:{user_id}` | HASH | — | push-service (Week 2+) | `session_id → instance_id`. Powers multi-device fan-out. |
| `user:delivery:{session_id}` | PUBSUB | — | push-service (Week 2+) | Cross-instance WebSocket routing channel. |
| `conversation:seq:{conv_id}` | STRING | — | chat-service (Week 2+) | Monotonic `INCR` for per-conversation sequence IDs. AOF persisted. |
| `group:member_count:{group_id}` | STRING | 10 min | group-service (Week 2+) | Cached count for the typing-indicator group-size gate. |

---

## Where these CREATE TABLEs live in the repo

Two valid patterns:

**Pattern A — Migrations in each service (recommended long-term).** Each service ships its own `db/migration/` directory with versioned files. The service runs them on startup.

```
backend/auth-service/src/main/resources/db/migration/V1__auth_init.sql
backend/user-service/migrations/0001_profile.up.sql
backend/chat-service/migrations/0001_chat_init.up.sql
```

Pros: schema lives with the code that uses it; per-service migration history; standard practice; survives `make dev-reset` because the migration tool re-runs on every cold start.

**Pattern B — `infra/postgres/init-<svc>.sql`** (simpler, what you've started). Each Postgres container mounts only its own init file, run once on first boot.

Pros: zero tooling — just SQL files. Cons: no migration history; schema changes require `make dev-reset` (wipes the volume); harder to evolve once you have real data.

For Week 1, **B is fine** — schemas are small, you'll `dev-reset` often anyway. Move to A once schemas stabilize.

**One critical fix needed for Pattern B** — the current `docker-compose.yml` mounts the whole `./infra/postgres/` directory into both Postgres containers, so `init-auth.sql` would run in `user-pg` and vice versa. Scope each mount per file instead:

```yaml
auth-pg:
  volumes:
    - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/00-shared.sql:ro
    - ./infra/postgres/init-auth.sql:/docker-entrypoint-initdb.d/10-auth.sql:ro

user-pg:
  volumes:
    - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/00-shared.sql:ro
    - ./infra/postgres/init-user.sql:/docker-entrypoint-initdb.d/10-user.sql:ro
```
