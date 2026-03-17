# giano — MVP Stage 1 Proposal
## Real-Time Messaging Platform (School Project — Trio Team)

> A modular monolith real-time chat application built with Spring Boot, WebSocket, Redis, and PostgreSQL.
> This proposal outlines a simplified, production-ready foundation for a team of three developers over 8–12 weeks.
> Focus: Core messaging logic, clean module boundaries, deliverable & testable.
> **Primary goal: establish a credible comparison baseline before microservices extraction.**

---

## Project Overview

**giano MVP** is a real-time chat platform where users can:
- Register and log in securely (JWT-based auth)
- Create or join chat rooms
- Send and receive messages in real-time (WebSocket)
- Reconnect and recover missed messages (sequence_id + DB catch-up)
- See who's online and typing (presence + activity indicators)

**Technology Stack:**
- Backend: Spring Boot 3.x + Spring Modulith (modular monolith)
- Real-time: WebSocket (STOMP), Redis Pub/Sub
- Database: PostgreSQL 16 + PgBouncer (transaction pooling — already configured)
- Frontend: Vite + React 18 + TypeScript + Tailwind CSS
- Infrastructure: Docker Compose (local dev + production)
- CI/CD: GitLab CI (test → build → deploy — already configured)
- Testing: JUnit 5, Testcontainers, Postman collection

**Team Structure (Trio):**
| Role | Responsibilities |
|------|------------------|
| **Backend (WebSocket & Messaging)** | WS server, Redis Pub/Sub routing, message persistence, sequence tracking |
| **Auth & User Module** | JWT auth, user profiles, session management |
| **DevOps & Frontend** | Docker Compose, GitLab CI, Vite frontend scaffold, integration testing, documentation |

**Scope Boundary:**
- ✅ Single-instance monolith (no multi-instance fleet scaling)
- ✅ PostgreSQL with PgBouncer in transaction pooling mode (already configured)
- ✅ WebSocket messaging flow (no external message brokers)
- ✅ Catch-up via DB query on reconnect
- ✅ Manual testing + JUnit + Testcontainers
- ❌ No email flows (verification and password reset are stubs only)
- ❌ No social login
- ❌ No multi-region, distributed consensus, or advanced chaos testing
- ❌ No paid features, analytics dashboards, or mobile apps

**Success Criteria:**
- 10 concurrent users chatting in one or multiple rooms with zero message loss
- JWT auth + login/signup fully functional
- Reconnect + catch-up working (users can refresh browser and see history)
- Full Docker Compose environment reproducible on any laptop
- Postman + JUnit tests covering happy path + key error cases
- Working Vite frontend with integrated WS client
- GitLab CI pipeline green on `main`

---

## Architecture Rule — Module Boundaries

**This is the most important constraint in the entire project.**

All cross-module communication must go through Spring application events — never direct `@Autowired` service injection across module boundaries. This maps the monolith's inter-module calls directly to the async messages (Kafka/RabbitMQ) that will carry the same payloads in the microservices version. The extraction then becomes a transport change, not a logic rewrite.

```java
// ❌ WRONG — direct cross-module injection, kills the baseline value
@Service
class MessagingService {
    @Autowired
    private UserService userService; // cross-module!
}

// ✅ CORRECT — event-driven, maps to Kafka topic in microservices version
@Service
class MessagingService {
    @Autowired
    private ApplicationEventPublisher events;

    public void sendMessage(...) {
        events.publishEvent(new MessageSentEvent(roomId, userId, text));
    }
}

@ApplicationModuleListener
class PresenceEventHandler {
    void on(MessageSentEvent event) { ... }
}
```

Add an `ApplicationModuleTest` in Phase 1 that runs `ApplicationModules.of(GianoApplication.class).verify()`. This test will **fail CI** if any cross-module dependency violations are introduced, protecting the baseline automatically.

**Module event map:**

| Module | Owns | Publishes | Listens To |
|--------|------|-----------|------------|
| `auth` | JWT issuance, refresh tokens, bcrypt | `UserRegisteredEvent` | — |
| `user` | User profiles, email verification stub | `UserUpdatedEvent` | `UserRegisteredEvent` |
| `room` | Room CRUD, membership | `UserJoinedRoomEvent`, `UserLeftRoomEvent` | `UserRegisteredEvent` |
| `messaging` | Message persistence, sequence_id, WS routing | `MessageSentEvent` | `UserJoinedRoomEvent`, `UserLeftRoomEvent` |
| `presence` | Online state in Redis, typing indicators | — | `MessageSentEvent`, `UserJoinedRoomEvent`, `UserLeftRoomEvent` |

---

## Core Features

### 1. Authentication & User Management

**Goal:**
Enable users to register, log in securely, and maintain authenticated sessions for the entire chat experience.

> **Scope note:** Email verification and password reset are **stubs only** in this version. Email verification auto-confirms on signup (dev mode flag). Password reset returns HTTP 200 with no actual email sent. Social login is deferred entirely.

**User Stories:**
- As a new user, I want to sign up with email and password so I can create an account and start chatting.
- As a returning user, I want to log in with my credentials to access my chats.
- As a logged-in user, I want to see and update my profile (name, avatar, status).
- As a user, I want to be automatically logged out after my session expires or when I log out manually.

**Flow/UX:**

**Sign Up Flow:**
```
User opens app
  → "Sign Up" button
  → Enter email, password, display name
  → Validation: email format, password strength, name not empty
  → Backend creates user record (hashed password with bcrypt)
  → email_verified = true immediately (dev stub — no email sent)
  → User redirected to login
  → User logs in with email + password
  → Backend generates JWT (exp: 15 min), refresh token (exp: 7 days)
  → Client stores both in localStorage
  → User redirected to room list / main chat page
```

**Login Flow:**
```
User enters email + password
  → Backend validates credentials
  → If valid: issue JWT + refresh token, return to client
  → Client stores tokens and redirects to dashboard
  → On every API/WS request, include JWT in Authorization header
  → If JWT expired but refresh token valid, silently refresh (no UX interruption)
  → If both expired: redirect to login page
```

**Profile Update:**
```
User opens profile page
  → Display current name, avatar, status (online/away/offline)
  → User edits fields (name, status, avatar upload)
  → Client sends PATCH /api/users/{id} with new data + JWT
  → Backend validates and updates DB
  → Return updated user object to client
  → UI updates immediately (optimistic update or refresh from response)
```

**Logout:**
```
User clicks "Logout"
  → Client clears localStorage (JWT + refresh token)
  → POST /api/auth/logout to backend (invalidate refresh token in DB)
  → Redirect to login page
  → WS connection closes on token validation failure
```

**Permission & Security:**
- All passwords stored with bcrypt (minimum 12 rounds), salted.
- JWT signed with HS256 (secret stored in environment variable, never in code).
- Refresh tokens stored in DB with user_id foreign key; invalidated on logout.
- All API endpoints require valid JWT except `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/reset-password`.
- WebSocket connection upgrade validated with JWT (sent as query param or header).

**Must-have:**
- Email/password registration with validation (email format, password: min 8 chars, at least 1 upper, 1 digit, 1 special).
- Secure login endpoint with JWT generation (access token: 15 min, refresh token: 7 days).
- Password hashing with bcrypt (min 12 rounds).
- Email verification stub: `email_verified = true` on signup, no email sent.
- Session/token management (refresh endpoint to issue new JWT without re-login).
- Basic profile management: display name, avatar (URL or simple emoji).
- Logout endpoint that invalidates refresh token.
- JWT validation middleware on all protected endpoints.
- WebSocket upgrade requires valid JWT.

**Should-have:**
- Account deactivation option (soft delete; can reactivate by logging in again).
- Session timeout notification (warn user 1 min before session expires).
- User presence status (online/away/offline) synced across rooms.

**Good-to-have:**
- Login attempt rate limiting (max 5 failed attempts per IP per 15 min, then cooldown).
- Password strength indicator on signup form.
- Remember-me checkbox (longer-lived refresh token for trusted devices).

**Deferred (out of scope):**
- ~~Email verification link~~ — stubbed; auto-verified on signup
- ~~Password reset via email~~ — stub endpoint, HTTP 200, no email
- ~~Social login (Google OAuth)~~ — deferred
- ~~CAPTCHA on sign-up~~ — deferred

**Edge Cases:**
- Duplicate Email: Show "Email already registered" and suggest login.
- Expired Refresh Token: Redirect to login page.
- Logout from All Devices: Invalidate all refresh tokens for that user at once.
- Session During Disconnect: WS reconnects automatically, uses refresh token if access token expired.

---

### 2. Chat Rooms & Room Management

**Goal:**
Enable users to create or discover chat rooms, join rooms, and see room metadata.

**User Stories:**
- As a user, I want to see a list of available rooms so I can join conversations I'm interested in.
- As a user, I want to create a new room with a name and description.
- As a user, I want to join a room by browsing the list or via invite link.
- As a room admin, I want to see members and remove disruptive users.
- As a user, I want to leave a room whenever I choose.

**Flow/UX:**

**Room Discovery & Join:**
```
User lands on dashboard
  → Show "My Rooms" sidebar (list of joined rooms)
  → Show "Browse Rooms" section with all public rooms
  → For each room: name, description, member count, last message preview
  → User clicks "Join" → backend adds user to room_members table
  → Room appears in sidebar; user can now send messages
```

**Create Room:**
```
User clicks "Create Room" button
  → Modal: enter room name (required), description (optional)
  → Backend validates: name not empty, name < 100 chars
  → Backend creates room record, adds creator as admin member
  → Return room ID + name to client
  → Open newly created room in chat view
```

**Leave Room:**
```
User clicks "Leave" on room in sidebar
  → Confirmation dialog
  → POST /api/rooms/{roomId}/leave
  → Backend removes user from room_members
  → Room disappears from sidebar
```

**Permission & Security:**
- All rooms are public; anyone can join without approval.
- Room admin (creator): can remove members and delete room.
- Regular members: can send messages and see room history.
- All endpoints require JWT; room access validated (user must be a member or room is public).

**Must-have:**
- Create room with name and optional description.
- List all public rooms (sorted by last activity).
- Join public room (add user to room_members, idempotent).
- Leave room (remove user from room_members).
- View list of rooms user is a member of (in sidebar).
- View members in a room (list of user names/avatars with online status).

**Should-have:**
- Room admin can remove members.
- Room admin can change room name or description.
- Room admin can delete room (soft delete; preserve message history).
- Search/filter rooms by name.
- Invite link to room (shareable URL that auto-joins).

**Good-to-have:**
- Room avatar/icon.
- Room archival.

**Edge Cases:**
- Non-existent Room: Show "Room not found" error.
- Member Removed Mid-Chat: WS connection closes; user sees "You have been removed" message.
- Room Permissions Race: Backend validates membership on message receipt; rejects if user was removed.

---

### 3. Real-Time Messaging (WebSocket)

**Goal:**
Enable users to send and receive messages instantly via WebSocket, with delivery to all room members.

**User Stories:**
- As a user, I want to type a message and send it; it should appear instantly for me and all others in the room.
- As a user, I want to see when other users are typing.
- As a user, I want to see messages in chronological order with sender name, timestamp, and content.
- As a user joining a room, I want to see recent message history (last 50 messages).
- As a user, if my internet drops, I want my connection to automatically reconnect and catch up on missed messages.

**Flow/UX:**

**Sending a Message:**
```
User types in message input field
  → User clicks Send or presses Enter
  → Client sends message via WS: { text, roomId, sequence_id, timestamp }
  → Backend receives message, validates:
     - User is member of roomId
     - Message text not empty and ≤ 5000 chars
     - Timestamp within 1 min of server time
  → Backend stores message: INSERT INTO messages (room_id, user_id, text, sequence_id, created_at)
  → Backend publishes to Redis Pub/Sub: "room:{roomId}"
  → All WS clients subscribed to that channel receive message in real-time
  → Client appends message to chat view with sender name, timestamp, avatar
```

**Message History (Catch-Up):**
```
User joins room or reconnects
  → Client requests: GET /api/rooms/{roomId}/messages?after_sequence_id=X&limit=50
  → Backend queries: SELECT * FROM messages WHERE room_id=roomId AND sequence_id > X
                     ORDER BY sequence_id DESC LIMIT 50
  → Return list of messages
  → Client sorts by sequence_id before rendering
  → Client updates lastSeqId = max(sequence_id) from response
```

**Real-Time Delivery (Single Server):**
```
User A → WS Server → @MessageMapping("/room/{roomId}/send")
  → Validate + persist to DB
  → Publish to Redis Pub/Sub "room:{roomId}"
  → Redis listener delivers to all connected WS clients in that room
```

**Typing Indicator:**
```
User starts typing (keydown, debounced 300ms)
  → Client sends WS message: { type: "typing", roomId }
  → Backend publishes to Redis: "room:{roomId}:typing"
  → All clients show "User X is typing..."
  → After 2 seconds of inactivity, client sends "stop typing"
  → Clients clear the indicator for that user
```

**Redis Pub/Sub Channels:**
| Channel | Carries |
|---------|---------|
| `room:{roomId}` | Live message payloads |
| `room:{roomId}:typing` | Typing start/stop events |
| `room:{roomId}:events` | User joined/left system events |

**Permissions & Security:**
- Only room members can send/receive messages.
- WS connection validated with JWT; invalid/expired tokens rejected.
- Message validation: non-empty, ≤ 5000 chars, parameterized queries only.
- All messages stored in DB; immutable after creation.

**Must-have:**
- WebSocket server (Spring Boot + STOMP).
- Send message to room (broadcast to all members).
- Store messages in DB (`messages` table: room_id, user_id, text, sequence_id, created_at).
- `sequence_id` tracking (per room; `UNIQUE(room_id, sequence_id)` constraint for deduplication).
- Catch-up on reconnect (fetch messages after last known sequence_id).
- Message validation (non-empty, ≤ 5000 chars, membership check).
- Display sender name, avatar, and timestamp.
- Typing indicators.

**Should-have:**
- Last-read message tracking.
- Message pagination (load older messages on scroll-up).
- Emoji support.

**Good-to-have:**
- Message editing (shows "edited" indicator).
- Message deletion (shows "[deleted]").
- Image/file sharing.
- Search messages.

**Edge Cases:**
- Message Sent While Offline: Client queues message; sends on reconnect.
- Duplicate Message: `UNIQUE(room_id, sequence_id)` constraint rejects re-inserts.
- Message Order Collision: Client sorts by sequence_id before rendering.
- User Removed Mid-Message: Backend validates membership on receipt; rejects with "not a member".
- Redis Pub/Sub Loss: At-most-once delivery accepted for live path; catch-up API covers gaps on reconnect.
- Very Long Message: Backend rejects with 400 "Message too long (max 5000 chars)".

---

### 4. Online Presence & Activity

**Goal:**
Show users who is currently online in a room and update presence in real-time.

**User Stories:**
- As a user, I want to see who is currently online in a room.
- As a user, I want my status to automatically update to "online" when I connect and "offline" when I disconnect.
- As a user, I want to see when someone joins or leaves a room.

**Flow/UX:**

**Presence On Join:**
```
User connects WS to a room
  → Backend: HSET room:{roomId}:members {userId: timestamp}
  → Backend publishes "user_joined" event to "room:{roomId}:events"
  → All clients update member list (add user, mark online)
  → System message in chat: "User X joined"
```

**Presence On Disconnect:**
```
User closes browser
  → Server-side onDisconnect hook fires
  → Backend: HDEL room:{roomId}:members {userId}
  → Backend publishes "user_left" event
  → Clients update member list (remove user, mark offline)
  → System message in chat: "User X left"
```

**Heartbeat:**
```
Server PINGs all WS clients every 30s
  → Client responds with PONG (framework handles automatically)
  → No PONG after 2 missed cycles (~60s): mark client disconnected
```

**Permissions & Security:**
- Presence data is public within a room (all members can see who is online).
- Presence data is ephemeral (stored in Redis; loss on restart is acceptable).
- Each user's presence is scoped to rooms they're a member of.
- Deduplication: same user connecting from two tabs is recorded once.

**Must-have:**
- Track online users per room (Redis Hash).
- Show online user list with avatars and status indicator.
- Add user to presence on WS connect; remove on WS disconnect.
- Broadcast presence changes to room subscribers.
- System messages for join/leave (gray, smaller text).

**Should-have:**
- Typing indicators (who is typing; clear after 2s inactivity).
- User status: online, idle (away for >5 min), offline.
- Last activity timestamp.

**Good-to-have:**
- Custom status message ("In a meeting", etc.).

**Edge Cases:**
- Ghost User: Tab closed without clean disconnect; shows online for ~60s until heartbeat timeout.
- Duplicate Online Entries: Deduplicate by checking if userId already in hash before adding.
- Stale Presence After Crash: Resolved by heartbeat timeout or client re-announce on reconnect.

---

### 5. Error Handling & Reconnection

**Goal:**
Handle network failures gracefully, auto-reconnect on disconnect, and show meaningful error messages.

**User Stories:**
- As a user, if my internet drops, I want the app to reconnect automatically without a page reload.
- As a user, if a message fails to send, I want to see an error and be able to retry.
- As a user, I want helpful error messages when something goes wrong.

**Flow/UX:**

**Auto-Reconnect:**
```
WS connection drops
  → Client detects disconnect (WS onclose event)
  → Show banner: "Reconnecting..."
  → Attempt reconnect with exponential backoff + ±20% jitter:
    1s → 2s → 4s → 8s → 16s → 30s (cap)
  → On success:
    - Re-subscribe to room channels
    - Fetch catch-up messages (after lastSeqId)
    - Update presence (user back online)
    - Hide reconnection banner
  → After 10 failed attempts:
    - Show: "Connection lost. Please refresh the page or check your connection."
    - Show manual refresh button
```

**Send Failure & Retry:**
```
User sends message but WS not connected
  → Message added to local queue; shown as "Pending..." in gray
  → On reconnect: flush queue in order
  → On success: message confirmed
  → On failure after flush: show "Failed to send. Retry?" with retry button
```

**Error Messages:**

| Situation | Message shown |
|-----------|---------------|
| Network error | "Network error: check your connection" |
| WS disconnect | "Connection lost. Reconnecting..." |
| 5xx response | "Server error: please try again" |
| Message too long | "Message too long (max 5000 chars)" |
| Not a room member | "You are not a member of this room" |
| Room not found | "Room not found" |
| 429 rate limit | "Server busy. Please try again in a moment." |
| 401 auth error | Redirect to login |

**Permissions & Security:**
- Auth errors (401): redirect to login.
- All errors logged server-side for debugging.

**Must-have:**
- Detect WS disconnection (client-side `onclose`).
- Auto-reconnect with exponential backoff + jitter (max 30s delay).
- Show reconnection status to user (banner).
- Fetch missed messages after reconnect.
- Queue messages during disconnect; send when reconnected.
- User-friendly error messages (toast, banner, or inline).
- Handle 401 by redirecting to login.

**Should-have:**
- Retry button on failed messages.
- Max retry limit with persistent error banner after N attempts.
- Distinguish network errors from app errors.

**Good-to-have:**
- Fallback UI mode (message history available, no real-time).
- Diagnostic indicator (connection status, last error, retry count).

**Edge Cases:**
- Continuous Network Failures: Retry indefinitely up to max; user can manually reload.
- Stale Message Cache: Catch-up API covers gaps; older messages load on page refresh.
- User Removed During Reconnect: Membership check fails on reconnect; redirect to room list.

---

## Technical Architecture

### Backend Stack

**Framework & Language:**
- Spring Boot 3.x (latest LTS)
- Spring Modulith (modular monolith — module boundary enforcement)
- Java 21 (LTS)

**Core Libraries:**
- Spring WebSocket + STOMP
- Spring Data JPA + Hibernate
- Spring Security + JWT (`jjwt` library)
- Redis (`spring-data-redis`) for Pub/Sub and presence
- PostgreSQL JDBC driver
- Lombok
- MapStruct

**Database:**
- **PostgreSQL 16** (primary storage)
- **PgBouncer** in transaction pooling mode (already configured — keep as-is)
  - HikariCP `maximum-pool-size` should be set ≤ `DEFAULT_POOL_SIZE` (25)
  - Avoid nested transactions and DDL inside transactions (transaction pooling limitation)

**Cache & Pub/Sub (Redis):**
| Key | Type | Purpose |
|-----|------|---------|
| `room:{roomId}:members` | Hash | Online users: userId → login timestamp |
| (Pub/Sub) `room:{roomId}` | Channel | Live messages |
| (Pub/Sub) `room:{roomId}:typing` | Channel | Typing indicators |
| (Pub/Sub) `room:{roomId}:events` | Channel | Join/leave events |

> **Note:** Redis message cache (`messages:{roomId}`) is removed from this version. Catch-up is handled by a DB query with `sequence_id`. This simplifies the architecture and is sufficient for the 10-user target load.

### Infrastructure (Docker Compose)

```yaml
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: giano
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports:
      - "5433:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U giano_user -d giano"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6380:6379"

  pgbouncer:
    image: edoburu/pgbouncer:v1.25.1-p0
    restart: unless-stopped
    environment:
      DB_HOST: postgres
      DB_USER: giano_user
      DB_PASSWORD: ${DB_PASSWORD}
      DB_NAME: giano
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 25
      AUTH_TYPE: scram-sha-256
      AUTH_USER: giano_user
      AUTH_QUERY: "SELECT usename, passwd FROM pg_shadow WHERE usename=$1"
    ports:
      - "6433:5432"
    depends_on:
      postgres:
        condition: service_healthy

  app:
    build: ./backend
    restart: unless-stopped
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://pgbouncer:5432/giano
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRY_MINUTES: 15
      JWT_REFRESH_EXPIRY_DAYS: 7
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
  redis_data:
```

### CI/CD Pipeline (GitLab CI)

```yaml
stages:
  - test
  - build
  - deploy

variables:
  POSTGRES_DB: giano_test
  POSTGRES_USER: test
  POSTGRES_PASSWORD: test
  POSTGRES_HOST_AUTH_METHOD: trust

# ── Backend tests ────────────────────────────────────────────
backend:test:
  stage: test
  image: eclipse-temurin:21-jdk-alpine
  services:
    - postgres:16-alpine
    - redis:7-alpine
  variables:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres/giano_test
    SPRING_DATASOURCE_USERNAME: test
    SPRING_DATASOURCE_PASSWORD: test
    SPRING_DATA_REDIS_HOST: redis
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
  script:
    - cd backend
    - ./mvnw test
  artifacts:
    when: always
    reports:
      junit: backend/target/surefire-reports/TEST-*.xml
    paths:
      - backend/target/site/jacoco/
    expire_in: 1 week
  cache:
    key: backend-$CI_COMMIT_REF_SLUG
    paths:
      - backend/.m2/repository/

# ── Frontend build ───────────────────────────────────────────
frontend:build:
  stage: test
  image: node:20-alpine
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
  script:
    - cd frontend
    - npm ci
    - npm run build
  cache:
    key: frontend-$CI_COMMIT_REF_SLUG
    paths:
      - frontend/node_modules/

# ── Docker image build ───────────────────────────────────────
docker:build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
  variables:
    IMAGE: $CI_REGISTRY_IMAGE/backend:$CI_COMMIT_SHORT_SHA
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $IMAGE ./backend
    - docker push $IMAGE
    - docker tag $IMAGE $CI_REGISTRY_IMAGE/backend:latest
    - docker push $CI_REGISTRY_IMAGE/backend:latest

# ── Deploy to VPS ─────────────────────────────────────────────
deploy:production:
  stage: deploy
  image: alpine:latest
  rules:
    - if: '$CI_COMMIT_BRANCH == "main"'
      when: manual
  before_script:
    - apk add --no-cache openssh-client
    - eval $(ssh-agent -s)
    - chmod 400 "$SSH_PRIVATE_KEY"
    - ssh-add "$SSH_PRIVATE_KEY"
    - mkdir -p ~/.ssh
    - chmod 700 ~/.ssh
    - cp "$SSH_KNOWN_HOSTS" ~/.ssh/known_hosts
    - chmod 644 ~/.ssh/known_hosts
  script:
    - ssh deploy@$SSH_HOST "
        docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY &&
        docker pull $CI_REGISTRY_IMAGE/backend:latest &&
        cd /opt/giano &&
        docker compose -f docker-compose.prod.yml up -d --no-deps app
      "
```

**Required CI/CD variables (set in GitLab → Settings → CI/CD → Variables):**
| Variable | Type | Notes |
|----------|------|-------|
| `DB_PASSWORD` | Variable | PostgreSQL + PgBouncer password |
| `JWT_SECRET` | Variable (masked) | Min 32-char random string |
| `SSH_PRIVATE_KEY` | File | Deploy key; 400 permissions |
| `SSH_KNOWN_HOSTS` | File | VPS fingerprint |
| `SSH_HOST` | Variable | VPS IP or hostname |

### Frontend Stack

**Framework & Build Tool:**
- Vite + React 18 + TypeScript
- Tailwind CSS

**Real-Time Communication:**
- STOMP over WebSocket (`@stomp/stompjs`)

**State Management:**
- Zustand (lightweight, avoids Redux boilerplate)

**HTTP Client:**
- Axios with interceptor for JWT header + silent refresh

**Frontend Folder Structure:**
```
frontend/
├── src/
│   ├── components/
│   │   ├── Auth/
│   │   │   ├── LoginForm.tsx
│   │   │   ├── SignUpForm.tsx
│   │   │   └── ProfileCard.tsx
│   │   ├── Chat/
│   │   │   ├── ChatWindow.tsx
│   │   │   ├── MessageList.tsx
│   │   │   ├── MessageInput.tsx
│   │   │   ├── RoomList.tsx
│   │   │   └── MemberList.tsx
│   │   └── Common/
│   │       ├── Navbar.tsx
│   │       ├── Toast.tsx
│   │       └── LoadingSpinner.tsx
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useWebSocket.ts
│   │   ├── useRoom.ts
│   │   └── useMessages.ts
│   ├── services/
│   │   ├── api.ts
│   │   ├── authService.ts
│   │   ├── roomService.ts
│   │   └── websocketService.ts
│   ├── store/
│   │   ├── authStore.ts
│   │   ├── roomStore.ts
│   │   └── messageStore.ts
│   ├── App.tsx
│   ├── main.tsx
│   └── index.css
├── vite.config.ts
├── package.json
└── .env.local
```

---

## Phase Breakdown & Milestones

### Phase 1: Setup & Infrastructure (Week 1–2)

**Deliverables:**
- Docker Compose environment fully verified locally.
- Database schema (`init.sql`) with all tables and indexes.
- Spring Boot project with Spring Modulith, WebSocket, and `ApplicationModuleTest` verifying module boundaries.
- Vite frontend scaffold (React/TS, Tailwind, folder structure, Vite proxy).
- GitLab repo with README and branch strategy.

**Tasks:**
- `[Backend]` Initialize Spring Boot project (Modulith, WebSocket, Security, Data JPA, Redis, jjwt).
- `[Backend]` Create entities and JPA repositories for users, rooms, messages.
- `[Backend]` Add `ApplicationModuleTest` — must pass CI from day one.
- `[DevOps]` Verify existing `docker-compose.yml` works end-to-end; add `app` service.
- `[DevOps]` Write `init.sql` with all tables and indexes (see Data Model section).
- `[DevOps]` Set CI/CD variables in GitLab; verify `backend:test` and `frontend:build` stages pass.
- `[Frontend]` `npm create vite`, install deps (react, axios, zustand, stompjs, tailwind).
- `[Frontend]` Set up Vite proxy for `/api` and `/ws` to `http://localhost:8080`.
- `[All]` Set up branch strategy: `main` (protected), `dev`, feature branches.

### Phase 2: Authentication (Week 2–4)

**Deliverables:**
- Signup/login/refresh/logout endpoints with JWT.
- Email verification stub (auto-verify; no email).
- JWT validation middleware.
- React login/signup forms with auth hooks.
- Postman collection for auth endpoints.

**Tasks:**
- `[Backend]` Implement JWT generation/validation (HS256, 15 min access, 7 day refresh).
- `[Backend]` Create `/auth/signup`, `/auth/login`, `/auth/refresh`, `/auth/logout` endpoints.
- `[Backend]` Publish `UserRegisteredEvent` from auth module on signup.
- `[Backend]` BCrypt password hashing (Spring Security `PasswordEncoder`).
- `[Backend]` JWT validation filter on all protected endpoints.
- `[Frontend]` `LoginForm`, `SignUpForm` components.
- `[Frontend]` `useAuth` hook (JWT in localStorage, auto-refresh on 401).
- `[Frontend]` Axios interceptor for `Authorization` header.
- `[Frontend]` Protected route wrapper (redirect to login if not authenticated).

### Phase 3: WebSocket & Real-Time Messaging (Week 4–7)

**Deliverables:**
- WS server with STOMP endpoint and message routing.
- Message persistence with sequence_id.
- Redis Pub/Sub per room.
- Catch-up API.
- Frontend WS client with reconnect logic.
- Manual e2e test: two browser tabs exchanging messages.

**Tasks:**
- `[Backend]` Configure Spring WebSocket + STOMP.
- `[Backend]` `@MessageMapping` handlers: `/app/room/{roomId}/send`, `/app/room/{roomId}/typing`.
- `[Backend]` Message service: validate → persist → publish to Redis → emit `MessageSentEvent`.
- `[Backend]` Sequence_id generation (DB sequence or `SELECT MAX(sequence_id) + 1` with row lock).
- `[Backend]` Redis Pub/Sub listeners (dedicated thread pool).
- `[Backend]` `GET /api/rooms/{id}/messages?after_sequence_id=X&limit=50`.
- `[Frontend]` `websocketService.ts` (StompClient wrapper: connect, disconnect, subscribe, send, reconnect).
- `[Frontend]` `ChatWindow` (message list, input field, auto-scroll).
- `[Frontend]` `useMessages` hook (message state, catch-up trigger on reconnect).
- `[Frontend]` Reconnect logic (exponential backoff + jitter, message queue, banner).
- `[Testing]` Manual test: two tabs, one room, send/receive, kill network, reconnect, verify catch-up.

### Phase 4: Room Management (Week 7–8)

**Deliverables:**
- Room CRUD and membership endpoints.
- Frontend room list sidebar, create-room form, member panel.

**Tasks:**
- `[Backend]` `RoomService`: create, list, join (idempotent), leave, get members. Publishes `UserJoinedRoomEvent` / `UserLeftRoomEvent`.
- `[Backend]` Validate membership before allowing message send.
- `[Backend]` `GET /api/rooms`, `POST /api/rooms`, `POST /api/rooms/{id}/join`, `POST /api/rooms/{id}/leave`, `GET /api/rooms/{id}/members`.
- `[Frontend]` `RoomList` sidebar (joined rooms, join button, last message preview).
- `[Frontend]` `CreateRoomForm` modal.
- `[Frontend]` `RoomInfo` panel (name, description, member list with online status).

### Phase 5: Presence & Typing Indicators (Week 8–9)

**Deliverables:**
- Online presence tracking in Redis.
- Typing indicators via WS.
- System messages for join/leave.
- Frontend presence UI updates in real-time.

**Tasks:**
- `[Backend]` On WS connect: `HSET room:{roomId}:members {userId} {timestamp}`.
- `[Backend]` On WS disconnect: `HDEL room:{roomId}:members {userId}`.
- `[Backend]` Listens to `UserJoinedRoomEvent` / `UserLeftRoomEvent` — publishes presence events to Redis.
- `[Backend]` Handle `/app/room/{roomId}/typing` for typing indicator.
- `[Frontend]` Subscribe to presence channel; update member list in real-time.
- `[Frontend]` Send typing event on keydown (debounced 300ms); send stop after 2s.
- `[Frontend]` Show "User X is typing..." banner.
- `[Frontend]` System messages (gray, smaller text): "User X joined the room".

### Phase 6: Error Handling & Polish (Week 9–10)

**Deliverables:**
- Auto-reconnect fully wired with exponential backoff + jitter.
- User-facing error toasts and banners.
- Message queue during offline periods.
- Exception handler for consistent error responses.
- Chaos test: kill Redis, restart app, kill network.

**Tasks:**
- `[Backend]` `@ControllerAdvice` exception handler (consistent JSON error body with status, code, message).
- `[Backend]` Return proper status codes: 400 (validation), 401 (auth), 403 (forbidden), 404 (not found), 429 (rate limit stub), 5xx (server error).
- `[Frontend]` Full reconnect logic wired: detect disconnect → backoff → retry → max attempts → error banner.
- `[Frontend]` Message queue: store in memory during disconnect; flush on reconnect.
- `[Frontend]` Toast/notification system.
- `[Frontend]` Disable send button while offline; show pending message count.

### Phase 7: Testing & Documentation (Week 10–11)

**Deliverables:**
- JUnit 5 + Testcontainers suite with >80% service-layer coverage.
- Postman collection for all endpoints.
- README with setup, architecture, and environment variable list.
- ER diagram + architecture diagram.
- Demo video.

**Tasks:**
- `[Backend]` JUnit tests: `AuthService`, `RoomService`, `MessageService`, repository layer.
- `[Backend]` Testcontainers integration tests (PostgreSQL + Redis).
- `[Backend]` Test edge cases: duplicate messages, invalid JWT, non-member send, sequence_id ordering.
- `[Backend]` Verify `ApplicationModuleTest` still passes (no regressions on module boundaries).
- `[Testing]` Postman collection: auth flow, room CRUD, message catch-up, presence.
- `[All]` README: project overview, `docker compose up` instructions, env vars, architecture notes.
- `[All]` ER diagram, module event diagram.
- `[All]` Demo video (signup → create room → send messages → disconnect → reconnect → catch-up).

### Phase 8: Final Polish & Review (Week 11–12)

**Deliverables:**
- Code review within trio; all feedback addressed.
- Security audit checklist signed off.
- Final e2e testing.
- Presentation slides.

**Tasks:**
- `[All]` Code review: focus on module boundary enforcement, security, SQL query correctness.
- `[Backend]` Check for N+1 queries; verify all indexes from `init.sql` are present.
- `[Backend]` Review CORS config (frontend origin whitelisted; no `*` in production).
- `[Backend]` Verify no hardcoded secrets; all via environment variables.
- `[Frontend]` Clean up console warnings/errors.
- `[All]` Final e2e run using QA script below.
- `[All]` Prepare presentation slides.

---

## Data Model

```sql
CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  email         VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(100),
  avatar_url    TEXT,
  email_verified BOOLEAN DEFAULT TRUE,   -- auto-verified in this version
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at    TIMESTAMP NULL
);

CREATE TABLE rooms (
  id          BIGSERIAL PRIMARY KEY,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  is_public   BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at  TIMESTAMP NULL
);

CREATE TABLE room_members (
  id        BIGSERIAL PRIMARY KEY,
  room_id   BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role      VARCHAR(20) NOT NULL DEFAULT 'member', -- 'member' | 'admin'
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, user_id)
);

CREATE TABLE messages (
  id          BIGSERIAL PRIMARY KEY,
  room_id     BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text        TEXT NOT NULL,
  sequence_id BIGINT NOT NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, sequence_id)
);

-- Critical indexes
CREATE INDEX idx_messages_room_seq     ON messages(room_id, sequence_id DESC);
CREATE INDEX idx_room_members_room     ON room_members(room_id);
CREATE INDEX idx_room_members_user     ON room_members(user_id);
CREATE INDEX idx_refresh_tokens_expiry ON refresh_tokens(expires_at);

CREATE TABLE refresh_tokens (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Success Metrics & Acceptance Criteria

**Functional:**
- ✅ User can sign up, log in, and log out.
- ✅ User can create a room and another user can join it.
- ✅ User can send a message and see it appear instantly for other users in the room.
- ✅ User can see the last 50 messages when entering a room.
- ✅ User can see who is online in a room.
- ✅ User's browser reconnects automatically if network drops; they see "Reconnecting..." banner.
- ✅ User receives missed messages after reconnect (catch-up from DB by sequence_id).
- ✅ Typing indicators show when another user is typing.
- ✅ System messages appear when a user joins or leaves.

**Non-Functional:**
- ✅ 10 concurrent users chatting without message loss or ordering errors.
- ✅ Message delivery latency <100ms on localhost.
- ✅ Reconnect completes in <5s on average.
- ✅ `docker compose up -d` starts all services healthy within 60s.
- ✅ All module boundary violations caught by `ApplicationModuleTest` in CI.
- ✅ JUnit + Testcontainers suite with >80% service-layer coverage.
- ✅ Passwords hashed with bcrypt (min 12 rounds).
- ✅ JWT validated on all protected endpoints; expired tokens rejected with 401.
- ✅ No hardcoded secrets.
- ✅ No cross-module `@Autowired` service references.

**Documentation:**
- ✅ README with setup instructions and architecture overview.
- ✅ ER diagram and module event diagram.
- ✅ API documentation (endpoints, request/response examples, error codes).
- ✅ Postman collection (runnable for all features).
- ✅ Demo video showing full user flow including reconnect scenario.

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Cross-module `@Autowired` violations introduced under time pressure | Medium | High | `ApplicationModuleTest` fails CI on any violation. Fix before merging. |
| PgBouncer transaction mode breaks `@Transactional(REQUIRES_NEW)` or DDL inside transactions | Low | Medium | Avoid nested transactions. Run Flyway/Liquibase migrations against Postgres directly, not via PgBouncer. |
| WS blocked by VPS firewall or Nginx missing upgrade headers | Medium | High | Test WS upgrade through VPS in Week 1. Configure Nginx proxy with `Upgrade` and `Connection` headers early. |
| Phase 3 (WS + Redis) slips and blocks all downstream phases | Medium | High | Pair-program WS + Redis. Target a working two-tab PoC by end of Week 5. Cut scope (typing, presence) if behind. |
| Redis restart loses presence data mid-demo | Low | Low | Ephemeral by design. Clients re-announce on reconnect. Document as known limitation. |
| Merge conflicts with three developers on shared modules | Low | Low | Clear module ownership. Merge to `dev` daily. Code review on all PRs. |

---

## Deployment & Running

### Local Development

**Prerequisites:**
- Docker & Docker Compose
- Java 21+ (for IDE support)
- Node 20+ (for Vite frontend)
- Git

**Setup:**
```bash
# Clone repo
git clone <repo-url>
cd giano

# Configure environment
cp .env.example .env
# Edit .env: set DB_PASSWORD, JWT_SECRET

# Start infrastructure (Postgres, Redis, PgBouncer)
docker compose up -d postgres redis pgbouncer

# Wait for Postgres health check (or check: docker compose ps)
# Then start backend
cd backend
./mvnw spring-boot:run

# In another terminal, start frontend
cd frontend
npm install
npm run dev
# Open http://localhost:5173

# Run tests
cd backend
./mvnw test

# Run with coverage report
./mvnw verify
# Report at: backend/target/site/jacoco/index.html
```

**Troubleshooting:**
- "Connection refused to pgbouncer": Postgres healthcheck may still be pending. Wait ~15s; check `docker compose ps`.
- "Port already in use 5433/6380/6433": These ports are non-default to avoid conflicts. Check for other Docker services on those ports.
- "JWT secret not set": Ensure `JWT_SECRET` is in `.env` and is at least 32 characters.
- "SpringBoot ApplicationModuleTest failed": A cross-module direct dependency was introduced. Remove the `@Autowired` reference and replace with an application event.

### QA Script (Manual E2E Test)

1. Open `http://localhost:5173` in two browser windows (Window 1, Window 2).
2. **Window 1:** Sign up (email A), log in.
3. **Window 2:** Sign up (email B), log in.
4. **Window 1:** Create room "General".
5. **Window 1:** Send message "Hello from A".
6. **Window 2:** Join room "General". Verify message history shows "Hello from A".
7. **Window 2:** Send "Hi A" — verify Window 1 sees it instantly.
8. **Window 1:** Start typing (don't send) — verify Window 2 shows typing indicator.
9. **Window 2:** Disconnect network (or close tab and reopen).
10. **Window 1:** Send 3 more messages while Window 2 is disconnected.
11. **Window 2:** Reconnect — verify "Reconnecting..." banner appears, then catch-up messages appear in correct order.
12. Verify both users show as online in the member panel.

---

## Deliverables Checklist

**Week 1–2 (Infrastructure):**
- [ ] `docker-compose.yml` verified end-to-end with all services
- [ ] `init.sql` with all tables, constraints, and indexes
- [ ] Spring Boot skeleton with `ApplicationModuleTest` passing CI
- [ ] Vite frontend scaffold
- [ ] GitLab repo, README started, branch strategy set

**Week 2–4 (Auth):**
- [ ] Signup/login/refresh/logout endpoints
- [ ] JWT generation & validation middleware
- [ ] Email verification stub (auto-verify)
- [ ] `UserRegisteredEvent` published from auth module
- [ ] React login/signup forms with `useAuth` hook
- [ ] Postman collection (auth endpoints)

**Week 4–7 (Messaging):**
- [ ] WS server with STOMP
- [ ] Message persistence + sequence_id
- [ ] Redis Pub/Sub integration
- [ ] Catch-up API (`after_sequence_id`)
- [ ] Frontend WS client with reconnect logic
- [ ] Two-tab e2e manual test passing

**Week 7–8 (Rooms):**
- [ ] Room CRUD endpoints
- [ ] Membership management
- [ ] `UserJoinedRoomEvent` / `UserLeftRoomEvent` published
- [ ] Frontend room list, create form, member panel

**Week 8–9 (Presence):**
- [ ] Redis presence hash (on connect/disconnect)
- [ ] Typing indicators (debounced, auto-clear)
- [ ] System messages (join/leave)
- [ ] Frontend presence UI

**Week 9–10 (Resilience):**
- [ ] Auto-reconnect with backoff + jitter
- [ ] Message queue during offline
- [ ] Error toasts and banners
- [ ] `@ControllerAdvice` exception handler

**Week 10–11 (Testing & Docs):**
- [ ] JUnit 5 service-layer tests
- [ ] Testcontainers integration tests
- [ ] `ApplicationModuleTest` still passing
- [ ] Postman collection (all endpoints)
- [ ] README with setup, env vars, architecture
- [ ] ER diagram + module event diagram
- [ ] Demo video

**Week 11–12 (Polish):**
- [ ] Code review & refactoring
- [ ] Security audit (secrets, JWT, CORS, SQL)
- [ ] Final e2e QA script run
- [ ] GitLab CI pipeline green on `main`
- [ ] Presentation slides

---

## Deferred Items

These items from the original proposal are explicitly out of scope. They may be revisited during the microservices comparison phase.

| Feature | Reason |
|---------|--------|
| Email verification (SMTP) | No comparison value; stub sufficient |
| Password reset via email | No comparison value; stub sufficient |
| Social login (Google OAuth) | Unrelated to comparison goals |
| Redis message cache (`messages:{roomId}`) | Replaced by DB catch-up query; simpler |
| Message pagination (scroll-up load older) | Deferred; catch-up covers the baseline |
| Message edit / delete | Adds sequence_id complexity; not needed |
| File / image sharing | Storage layer not designed for it |
| @Mentions and notifications | Out of scope |
| Read receipts | Out of scope |
| Bucket4j rate limiting | Stub or omit; not a comparison metric |
| Multi-instance / clustering | Explicitly out of scope for monolith version |

---

## References & Resources

**Spring Boot & Modulith:**
- [Spring Boot WebSocket Tutorial](https://spring.io/guides/gs/messaging-stomp-websocket)
- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [Spring Security & JWT (Baeldung)](https://www.baeldung.com/spring-security-authentication-with-a-database)

**Redis:**
- [Spring Data Redis Pub/Sub](https://spring.io/guides/gs/messaging-redis)
- [Redis Pub/Sub Documentation](https://redis.io/topics/pubsub)

**React + WebSocket:**
- [@stomp/stompjs Documentation](https://stomp-js.github.io/stomp-js/latest/)
- [Vite React Template](https://vitejs.dev/guide/#scaffolding-your-first-vite-project)

**Testing:**
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers for Postgres & Redis](https://www.testcontainers.org/)

**Docker & Infrastructure:**
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PgBouncer Configuration](https://pgbouncer.github.io/config.html)
- [edoburu/pgbouncer Docker image](https://hub.docker.com/r/edoburu/pgbouncer)

---

**Document Version:** 2.0
**Last Updated:** 2026-03-17
**Prepared for:** 3-person development team (school project, 8–12 weeks)
**Changes from v1.1:** Updated Docker Compose to match configured infrastructure (PgBouncer v1.25.1-p0, scram-sha-256, adjusted ports). Added GitLab CI pipeline. Added Spring Modulith module event map and `ApplicationModuleTest` requirement. Stubbed email verification and password reset. Removed Redis message cache in favour of DB catch-up query. Added Deferred Items section.
