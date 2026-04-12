# Design Patterns

This document explains the three core patterns used in Zalord and why they were chosen.

---

## 1. Transactional Outbox Pattern

### Problem
When Chat Service saves a message to PostgreSQL and then tries to publish it to Kafka, two things can go wrong:
- The DB write succeeds but the network drops before Kafka receives it → **message lost**
- The Kafka publish fails → the message is saved but never delivered

You cannot wrap a DB transaction and a Kafka publish in a single atomic operation. They are separate systems.

### Solution: Outbox Table

Write to both `messages` and `outbox` in a **single database transaction**. A separate process (Message Relay) reads the outbox and publishes to Kafka.

```
┌─────────────────────────────────────┐
│  PostgreSQL Transaction             │
│                                     │
│  INSERT INTO messages (...)         │
│  INSERT INTO outbox (              │
│    payload = <message JSON>,        │
│    status  = 'PENDING'             │
│  )                                  │
│                                     │
│  COMMIT  ◄── atomic                │
└─────────────────────────────────────┘
            │
            ▼
    Message Relay polls outbox
            │
            ▼
    Publish to Kafka
            │
            ▼
    Kafka ACK received
            │
            ▼
    UPDATE outbox SET status = 'PROCESSED'
```

### Outbox Table Schema
```sql
CREATE TABLE outbox (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id   UUID NOT NULL REFERENCES messages(id),
  payload      JSONB NOT NULL,
  status       TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
  retry_count  INT NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status ON outbox(status) WHERE status = 'PENDING';
```

### Message Relay — Safe Concurrent Polling
```sql
SELECT * FROM outbox
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

`SKIP LOCKED` allows multiple relay instances to run without stepping on each other.

### Retry Strategy
| Scenario | Behavior |
|---|---|
| Kafka publish timeout | Leave as PENDING, increment retry_count |
| retry_count > 5 | Set status = 'FAILED', alert/dead-letter |
| Relay crashes mid-publish | On restart, picks up PENDING records again (idempotent with Kafka `message_id` as key) |

### Guarantees
- **At-least-once delivery** to Kafka (Message Service must handle duplicates via `message_id` deduplication)
- **No message loss** as long as PostgreSQL is up
- DB and Kafka are never partially in sync

---

## 2. Redis Sequence Generator

### Problem
In a distributed system with multiple Chat Service instances, how do you ensure messages in a conversation are always ordered correctly, even when sent simultaneously from different clients?

Database `AUTO_INCREMENT` or `SERIAL` doesn't work across services. Timestamps have millisecond collisions and are unreliable for strict ordering.

### Solution: Redis INCR per Conversation

Redis is single-threaded. `INCR` on a key is atomic and returns a globally unique, monotonically increasing integer for that key.

```
Redis key: conversation:seq:{conv_id}
Operation: INCR conversation:seq:{conv_id} → returns next sequence_id
```

### Usage in Chat Service
```go
seqID, err := redisClient.Incr(ctx, fmt.Sprintf("conversation:seq:%s", convID)).Result()
if err != nil {
    // fallback: use PostgreSQL sequence (see below)
}
// seqID is now the authoritative ordering position for this message
```

### Redis AOF Configuration
Redis stores data in RAM. On restart without persistence, all sequence counters reset → sequence_id collisions in ScyllaDB.

```conf
# redis.conf
appendonly yes
appendfsync everysec   # flush to disk every second — balance of safety and performance
```

`appendfsync everysec` means at most 1 second of sequence data is lost on a crash, which is acceptable — Message Relay's re-sync script (below) handles recovery.

### Re-sync Script (Disaster Recovery)
If Redis is lost entirely:

```sql
-- For each active conversation, get the current max sequence_id from ScyllaDB
SELECT conv_id, MAX(sequence_id) as max_seq FROM messages GROUP BY conv_id;
```

```bash
# Load each max back into Redis
for each (conv_id, max_seq):
    redis-cli SET conversation:seq:{conv_id} {max_seq}
```

### Why Not PostgreSQL Sequence?
PostgreSQL sequences are safe but add a synchronous round-trip to the DB on every message send. At 10K concurrent users, this becomes a bottleneck. Redis INCR is ~10x faster and runs in memory.

---

## 3. Presigned URL for Media Uploads

### Problem
If clients upload files through Chat Service or Media Service, those services become the bottleneck for all binary data transfer — large files block message processing threads.

### Solution: Presigned URL (Direct-to-MinIO Upload)

The service only generates a time-limited, pre-authorized URL. The client uploads directly to MinIO without going through any application service.

```
Client                  Media Service              MinIO
  │                          │                       │
  │  POST /media/upload-url  │                       │
  │  { filename, mime_type } │                       │
  ├─────────────────────────▶│                       │
  │                          │  GeneratePresignedURL │
  │                          ├──────────────────────▶│
  │                          │◀──────────────────────│
  │  { upload_url, file_id } │                       │
  │◀─────────────────────────│                       │
  │                          │                       │
  │  PUT {upload_url} + file binary                  │
  ├──────────────────────────────────────────────────▶│
  │  200 OK                                          │
  │◀──────────────────────────────────────────────────│
  │                          │                       │
  │  POST /messages (with file_id in payload)        │
  ├─────────────────────────▶│                       │
```

### Presigned URL Generation (Go/Java with MinIO SDK)
```java
// Java (Media Service)
String presignedUrl = minioClient.getPresignedObjectUrl(
    GetPresignedObjectUrlArgs.builder()
        .method(Method.PUT)
        .bucket("zalord-media")
        .object(fileId + "/" + filename)
        .expiry(5, TimeUnit.MINUTES)
        .build()
);
```

### Download URLs
Do not store or expose permanent MinIO object URLs. Generate a Presigned GET URL on each download request (expires in 1 hour). This allows:
- Access revocation (delete the file → all URLs stop working)
- Future migration to S3 or another backend transparently

### File Validation
Media Service should validate before issuing a Presigned URL:
- Max file size (e.g., 100MB)
- Allowed MIME types (image/jpeg, image/png, video/mp4, application/pdf, etc.)
- User upload quota check

### Security Note
The Presigned URL is tied to a specific bucket/object path and HTTP method (PUT only). It cannot be used to read other files or perform other operations. Expiry of 5 minutes limits the window for abuse.
