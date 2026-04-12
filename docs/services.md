# Services

Detailed breakdown of each service: responsibilities, APIs, data ownership, and inter-service dependencies.

---

## 1. Auth Service (Java / Spring Boot)

### Responsibilities
- User registration: phone number, display name, password (bcrypt hashed)
- Login with phone number + password
- JWT access token issuance (short-lived, 15 min)
- Refresh token management per device session (long-lived, stored in Redis)
- Multi-device session tracking: each login creates a new session entry
- Logout single device or all devices (force-logout)
- Soft account deletion (marks user as deleted, data retained)

> **TBD:** Phone OTP verification on registration — decide whether to add SMS verification (requires Twilio/AWS SNS) or skip for course scope.

### API (REST — `/api/v1/auth`)
| Method | Path | Description |
|---|---|---|
| POST | `/register` | Register with phone, display_name, password |
| POST | `/login` | Login, returns access token + refresh token + session_id |
| POST | `/refresh` | Exchange refresh token for new access token |
| POST | `/logout` | Revoke current device session |
| POST | `/logout/all` | Revoke all sessions (all devices) |
| DELETE | `/account` | Soft-delete own account |

### Error Format
```json
{
  "code": "PHONE_ALREADY_REGISTERED",
  "message": "This phone number is already in use.",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

### Data Ownership
- `users` table in PostgreSQL (id, phone_number, display_name, password_hash, avatar_url, is_deleted, deleted_at, created_at)
- `user_sessions` table in PostgreSQL (id, user_id, device_type, refresh_token_jti, fcm_token, vapid_endpoint, created_at, last_active_at)
- Active sessions in Redis: `refresh:{user_id}:{token_jti}` with TTL

### Dependencies
- PostgreSQL
- Redis

---

## 2. User Service (Go)

### Responsibilities
- User profile management (display name, avatar, bio)
- Friend requests: send, accept, decline, block
- Contact list retrieval
- Online presence: set/get status (online, away, offline) with auto-expiry via Redis TTL
- Last seen timestamp
- Presence privacy settings: users can control who sees their online status / last seen
- Read receipts: mark conversation as read up to a sequence_id
- Typing indicators: broadcast typing state (DM always; groups only when member count < 50)

> **TBD:** Presence privacy scope — is this a **global toggle** (hide from everyone) or **per-contact** (hide from specific friends)? Per-contact requires a privacy check on every presence query.

### API (REST + gRPC — `/api/v1/users`)
| Method | Path | Description |
|---|---|---|
| GET | `/{id}` | Get user profile |
| PUT | `/me` | Update own profile (display name, avatar, bio) |
| GET | `/me/privacy` | Get current privacy settings |
| PUT | `/me/privacy` | Update privacy settings (show_online, show_last_seen) |
| POST | `/friends/request` | Send friend request |
| PUT | `/friends/request/{id}` | Accept or decline friend request |
| DELETE | `/friends/{id}` | Remove friend |
| POST | `/friends/{id}/block` | Block user |
| GET | `/friends` | List friends / contacts |
| GET | `/{id}/presence` | Get online status (respects privacy settings) |
| POST | `/typing` | Broadcast typing indicator to conversation |
| POST | `/read` | Mark messages as read up to sequence_id |

**gRPC (internal):**
- `GetUserInfo(user_id)` — called by Chat Service, Push Service, Message Service
- `GetPresence(user_id)` — called by Push Service before deciding to notify
- `GetPrivacySettings(user_id)` — called by Push Service

### Data Ownership
- `profiles` table in PostgreSQL (user_id, display_name, avatar_url, bio, updated_at)
- `friendships` table in PostgreSQL (user_a, user_b, status: pending/accepted/blocked, created_at)
- `privacy_settings` table in PostgreSQL (user_id, show_online_status, show_last_seen, updated_at)
- Presence in Redis: `presence:{user_id}` → `{status, last_seen}` with 5-min TTL (refreshed on heartbeat)
- Group member count cache in Redis: `group:member_count:{group_id}` (for typing indicator gate)

### Dependencies
- PostgreSQL
- Redis

---

## 3. Group Service (Java / Spring Boot)

### Responsibilities
- Create and disband groups (max 1,000 members)
- Add / remove members
- Role management: owner, admin, member
- Group metadata: name, avatar, description
- Admin-only send mode: restrict messaging to admins only
- Pinned messages: pin up to N messages per group
- Announcements: admin-created posts pinned at the top of the group

> **TBD:** Announcements — are these a special message type visible in the message feed, or a separate banner/section at the top of the group UI? Decision affects whether they go through the Chat Service flow or a separate endpoint.

### API (REST + gRPC — `/api/v1/groups`)
| Method | Path | Description |
|---|---|---|
| POST | `/` | Create a group |
| GET | `/{id}` | Get group info + settings |
| PUT | `/{id}` | Update metadata (name, avatar, description) |
| DELETE | `/{id}` | Disband group (owner only) |
| POST | `/{id}/members` | Add member(s) |
| DELETE | `/{id}/members/{user_id}` | Remove member |
| PUT | `/{id}/members/{user_id}/role` | Change member role |
| GET | `/{id}/members` | List members (paginated) |
| PUT | `/{id}/settings` | Toggle admin-only mode, etc. |
| POST | `/{id}/pins` | Pin a message |
| DELETE | `/{id}/pins/{message_id}` | Unpin a message |
| GET | `/{id}/pins` | List pinned messages |
| POST | `/{id}/announcements` | Create announcement (admin+) |
| GET | `/{id}/announcements` | List announcements |
| DELETE | `/{id}/announcements/{id}` | Delete announcement |

**gRPC (internal):**
- `IsMember(group_id, user_id)` — called by Chat Service before allowing send
- `GetMembers(group_id)` — called by Push Service for fan-out
- `GetMemberCount(group_id)` — called by User Service / Chat Service for typing gate
- `CanSend(group_id, user_id)` — checks admin-only mode + membership

### Data Ownership
- `groups` table in PostgreSQL (id, name, avatar_url, description, owner_id, created_at)
- `group_members` table in PostgreSQL (group_id, user_id, role, joined_at)
- `group_settings` table in PostgreSQL (group_id, admin_only_send, updated_at)
- `pinned_messages` table in PostgreSQL (group_id, message_id, pinned_by, pinned_at)
- `group_announcements` table in PostgreSQL (id, group_id, content, created_by, created_at, deleted_at)

### Dependencies
- PostgreSQL

---

## 4. Chat Service (Go + WebSocket)

### Responsibilities
- Manage persistent WebSocket connections for online users
- Authenticate WebSocket upgrade via JWT
- Receive outgoing messages from clients (text, image, video, file)
- Assign monotonically increasing `sequence_id` per conversation via Redis `INCR`
- Write message + outbox record atomically in a single PostgreSQL transaction
- Handle message edit: update content, write to `message_edits`, update outbox
- Handle unsend (soft delete): clear content, set `is_deleted=true`, write to outbox
- Forward typing events (with member count gate for groups) and read receipts via Redis Pub/Sub
- Register/deregister client session in Redis on connect/disconnect

### WebSocket Events (client → server)
| Event | Payload | Description |
|---|---|---|
| `message.send` | `{conv_id, content, type, reply_to?}` | Send a message |
| `message.edit` | `{message_id, new_content}` | Edit a sent message |
| `message.unsend` | `{message_id}` | Unsend a message (soft delete, clears content) |
| `typing.start` | `{conv_id}` | User started typing |
| `typing.stop` | `{conv_id}` | User stopped typing |
| `message.read` | `{conv_id, last_seq_id}` | Mark conversation read up to seq |

### WebSocket Events (server → client)
Delivered by **Push Service** — see Push Service section.

### Typing Gate (groups)
```go
memberCount := redis.Get("group:member_count:{conv_id}")
if conv.Type == "group" && memberCount >= 50 {
    // drop typing event, do not broadcast
    return
}
```

### Atomic Write (send)
```sql
BEGIN;
  INSERT INTO messages (id, conv_id, sender_id, sequence_id, content, type, reply_to, created_at)
  VALUES (...);
  INSERT INTO outbox (id, message_id, event_type, payload, status, created_at)
  VALUES (..., 'MESSAGE_SENT', ..., 'PENDING');
COMMIT;
```

### Atomic Write (edit)
```sql
BEGIN;
  INSERT INTO message_edits (id, message_id, old_content, edited_at) VALUES (...);
  UPDATE messages SET content = $new, is_edited = true, last_edited_at = now() WHERE id = $id;
  INSERT INTO outbox (id, message_id, event_type, payload, status, created_at)
  VALUES (..., 'MESSAGE_EDITED', ..., 'PENDING');
COMMIT;
```

### Atomic Write (unsend)
```sql
BEGIN;
  UPDATE messages SET content = NULL, is_deleted = true, deleted_at = now() WHERE id = $id;
  INSERT INTO outbox (id, message_id, event_type, payload, status, created_at)
  VALUES (..., 'MESSAGE_DELETED', ..., 'PENDING');
COMMIT;
```

### Data Ownership
- `conversations` table in PostgreSQL (id, type: dm/group, ref_id, created_at)
- `messages` table in PostgreSQL (staging + outbox source — canonical storage in ScyllaDB)
- `message_edits` table in PostgreSQL
- `outbox` table in PostgreSQL
- Sequence keys in Redis: `conversation:seq:{conv_id}`
- Session registry in Redis: `user:sessions:{user_id}` → HSET of `{session_id → instance_id}`

### Dependencies
- PostgreSQL
- Redis (INCR, Pub/Sub, session registry)
- User Service (gRPC: verify sender, check friendship)
- Group Service (gRPC: `IsMember`, `CanSend`)

---

## 5. Message Relay (Go)

### Responsibilities
- Continuously poll `outbox` for `PENDING` records
- Publish each record to Kafka topic `chat.messages` with `event_type` in the payload
- Mark records `PROCESSED` after Kafka ACK
- Retry with backoff; mark `FAILED` after 5 retries

### Processing Loop
```
LOOP every 100ms (or via Postgres LISTEN/NOTIFY):
  SELECT * FROM outbox WHERE status = 'PENDING'
  ORDER BY created_at ASC LIMIT 100
  FOR UPDATE SKIP LOCKED

  FOR each record:
    publish to Kafka → on ACK  → UPDATE status = 'PROCESSED'
                    → on FAIL  → increment retry_count
                               → if retry_count > 5 → set status = 'FAILED'
```

> `FOR UPDATE SKIP LOCKED` allows safe parallel relay instances.

### Kafka Message Envelope
```json
{
  "event_type": "MESSAGE_SENT | MESSAGE_EDITED | MESSAGE_DELETED",
  "message_id": "...",
  "conv_id": "...",
  "sender_id": "...",
  "payload": { ... }
}
```

### Data Ownership
- Reads/updates `outbox` table in PostgreSQL

### Dependencies
- PostgreSQL
- Kafka

---

## 6. Push Service (Go + WebSocket)

### Responsibilities
- Maintain active session registry (per-session WebSocket connections)
- Consume Kafka `chat.messages`
- Fan-out delivery to **all active sessions** of each recipient
- Use Redis Pub/Sub for cross-instance session routing
- Update session registry on connect/disconnect
- Emit presence update events to friends when user connects/disconnects

### Multi-Device Delivery Logic
```
Consume Kafka event for recipient User B:

  sessions = Redis HGETALL user:sessions:{user_b_id}
  // e.g. [{ session_1: instance_1 }, { session_2: instance_3 }]

  FOR each session:
    IF session connected to THIS instance:
      → deliver via WebSocket directly
    ELSE:
      → Redis PUBLISH user:delivery:{session_id} {message}
         (instance holding that session receives and delivers)

  IF sessions is empty:
    → skip (Notification Service handles offline delivery)
```

### WebSocket Events (server → client)
| Event | Payload |
|---|---|
| `message.new` | Full message object with sequence_id |
| `message.edited` | `{message_id, new_content, last_edited_at}` |
| `message.deleted` | `{message_id, conv_id}` |
| `message.read` | `{conv_id, last_seq_id, reader_id}` |
| `typing.indicator` | `{conv_id, user_id, typing: bool}` |
| `presence.update` | `{user_id, status, last_seen}` |

### Dependencies
- Kafka (consumer: `chat.messages`)
- Redis (Pub/Sub, session registry)
- User Service (gRPC: `GetPresence`, `GetPrivacySettings`)
- Group Service (gRPC: `GetMembers` for group fan-out)

---

## 7. Message Service (Java / Spring Boot)

### Responsibilities
- Consume `chat.messages` from Kafka
- Persist new messages to ScyllaDB
- Handle `MESSAGE_EDITED` events: update content in ScyllaDB, store edit in `message_edits`
- Handle `MESSAGE_DELETED` events: soft-delete in ScyllaDB (clear content, set `is_deleted=true`)
- Serve paginated message history (cursor = sequence_id)
- Per-conversation full-text search
- Global search across all conversations the user belongs to
- Delivery status tracking (sent → delivered → read)

### API (REST — `/api/v1/messages`)
| Method | Path | Description |
|---|---|---|
| GET | `/{conv_id}` | Message history, cursor-based pagination (`?before=seq_id&limit=50`) |
| GET | `/{conv_id}/{message_id}` | Single message |
| GET | `/{conv_id}/search?q=` | Search within a conversation |
| GET | `/search?q=` | Global search across user's conversations |
| GET | `/{conv_id}/{message_id}/edits` | Edit history for a message |

### ScyllaDB Schema
```cql
-- Main messages table
CREATE TABLE messages (
  conv_id        UUID,
  sequence_id    BIGINT,
  message_id     UUID,
  sender_id      UUID,
  content        TEXT,           -- NULL if is_deleted = true
  type           TEXT,           -- text | image | video | file
  file_id        UUID,
  reply_to       BIGINT,         -- sequence_id of replied message, nullable
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

### Search Strategy
- **Per-conversation:** PostgreSQL `tsvector` index on a `message_search` table (conv_id, message_id, content_tsv). Populated by Message Service after persisting to ScyllaDB.
- **Global:** Same table, query filtered by the list of conv_ids the user belongs to. Pagination by `ts_rank`.

### Dependencies
- Kafka (consumer: `chat.messages`)
- ScyllaDB
- PostgreSQL (search index)

---

## 8. Notification Service (Go)

### Responsibilities
- Consume `chat.messages` from Kafka
- Check if recipient has any active session (via Redis session registry)
- If fully offline: send push notification to all registered devices
- Support both **FCM** (Android/iOS) and **Web Push / VAPID** (browser)
- Toggle notification content: show message preview OR generic "You have a new message"
- Respect per-conversation mute settings

### Push Token Types
| Platform | Protocol | Token field |
|---|---|---|
| Android / iOS | FCM | `fcm_token` |
| Web browser | VAPID Web Push | `vapid_endpoint` + `vapid_keys` |

### Notification Payload
```json
// FCM (mobile)
{
  "to": "{fcm_token}",
  "notification": {
    "title": "Thinh Nguyen",
    "body": "Hey, are you free?"   // or "You have a new message" if preview disabled
  },
  "data": {
    "conv_id": "...",
    "message_id": "...",
    "type": "text"
  }
}

// Web Push (browser) — VAPID
{
  "title": "Thinh Nguyen",
  "body": "Hey, are you free?",
  "data": { "conv_id": "...", "message_id": "..." }
}
```

### Data Ownership
- `push_tokens` table in PostgreSQL (user_id, session_id, platform: fcm/webpush, token_or_endpoint, vapid_keys, created_at)
- `notification_prefs` table in PostgreSQL (user_id, conv_id, muted, muted_until, show_preview)

### Dependencies
- Kafka (consumer: `chat.messages`)
- PostgreSQL
- Redis (check session registry for online status)
- FCM API
- Web Push library (VAPID)

---

## 9. Media Service (Java / Spring Boot)

### Responsibilities
- Validate upload request (file size ≤ 100MB, allowed MIME types)
- Check user upload quota
- Generate MinIO Presigned PUT URL (5-min expiry)
- Generate MinIO Presigned GET URL per download request (1-hour expiry)
- Track file metadata in PostgreSQL
- Publish `media.uploaded` event to Kafka after client confirms upload

### Allowed MIME Types
`image/jpeg`, `image/png`, `image/gif`, `image/webp`, `video/mp4`, `video/quicktime`, `application/pdf`, `application/zip`, and common document types.

### Upload Flow
```
1. Client  →  POST /api/v1/media/upload-url  { filename, content_type, size }
2. Media Service validates size + MIME type
3. Media Service → MinIO SDK → Presigned PUT URL (expires 5 min)
4. Media Service → INSERT media_files (status=PENDING) → responds { upload_url, file_id }
5. Client  →  PUT {upload_url} + binary directly to MinIO
6. Client  →  POST /api/v1/media/{file_id}/confirm
7. Media Service → UPDATE media_files SET status=UPLOADED
8. Media Service → Kafka PUBLISH media.uploaded
9. Client  →  sends chat message with { file_id } in payload
```

### API (REST — `/api/v1/media`)
| Method | Path | Description |
|---|---|---|
| POST | `/upload-url` | Request Presigned upload URL |
| POST | `/{file_id}/confirm` | Confirm upload completed |
| GET | `/{file_id}` | Get file metadata + time-limited download URL |
| DELETE | `/{file_id}` | Delete file (uploader only, soft delete) |

### Data Ownership
- `media_files` table in PostgreSQL (id, uploader_id, bucket, object_key, filename, size, mime_type, status: pending/uploaded/deleted, created_at)

### Dependencies
- MinIO
- PostgreSQL
- Kafka (producer: `media.uploaded`)
