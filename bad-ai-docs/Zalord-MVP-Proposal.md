# giano — MVP Stage 1 Proposal
## Real-Time Messaging Platform (School Project — Trio Team)

> A modular monolith real-time chat application built with Spring Boot, WebSocket, Redis, and PostgreSQL.
> This proposal outlines a simplified, production-ready foundation for a team of three developers over 8–12 weeks.
> Focus: Core messaging logic, no distributed complexity, deliverable & testable.

---

## Project Overview

**giano MVP** is a real-time chat platform where users can:
- Register and log in securely (JWT-based auth)
- Create or join chat rooms
- Send and receive messages in real-time (WebSocket)
- Reconnect and recover missed messages (sequence_id + Redis cache)
- See who's online and typing (presence + activity indicators)

**Technology Stack:**
- Backend: Spring Boot 3.x + Spring Modulith (modular monolith)
- Real-time: WebSocket (STOMP), Redis Pub/Sub
- Database: PostgreSQL + PgBouncer (connection pooling)
- Frontend: Vite + React/Vue.js + native WebSocket / STOMP client
- Infrastructure: Docker Compose (local dev environment)
- Testing: JUnit 5, Testcontainers, Postman collection

**Team Structure (Trio):**
| Role | Responsibilities |
|------|------------------|
| **Backend (WebSocket & Messaging)** | WS server, Redis Pub/Sub routing, message persistence, sequence tracking |
| **Auth & User Module** | JWT auth, user profiles, session management, fallback caching |
| **DevOps & Frontend** | Docker Compose setup, integration testing, Vite frontend scaffold, documentation |

**Scope Boundary:**
- ✅ Single-instance monolith (no multi-instance fleet scaling)
- ✅ PostgreSQL with connection pooling via PgBouncer
- ✅ Basic WebSocket messaging flow (no heavy message brokers)
- ✅ Catch-up from Redis cache only
- ✅ Manual testing + basic e2e tests
- ❌ No multi-region, no distributed consensus, no advanced chaos testing
- ❌ No paid features, no analytics dashboards, no mobile apps

**Success Criteria:**
- 10 concurrent users chatting in one or multiple rooms with zero message loss
- JWT auth + login/signup fully functional
- Reconnect + catch-up working (users can refresh browser and see history)
- Full Docker Compose environment reproducible on any laptop
- Postman + JUnit tests covering happy path + key error cases
- Working Vite frontend + integrated WS client
- Deployment-ready codebase (runs on any machine with Docker)

---

## Core Features

### 1. Authentication & User Management

**Goal:**
Enable users to register, log in securely, and maintain authenticated sessions for the entire chat experience. Manage user profiles and session tokens.

**User Stories:**
- As a new user, I want to sign up with email and password so I can create an account and start chatting.
- As a returning user, I want to log in with my credentials to access my previous chats and connections.
- As a logged-in user, I want to see my profile (name, avatar, status) and update it.
- As a user, I want to be automatically logged out after my session expires or when I log out manually.
- As a user, I want a simple password reset flow via email if I forget my password.

**Flow/UX:**

**Sign Up Flow:**
```
User opens app
  → "Sign Up" button
  → Enter email, password, display name
  → Validation: email format, password strength, name not empty
  → Backend creates user record (hashed password with bcrypt)
  → Send verification email (click link to confirm email)
  → On verify click: mark email as verified, redirect to login
  → User logs in with email + password
  → Backend generates JWT (exp: 15 min), refresh token (exp: 7 days)
  → Client stores both in localStorage (or secure cookie for refresh)
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
  → Optional: POST /api/auth/logout to backend (invalidate refresh token in DB if tracking)
  → Redirect to login page
  → WS connection closes (or backend closes it on token validation failure)
```

**Permission & Security:**
- All passwords stored with bcrypt (minimum 12 rounds), salted.
- JWT signed with HS256 or RS256 (secret key stored securely, not in code).
- Refresh tokens stored in DB with user_id foreign key; can be invalidated on logout.
- Email verification required before user can join a room (to prevent spam).
- Password reset link is time-limited (e.g., 1 hour expiry) and one-time-use.
- All API endpoints require valid JWT except /auth/signup, /auth/login, /auth/refresh, /auth/reset-password.
- WebSocket connection upgrade also validated with JWT (sent as query param or header).

**Must-have:**
- Email/password registration with validation (email format, password complexity: min 8 chars, at least 1 upper, 1 digit, 1 special).
- Secure login endpoint with JWT generation (access token: 15 min, refresh token: 7 days).
- Password hashing with bcrypt.
- Email verification via confirmation link (backend sends email with token, user clicks to verify).
- Session/token management (refresh endpoint to issue new JWT without re-login).
- Basic profile management: display name, avatar (URL or simple emoji).
- Logout endpoint that clears tokens.
- JWT validation middleware on all protected endpoints.
- WebSocket upgrade requires valid JWT.

**Should-have:**
- Password reset via email link (user enters email → receives reset link → sets new password).
- Account deactivation option (soft delete; can reactivate by logging in again).
- Session timeout notification (warn user 1 min before session expires).
- Option to view active sessions and log out from other devices (basic session list).
- User presence status (online/away/offline) synced across rooms.

**Good-to-have:**
- Social login (Google OAuth) as alternative to email/password.
- CAPTCHA on sign-up form (prevent bot registrations).
- Account recovery questions or backup codes (2FA alternative).
- Login attempt rate limiting (max 5 failed attempts per IP per 15 min, then cooldown).
- Password strength indicator on signup form.
- Remember-me checkbox (longer-lived refresh token for trusted devices).

**Edge Cases:**
- Duplicate Email: If user tries to sign up with existing email, show "Email already registered" and suggest login or password reset.
- Unverified Email: User tries to join room but email not verified → show banner "Verify email first" with resend option.
- Expired Reset Link: User clicks password reset link after 1h → show "Link expired, request new reset" button.
- Logout from All Devices: User requests logout everywhere → invalidate all refresh tokens for that user at once.
- Session During Disconnect: User loses internet mid-chat → WS reconnects automatically, uses refresh token if needed.
- Concurrent Logins: Same user logs in from 2 tabs → both tabs are valid but only 1 receives new messages (acceptable for this scope).

---

### 2. Chat Rooms & Room Management

**Goal:**
Enable users to create or discover chat rooms, join rooms they're interested in, and see room metadata (name, member count, description).

**User Stories:**
- As a user, I want to see a list of available rooms so I can join conversations I'm interested in.
- As a user, I want to create a new room with a name and description so I can start a conversation.
- As a user, I want to join an existing room by searching for it or via invite link.
- As a room creator (moderator), I want to see members in my room and remove disruptive users.
- As a user, I want to leave a room whenever I choose.
- As a user, I want to see which rooms I'm currently a member of in a sidebar.

**Flow/UX:**

**Room Discovery & Join:**
```
User lands on dashboard
  → Show "My Rooms" sidebar (list of joined rooms)
  → Show "Browse Rooms" section with all public rooms
  → For each room: name, description, member count, preview of last message
  → User clicks "Join" on a room → backend adds user to room_members table
  → Room appears in sidebar; user can now send messages in that room
```

**Create Room:**
```
User clicks "Create Room" button
  → Modal/form: enter room name (required), description (optional), visibility (public/private)
  → Backend validates: name not empty, name < 100 chars, no duplicates (optionally allow same name)
  → Backend creates room record, adds creator as admin member
  → Return room ID + name to client
  → Open newly created room in chat view
```

**Leave Room:**
```
User clicks "Leave" on room in sidebar
  → Confirmation dialog: "Are you sure? You can rejoin anytime."
  → On confirm: POST /api/rooms/{roomId}/leave
  → Backend removes user from room_members
  → If user is last member, optionally delete room (or keep for history)
  → Room disappears from sidebar; user loses access to that room's messages
```

**Room Member List:**
```
User opens room
  → Click room name or info icon
  → Show modal/panel: list of current members, each with name, avatar, online status
  → If user is admin: show "Remove" button next to each member (except self)
  → Click remove → confirmation → backend removes member from room
  → If user is not admin: see member list read-only
```

**Permission & Security:**
- Public rooms: anyone can join without approval.
- Private rooms: only invited members can join (for this scope, all rooms public is acceptable).
- Room admin (creator): can remove members and optionally delete room.
- Regular members: can send messages and see room history.
- Endpoints: GET /api/rooms, POST /api/rooms, POST /api/rooms/{id}/join, POST /api/rooms/{id}/leave, DELETE /api/rooms/{id} (admin only).
- All endpoints require JWT; room access is validated (user must be a member or room is public).

**Must-have:**
- Create room with name and optional description.
- List all public rooms with sorting/filtering (by name, member count, last activity).
- Join public room (add user to room_members).
- Leave room (remove user from room_members).
- View list of rooms user is member of (in sidebar).
- Basic room info page showing name, description, member count.
- View members in a room (list of user names/avatars).

**Should-have:**
- Room admin can remove members.
- Room admin can change room name or description.
- Room admin can delete room (soft delete if any messages exist, to preserve history).
- Search/filter rooms by name or keyword.
- Sort rooms by popularity (member count), recent activity, or alphabetical.
- Room activity indicator (last message timestamp, online member count).
- Invite link to room (shareable URL that auto-joins if not already member).
- Public vs Private room toggle (basic access control).

**Good-to-have:**
- Room avatar/icon (user-uploaded image or emoji).
- Room categories/tags for better discovery.
- Room archival (admin can archive inactive rooms; archived rooms disappear from list but messages remain).
- Room rules or pinned message (description of room guidelines).
- Room creation limits (user can only create N rooms per day, to prevent spam).
- Room member roles: admin, moderator, member (with different permission sets).

**Edge Cases:**
- Empty Room: Last member leaves → room becomes empty. Either delete it or keep it (both acceptable).
- Duplicate Room Names: Two users try to create room named "General" → allow both (no unique constraint on name alone).
- Non-existent Room: User tries to join room that was deleted → show "Room not found" error.
- Member Removed Mid-Chat: Admin removes user while they're viewing room → user's WS connection terminates, user sees "You have been removed" message, redirect to room list.
- Create Room While Offline: User creates room, loses internet, comes back online → retry mechanism shows "pending creation"; backend re-tries or acknowledges.
- Room Permissions Race: Admin removes user A, but A sends a message at same time → backend checks membership on message validation, rejects with "You are not a member of this room."

---

### 3. Real-Time Messaging (WebSocket)

**Goal:**
Enable users to send and receive messages instantly via WebSocket, with delivery to all room members. Store messages in database for persistence and catch-up.

**User Stories:**
- As a user, I want to type a message and send it; it should appear instantly for me and all other users in the room.
- As a user, I want to see when other users are typing in the room (typing indicator).
- As a user, I want to see messages in chronological order with sender name, timestamp, and content.
- As a user joining a room, I want to see recent message history (last 50 messages or last 1 hour).
- As a user, if my internet drops, I want my connection to automatically reconnect and catch up on missed messages.

**Flow/UX:**

**Sending a Message:**
```
User types in message input field
  → User clicks Send or presses Enter
  → Client generates unique sequence_id (local counter or UUID)
  → Client sends message via WS: { text, roomId, sequence_id, timestamp }
  → Backend receives message, validates:
     - User is member of roomId (check room_members table)
     - Message text not empty and < 5000 chars
     - Timestamp is reasonable (within 1 min of server time)
  → Backend stores message: INSERT INTO messages (room_id, user_id, text, sequence_id, created_at)
  → Backend publishes message to Redis Pub/Sub channel: "room:{roomId}"
  → All WS clients subscribed to that channel receive message in real-time
  → Client receives message and appends to chat view with sender name, timestamp, avatar
```

**Message History (Catch-Up):**
```
User joins room or reconnects
  → Client requests catch-up: GET /api/rooms/{roomId}/messages?after_sequence_id=X
  → Backend queries messages table: SELECT * FROM messages WHERE room_id=roomId AND sequence_id > X ORDER BY sequence_id DESC LIMIT 50
  → Return list of messages (text, sender name, timestamp, sequence_id)
  → Client renders messages in correct chronological order
  → Client updates lastSeqId = max(sequence_id) from response
  → On future connects, use this lastSeqId to avoid duplicates
```

**Real-Time Delivery (WS Flow):**
```
Architecture (single server):
  User A → WS Server → handle @MessageMapping("/room/{roomId}/send")
  Validate + persist to DB
  Publish to Redis Pub/Sub "room:{roomId}"
  Server's listener on that channel delivers to all connected WS clients for that room
```

**Typing Indicator:**
```
User starts typing (on keydown or after 300ms debounce)
  → Client sends WS message: { type: "typing", roomId }
  → Backend publishes to Redis: "room:{roomId}:typing"
  → All clients subscribed to that channel show "User X is typing..."
  → After user stops typing for 2 seconds, send "stop typing" message
  → Clients clear the typing indicator for that user
```

**Permissions & Security:**
- Only room members can send/receive messages in a room.
- Messages belong to the user who sent them; users cannot edit/delete others' messages.
- WS connection validated with JWT; invalid/expired tokens rejected.
- Message validation: non-empty, reasonable length, no SQL injection (parameterized queries).
- Rate limiting: user cannot send >5 messages per second (prevents spam).
- All messages stored in DB with created_at timestamp; immutable after creation.

**Must-have:**
- WebSocket server (Spring Boot with WebSocket + STOMP or raw WS).
- Send message to room via WS (broadcast to all members).
- Receive and display messages in real-time (auto-append to chat view).
- Store messages in database (messages table: room_id, user_id, text, sequence_id, created_at).
- Sequence_id tracking (per room; ensures message ordering and catch-up accuracy).
- Catch-up on reconnect (fetch recent messages from DB after sequence_id).
- Message validation (not empty, reasonable length).
- User membership check (sender must be in room).
- Display sender name, avatar, and timestamp with each message.
- Basic rate limiting (prevent message spam, e.g., max 10 msg/sec per user).

**Should-have:**
- Typing indicators (show "User X is typing..." in real-time).
- Last-read message tracking (show which messages user has seen).
- Message pagination (load older messages on scroll-up, lazy-load from DB).
- Emoji support in messages.
- Preview/link unfurling (if message contains URL, show title + preview).
- @mentions (user can tag another user, gets notified).
- Message reactions (users can react with emoji to messages; optional).

**Good-to-have:**
- Message editing (user can edit their own message; updated message shows "edited" indicator).
- Message deletion (user can delete own message; shows as "[deleted]" for others).
- Image/file sharing (user can upload image in message, displayed inline).
- Message threads/replies (reply to specific message, creates sub-conversation).
- Search messages (search bar to find messages in a room by keyword, date range, etc.).

**Edge Cases:**
- Message Sent While User Offline: WS disconnected but user sends optimistically → client queues message and retries on reconnect.
- Duplicate Message: Same message sent twice due to network retry → backend deduplicates by sequence_id (unique constraint).
- Message Order Collision: Two users send messages simultaneously → DB sequence_id ordering ensures correct order.
- User Removed Mid-Message: Admin removes user, user tries to send → backend validates membership, rejects with "not a member".
- Catch-Up Out of Order: Client receives messages out of order → client sorts by sequence_id before rendering.
- Redis Pub/Sub Message Loss: Redis restarts, pending message lost → at-most-once delivery is accepted for live path; catch-up API covers gaps on reconnect.
- Very Long Message: User pastes 50k char text → backend rejects with "Message too long (max 5000 chars)".
- Simultaneous Joins: Multiple users join room at same time → handled by DB transaction isolation.

---

### 4. Online Presence & Activity

**Goal:**
Show users who is currently online in a room and update presence in real-time. Enable users to see when others are active.

**User Stories:**
- As a user, I want to see a list of who is currently online in a room.
- As a user, I want to see a "typing" indicator when someone is typing a message.
- As a user, I want my status to automatically update to "online" when I connect and "offline" when I disconnect.
- As a user, I want to see when someone joins or leaves a room (optional system message or activity feed).

**Flow/UX:**

**Presence On Join:**
```
User connects WS to a room
  → Client sends WS message or makes API call: POST /api/rooms/{roomId}/join-session
  → Backend records user in Redis Hash: HSET room:{roomId}:members {userId: timestamp}
  → Backend publishes "user_joined" event to Redis Pub/Sub "room:{roomId}:events"
  → All clients subscribed to that room receive event
  → Clients update member list UI (add user, mark as online)
  → System message appears in chat: "User X joined"
```

**Presence On Leave/Disconnect:**
```
User closes browser or logs out
  → WS disconnect event triggered (server-side onDisconnect hook)
  → Backend removes user from Redis Hash: HDEL room:{roomId}:members {userId}
  → Backend publishes "user_left" event to Pub/Sub
  → Clients update member list (remove user, mark as offline)
  → System message appears in chat: "User X left"
```

**Periodic Heartbeat:**
```
To detect stale connections (user closed browser but didn't send disconnect):
  → Server sends PING to all WS clients every 30s
  → Client responds with PONG (framework handles this automatically)
  → If no PONG received, server marks client as disconnected after timeout
```

**Permissions & Security:**
- Presence data is public within a room (all room members can see who is online).
- Presence data is ephemeral (stored in Redis, lost on server restart — acceptable).
- Each user's presence limited to rooms they're a member of.

**Must-have:**
- Track online users per room (Redis Hash or in-memory map per WS server).
- Show online user count and list (display name, avatar, status indicator).
- Add user to presence on WS connect.
- Remove user from presence on WS disconnect.
- Broadcast presence changes to room subscribers.
- System messages for join/leave.

**Should-have:**
- Typing indicators (show who is typing, disappear after 2s of inactivity).
- User status: online, idle (away for >5 min), offline.
- Idle timeout: if user inactive for 5 min, mark as "away" but keep in online list.
- Last activity timestamp (show "User X active 2 mins ago").
- Read receipts (show checkmarks: sent, delivered, read).

**Good-to-have:**
- Custom user status message ("In a meeting", "Do not disturb", etc.).
- User activity feed (sidebar showing recent activity).
- Notification when user mentions you (even if away).

**Edge Cases:**
- Stale Presence: Server crashes, user still shows online → resolved by heartbeat timeout or client reconnect validation.
- Duplicate Online Entries: Same user connects from 2 tabs → use deduplication (check if user already present before adding).
- Ghost User: User closes tab without disconnect; shows online for 30s until heartbeat timeout → acceptable.
- Presence on Disconnect Race: User disconnects and joins another room simultaneously → accept if presence shows inconsistent for a moment.

---

### 5. Error Handling & Reconnection

**Goal:**
Handle network failures gracefully, auto-reconnect on disconnect, and provide meaningful error messages to users.

**User Stories:**
- As a user, if my internet connection drops, I want my app to automatically reconnect without me having to reload the page.
- As a user, if a message fails to send, I want to see an error and have the option to retry.
- As a user, I want to see helpful error messages (not cryptic codes) when something goes wrong.
- As a user, if the server is overloaded, I want to see a message like "Server busy, please try again" instead of a broken chat.

**Flow/UX:**

**Auto-Reconnect:**
```
WS connection drops (network lost, server restart, timeout)
  → Client detects disconnect (WS onclose event)
  → Client logs warning but does NOT redirect user away from room
  → Client waits 1s, then attempts reconnect (exponential backoff: 1s, 2s, 4s, 8s, max 30s)
  → Show banner to user: "Reconnecting..." while attempting
  → On successful reconnect:
     - Re-subscribe to room channels
     - Fetch catch-up messages (messages after lastSeqId)
     - Update presence (user back online)
     - Hide reconnection banner
     - Resume sending/receiving
  → If reconnect fails after N attempts (e.g., 10 retries = ~10 min):
     - Show persistent error banner: "Connection lost. Please refresh page or check your connection."
     - Allow manual refresh button
```

**Send Failure & Retry:**
```
User sends message but WS not connected
  → Client detects not connected (WS readyState !== OPEN)
  → Message added to local queue instead of sending immediately
  → Show indicator: "Pending..." or message in gray
  → Once reconnected, flush queue: send all queued messages in order
  → Each message gets sequence_id as it's sent, so order is preserved
  → On success, message turns bold/confirmed
  → If message fails after queue flush, show error: "Failed to send message. Retry?"
  → User can click Retry to re-send
```

**Error Messages:**
```
Network / Server Errors:
  - "Network error: check your connection" (client-side network error)
  - "Connection lost. Reconnecting..." (WS disconnect)
  - "Server error: please try again" (5xx response from backend)
  - "Request failed: message too long" (validation error)
  - "You are not a member of this room" (permission error)
  - "Room not found" (room deleted or doesn't exist)
  - "Server busy. Please try again in a moment." (rate limit hit)

UI Presentation:
  - Toast notification (bottom right, auto-dismiss after 3s for info, persist for errors)
  - In-message error indicator (message shows red, has retry button)
  - Banner at top of page (for connection issues)
```

**Permissions & Security:**
- Rate limiting on server: reject if user >5 msg/sec, return 429 with Retry-After header.
- Auth errors: if JWT invalid, reject and redirect to login.
- Membership errors: if user not in room, reject message send.
- All errors logged server-side for debugging.

**Must-have:**
- Detect WS disconnection (client-side onclose event).
- Auto-reconnect with exponential backoff (1s, 2s, 4s, ..., max 30s).
- Show reconnection status to user (banner or indicator).
- Fetch missed messages after reconnect.
- Queue messages during disconnect; send when reconnected.
- Display user-friendly error messages (toast, banner, or inline).
- Handle 429 (rate limit) with Retry-After guidance.
- Handle 401 (auth invalid) by redirecting to login.

**Should-have:**
- Retry button on failed messages.
- Exponential backoff with jitter (randomness to avoid thundering herd).
- Max retry limit (don't retry forever; inform user after N attempts).
- Local message cache (so chat doesn't disappear if page refreshed).
- Error logging to backend for monitoring.
- Distinguish between network errors and app errors.

**Good-to-have:**
- Circuit breaker pattern (if server unhealthy for too long, show maintenance message).
- Fallback UI mode (partial functionality if main server down; e.g., message history available but no real-time delivery).
- User-initiated refresh button in error banner.
- Diagnostic page (show connection status, last error, attempt count, etc.).

**Edge Cases:**
- Continuous Network Failures: User on unstable network → retry indefinitely; user can manually disconnect and come back later.
- Server Restart During Send: Message in-flight when server restarts → message might be lost (acceptable); user sees failure and can retry.
- Stale Message Cache: User offline for 1 hour, comes back online → Redis cache covers last 100 messages or 1 hour window; older messages require a page-load refresh.
- User Removed During Reconnect: Admin removes user, user tries to reconnect → auth/membership check fails, redirect to login or show "You've been removed from this room".
- Race Condition: User sends message, then immediately disconnects → backend validates and either commits or rejects; client retries on reconnect.

---

## Technical Architecture

### Backend Stack

**Framework & Language:**
- Spring Boot 3.x (latest LTS)
- Spring Modulith for modular monolith structure
- Java 17+ (LTS)

**Core Libraries:**
- Spring WebSocket + STOMP for real-time messaging
- Spring Data JPA + Hibernate for ORM
- Spring Security + JWT for authentication (jjwt library)
- Redis (spring-data-redis) for Pub/Sub and caching
- PostgreSQL JDBC driver
- Lombok for boilerplate reduction
- MapStruct for DTO mapping

**Database:**
- **PostgreSQL** (primary storage):
  - users (id, email, password_hash, display_name, avatar_url, created_at, verified_at)
  - rooms (id, name, description, created_by, created_at, deleted_at)
  - room_members (id, room_id, user_id, joined_at)
  - messages (id, room_id, user_id, text, sequence_id, created_at)
  - refresh_tokens (id, user_id, token_hash, expires_at)
- **Connection Pooling:** PgBouncer in transaction pooling mode (HikariCP can also be used).

**Cache & Pub/Sub:**
- **Redis** (in-memory):
  - Hash: `room:{roomId}:members` → online users (user_id → login_timestamp)
  - Cache: `messages:{roomId}` → last 100 messages per room (TTL: 24h)
  - Pub/Sub channels:
    - `room:{roomId}` → live messages
    - `room:{roomId}:typing` → typing indicators
    - `room:{roomId}:events` → join/leave events

**Infrastructure (Docker Compose):**
```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: giano
      POSTGRES_USER: giano_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"

  pgbouncer:
    image: edoburu/pgbouncer:1.21
    environment:
      DB_HOST: postgres
      DB_USER: giano_user
      DB_PASSWORD: ${DB_PASSWORD}
      DB_NAME: giano
      POOL_MODE: transaction
      MAX_CLIENT_CONN: 100
      DEFAULT_POOL_SIZE: 25
    ports:
      - "6432:6432"
    depends_on:
      - postgres

  app:
    build: .
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://pgbouncer:6432/giano
      SPRING_DATASOURCE_USERNAME: giano_user
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      JWT_EXPIRY_MINUTES: 15
      JWT_REFRESH_EXPIRY_DAYS: 7
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
      - pgbouncer

volumes:
  postgres_data:
  redis_data:
```

### Frontend Stack

**Framework & Build Tool:**
- Vite (fast dev server, optimized build)
- React 18 with TypeScript (or Vue 3 + TypeScript)
- Tailwind CSS or Material-UI for styling

**Real-Time Communication:**
- STOMP over WebSocket (using `@stomp/stompjs` library)
- or native WebSocket with custom protocol

**State Management:**
- React Context API (simple) or Zustand/Jotai (lightweight alternative)
- Redux optional (adds complexity not needed for this scope)

**HTTP Client:**
- axios or fetch API for REST calls

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
│   │   ├── api.ts (Axios instance + base config)
│   │   ├── authService.ts
│   │   ├── roomService.ts
│   │   └── websocketService.ts
│   ├── store/ (Context or state management)
│   │   ├── authContext.tsx
│   │   ├── roomContext.tsx
│   │   └── messageContext.tsx
│   ├── App.tsx
│   ├── main.tsx
│   └── index.css
├── vite.config.ts (proxy setup for /api and /ws to backend)
├── package.json
└── .env.local (API_URL, WS_URL)
```

---

## Phase Breakdown & Milestones

### Phase 1: Setup & Infrastructure (Week 1–2)
**Deliverables:**
- Docker Compose environment (Postgres, Redis, PgBouncer, Spring Boot skeleton).
- Database schema (users, rooms, room_members, messages, refresh_tokens).
- Vite frontend scaffold (React/Vue setup, Tailwind styling, folder structure).
- GitHub repo with README.

**Tasks:**
- [Backend] Initialize Spring Boot project with Modulith, WebSocket dependencies.
- [Backend] Create entities and JPA repositories for users, rooms, messages.
- [DevOps] Write Dockerfile, docker-compose.yml, db init scripts.
- [Frontend] npm create vite, install deps (react, axios, stompjs, tailwind).
- [Frontend] Set up Vite proxy for backend (/api, /ws).
- [All] Create GitHub repo, set up branch strategy (main, dev).

### Phase 2: Authentication (Week 2–4)
**Deliverables:**
- Signup/login endpoints (JWT + refresh tokens).
- Email verification (basic; send verification link via email).
- Password reset flow.
- JWT validation middleware.
- React/Vue login/signup forms + auth hooks.
- Postman collection for auth endpoints.

**Tasks:**
- [Backend] Implement JWT generation/validation (HS256, 15 min access, 7 day refresh).
- [Backend] Create /auth/signup, /auth/login, /auth/refresh, /auth/reset-password endpoints.
- [Backend] Add bcrypt password hashing (Spring Security's PasswordEncoder).
- [Backend] Email service stub (print to console or use fake SMTP for testing).
- [Backend] JWT validation filter on all protected endpoints.
- [Frontend] LoginForm, SignUpForm components.
- [Frontend] useAuth hook (manage JWT in localStorage, auto-refresh).
- [Frontend] Axios interceptor for Authorization header.
- [Frontend] Protected route wrapper (redirect to login if not authenticated).

### Phase 3: WebSocket & Real-Time Messaging (Week 4–7)
**Deliverables:**
- WS server (STOMP endpoint, message routing).
- Message persistence (save to DB on send).
- Redis Pub/Sub integration (publish/subscribe per room).
- Catch-up API (fetch messages by sequence_id).
- Frontend WS client (connect, subscribe, send, receive, reconnect).
- Basic e2e test (two clients sending/receiving).

**Tasks:**
- [Backend] Configure Spring WebSocket + STOMP.
- [Backend] Create @MessageMapping handlers: /app/room/{roomId}/send, /app/room/{roomId}/subscribe.
- [Backend] Message service: validate, persist, publish to Redis.
- [Backend] Sequence_id generation (auto-increment per room or local counter).
- [Backend] Redis Pub/Sub listeners (in separate thread pool or reactive).
- [Backend] GET /api/rooms/{roomId}/messages endpoint (pagination, catch-up).
- [Frontend] WebSocket service (StompClient wrapper, connect/disconnect/send).
- [Frontend] ChatWindow component (show messages, input field).
- [Frontend] Message list with auto-scroll, sender info, timestamps.
- [Frontend] useMessages hook (manage message state, catch-up on reconnect).
- [Frontend] Reconnection logic (exponential backoff, queue messages).
- [Testing] Manual test with 2 browser tabs, verify message delivery.

### Phase 4: Room Management (Week 7–8)
**Deliverables:**
- Create/list/join/leave room endpoints.
- Room member management (track members, show online count).
- Frontend room list sidebar, create room form, room member list.

**Tasks:**
- [Backend] RoomService: create, list, join, leave, get members.
- [Backend] Validate user is member before allowing message send.
- [Backend] GET /api/rooms (list all public rooms).
- [Backend] POST /api/rooms (create room, requires auth).
- [Backend] POST /api/rooms/{id}/join, /api/rooms/{id}/leave.
- [Backend] GET /api/rooms/{id}/members (list online/offline status).
- [Frontend] RoomList sidebar component (show joined rooms, join button).
- [Frontend] CreateRoomForm modal.
- [Frontend] RoomInfo panel (show name, description, member list).
- [Frontend] Auto-update member list on join/leave.

### Phase 5: Presence & Typing Indicators (Week 8–9)
**Deliverables:**
- Online presence tracking (Redis).
- Typing indicators (broadcast typing state).
- System messages (user joined/left room).
- Frontend UI updates (show online count, typing indicator).

**Tasks:**
- [Backend] On WS connect, add user to Redis `room:{roomId}:members` hash.
- [Backend] On WS disconnect, remove from hash.
- [Backend] Publish join/leave events to Redis Pub/Sub.
- [Backend] Handle @MessageMapping("/room/{roomId}/typing") for typing indicator.
- [Frontend] Subscribe to presence channel, update member list in real-time.
- [Frontend] Send typing event on keydown (debounced), stop on timeout or blur.
- [Frontend] Display "User X is typing..." banner in chat.
- [Frontend] Show system messages (gray, smaller text): "User X joined the room".

### Phase 6: Error Handling & Polish (Week 9–10)
**Deliverables:**
- Auto-reconnect with exponential backoff.
- Error toasts and user-friendly messages.
- Message queue during offline.
- Rate limiting on backend.
- Postman collection with all endpoints.

**Tasks:**
- [Backend] Implement rate limiter (e.g., Bucket4j or Spring Rate Limiter).
- [Backend] Return proper error codes (4xx for validation, 5xx for server errors).
- [Backend] Exception handler (@ControllerAdvice) for consistent error responses.
- [Frontend] Reconnect logic (detect disconnect, backoff, retry, max attempts).
- [Frontend] Message queue (store in localStorage during offline, flush on reconnect).
- [Frontend] Toast/notification system for errors and success messages.
- [Frontend] Disable send button during offline, show queue status.
- [Testing] Manual chaos testing: kill Redis, restart server, kill network, etc.

### Phase 7: Testing & Documentation (Week 10–11)
**Deliverables:**
- JUnit tests (auth, room creation, message persistence, sequence_id ordering).
- Postman collection (all endpoints, example requests/responses).
- README with setup, architecture, testing instructions.
- Demo script + recording.

**Tasks:**
- [Backend] JUnit 5 tests: AuthService, RoomService, MessageService, Repository tests.
- [Backend] Testcontainers for integration tests (Postgres + Redis).
- [Backend] Test edge cases (duplicate messages, invalid user, room not found, etc.).
- [Testing] Create Postman collection with auth flow, room CRUD, message catch-up.
- [All] Write README: project overview, setup steps (docker compose up), architecture diagram.
- [Frontend] Lighthouse performance audit, fix if needed.
- [All] Record demo video (sign up, create room, send messages, reconnect, offline recovery).

### Phase 8: Final Polish & Review (Week 11–12)
**Deliverables:**
- Code cleanup, comments, and refactoring.
- Security audit (password hashing, JWT validation, CORS settings).
- Performance tuning (message query optimization, Redis connection pooling).
- Final testing and bug fixes.

**Tasks:**
- [All] Code review within trio, address feedback.
- [Backend] Review SQL queries for N+1 problems, add indexes if needed.
- [Backend] Check CORS configuration (frontend origin allowed).
- [Backend] Verify all endpoints secured with JWT.
- [Frontend] Check console for warnings/errors, clean up.
- [All] Final e2e testing (end-to-end flow from signup to chat).
- [All] Prepare presentation slides.

---

## Success Metrics & Acceptance Criteria

**Functional:**
- ✅ User can sign up, verify email, log in, and log out.
- ✅ User can create a room with name and description.
- ✅ User can join a public room and leave.
- ✅ User can send a message and see it appear instantly for other users in the room.
- ✅ User can see the last 50 messages when entering a room (message history).
- ✅ User can see who is online in a room (member list with online indicator).
- ✅ User's browser reconnects automatically if network drops; they see "Reconnecting..." banner.
- ✅ User receives missed messages after reconnect (catch-up from Redis cache).
- ✅ Typing indicators show when another user is typing.
- ✅ System messages appear when user joins/leaves room.

**Non-Functional:**
- ✅ 10 concurrent users chatting without message loss or significant latency.
- ✅ Message delivery latency <100ms for live delivery (WS Pub/Sub).
- ✅ Reconnect completes in <5s on average (with backoff).
- ✅ Docker Compose environment starts with `docker-compose up -d` and all services healthy.
- ✅ All unit and integration tests pass (>80% code coverage).
- ✅ Zero SQL injection vulnerabilities (parameterized queries).
- ✅ JWT properly validated on all protected endpoints; invalid tokens rejected.
- ✅ Passwords hashed with bcrypt (min 12 rounds).

**Documentation:**
- ✅ README with setup instructions and architecture overview.
- ✅ Database schema diagram (ER diagram).
- ✅ API documentation (endpoints, request/response examples, error codes).
- ✅ Postman collection (runnable requests for all features).
- ✅ Code comments explaining key logic (WS routing, catch-up, reconnection).
- ✅ Demo video showing full user flow.

**Code Quality:**
- ✅ No hardcoded secrets in code (use environment variables).
- ✅ Consistent coding style (Spring/Java conventions, React/Vue best practices).
- ✅ Clear module separation (backend: auth, user, messaging, room; frontend: components, hooks, services).
- ✅ Meaningful commit history (clear commit messages, 1 feature per commit when possible).
- ✅ GitHub repo with clean history (no large binary files, .gitignore configured).

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| WebSocket connection issues (firewall, proxy) | Medium | High | Test behind corporate proxy early; provide fallback (long-polling) if time allows; document known issues. |
| Database performance (N+1 queries, slow transactions) | Medium | Medium | Use Spring Data projections, add indexes, profile with logs early. Do bulk loading tests with 10k messages. |
| Team skill gaps (Docker, Spring Modulith, JWT) | Low | Medium | Allocate Week 1 for learning/setup; pair program on complex parts (WS + Redis). Have senior dev (if available) review. |
| Redis data loss on restart | Low | Low | Presence and cache are ephemeral by design. Document this limitation. Redis AOF is already enabled in Docker Compose config (`--appendonly yes`). |
| Email delivery failures (testing without SMTP) | High | Low | Use mock email service for dev (in-memory or print to console); use Mailtrap or similar for staging. Don't block on email for signup if unverified state is acceptable. |
| Schedule slip (scope creep) | Medium | High | Strict prioritization: must-have only; defer should-have features. Weekly standup to track progress. Cut scope if behind. |
| Merge conflicts (3 devs, same file edits) | Low | Low | Use GitHub branches, clear ownership of modules. Merge daily. |

---

## Deployment & Running

### Local Development

**Prerequisites:**
- Docker & Docker Compose
- Java 17+ (for IDE support, not required if only using Docker)
- Node 18+ (for Vite frontend)
- Git

**Setup:**
```bash
# Clone repo
git clone <repo-url>
cd giano

# Start backend infrastructure
docker-compose up -d

# Wait for services to be ready (30s)
sleep 30

# Build and run backend
cd backend
./mvnw spring-boot:run

# In another terminal, start frontend
cd frontend
npm install
npm run dev
# Open http://localhost:5173 in browser

# Run tests
cd backend
./mvnw test

# View logs
docker-compose logs -f
```

**Troubleshooting:**
- "Connection refused": Wait 30s for Postgres to start, or check `docker-compose ps`.
- "Port already in use": Change port in docker-compose.yml or kill process: `lsof -i :8080`.
- "JWT secret not set": Set `JWT_SECRET=test-secret-12345` in .env or export before docker-compose up.

### Testing & Demo

**Manual Testing (QA Script):**
1. Open http://localhost:5173 in two browser windows.
2. Window 1: Sign up (new email), verify email (check logs for link), log in.
3. Window 2: Sign up (different email), log in.
4. Window 1: Create room "General".
5. Window 1: Send message "Hello from user 1".
6. Window 2: Join room "General".
7. Window 2: See previous message and typing indicator if Window 1 is typing.
8. Window 2: Send message "Hi user 1".
9. Window 1: See message instantly.
10. Kill Window 2 internet (close tab or disconnect network).
11. Window 1: Send more messages.
12. Window 2: Reconnect (refresh or reconnect network).
13. Window 2: See "reconnecting" banner, then catch-up messages appear.
14. Verify both users show as online in member list.

**Automated Testing (JUnit + Postman):**
```bash
# Run backend tests
./mvnw test

# Run with coverage
./mvnw jacoco:report
```

---

## Deliverables Checklist

**Week 1-2 (Infrastructure):**
- [ ] Docker Compose file with all services
- [ ] Database schema + migrations
- [ ] GitHub repo created, README started
- [ ] Spring Boot skeleton with WebSocket dependency
- [ ] Vite frontend scaffold

**Week 2-4 (Auth):**
- [ ] Signup/login endpoints + tests
- [ ] JWT generation & validation
- [ ] Email verification (stub)
- [ ] React/Vue login forms
- [ ] Postman collection (auth endpoints)

**Week 4-7 (Messaging):**
- [ ] WS server with STOMP
- [ ] Message persistence
- [ ] Redis Pub/Sub integration
- [ ] Catch-up API
- [ ] Frontend WS client
- [ ] E2E messaging test (manual + automated)

**Week 7-8 (Rooms):**
- [ ] Room CRUD endpoints
- [ ] Room membership management
- [ ] Frontend room list, create form

**Week 8-9 (Presence):**
- [ ] Online presence tracking
- [ ] Typing indicators
- [ ] Frontend presence UI

**Week 9-10 (Resilience):**
- [ ] Auto-reconnect logic
- [ ] Error handling + toasts
- [ ] Rate limiting
- [ ] Message queue during offline

**Week 10-11 (Testing & Docs):**
- [ ] JUnit test suite (>80% coverage)
- [ ] Integration tests (Testcontainers)
- [ ] Postman collection (all endpoints)
- [ ] README with setup & architecture
- [ ] Database schema diagram
- [ ] API documentation
- [ ] Demo video

**Week 11-12 (Polish):**
- [ ] Code review & refactoring
- [ ] Security audit checklist signed off
- [ ] Performance tuning + benchmarks
- [ ] Final e2e testing
- [ ] Presentation slides prepared

---

## References & Resources

**Spring Boot & WebSocket:**
- [Spring Boot WebSocket Tutorial](https://spring.io/guides/gs/messaging-stomp-websocket)
- [Spring Modulith Documentation](https://spring.io/projects/spring-modulith)
- [Spring Security & JWT](https://www.baeldung.com/spring-security-authentication-with-a-database)

**Redis:**
- [Spring Data Redis Pub/Sub](https://spring.io/guides/gs/messaging-redis)
- [Redis Pub/Sub Best Practices](https://redis.io/topics/pubsub)

**React/Vue + WebSocket:**
- [@stomp/stompjs Documentation](https://stomp-js.github.io/stomp-js/latest/)
- [Vite React Template](https://vitejs.dev/guide/#scaffolding-your-first-vite-project)
- [Real-Time Chat with React & WebSocket](https://www.ably.io/tutorials/websockets-react-tutorial)

**Testing:**
- [JUnit 5 Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Testcontainers for Postgres & Redis](https://www.testcontainers.org/)

**Docker & DevOps:**
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [PgBouncer Configuration](https://pgbouncer.github.io/config.html)

---

## Appendix: Sample Data Model

**Users Table:**
```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100),
  avatar_url TEXT,
  email_verified BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);
```

**Rooms Table:**
```sql
CREATE TABLE rooms (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  created_by BIGINT NOT NULL REFERENCES users(id),
  is_public BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP NULL
);
```

**Room Members Table:**
```sql
CREATE TABLE room_members (
  id BIGSERIAL PRIMARY KEY,
  room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, user_id)
);
```

**Messages Table:**
```sql
CREATE TABLE messages (
  id BIGSERIAL PRIMARY KEY,
  room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  sequence_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(room_id, sequence_id)
);

CREATE INDEX idx_messages_room_sequence ON messages(room_id, sequence_id DESC);
```

**Refresh Tokens Table:**
```sql
CREATE TABLE refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
```

---

**Document Version:** 1.1
**Last Updated:** 2026-03-17
**Prepared for:** 3-person development team (school project, 8–12 weeks)
