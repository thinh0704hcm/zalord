# Zalord API Demo Guide

Quick reference for frontend integration with the Zalord backend.

## Base URL

```
http://140.245.47.255
```

All requests go through Kong API Gateway. Path-based routing is configured — no special headers needed. Just use the base URL directly.

---

## Authentication Flow

### 1. Register

```bash
curl -X POST http://140.245.47.255/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Your Name",
    "phoneNumber": "0900000099",
    "password": "YourPass123!"
  }'
```

**Response (200):**
```json
{
  "status": "success",
  "message": "User registered successfully",
  "data": {
    "displayName": "Your Name",
    "phoneNumber": "0900000099",
    "createdAt": "2026-06-28T11:24:48.235Z"
  }
}
```

### 2. Login

```bash
curl -X POST http://140.245.47.255/api/v1/auth/login \
  -H "Host: auth.zalord.vn" \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "0900000099",
    "password": "YourPass123!"
  }'
```

**Response (200):**
```json
{
  "status": "success",
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eke97U0fv1Fva_KtQ..."
  }
}
```

### 3. Refresh Token

```bash
curl -X POST http://140.245.47.255/api/v1/auth/refresh \
  -H "Host: auth.zalord.vn" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eke97U0fv1Fva_KtQ..."
  }'
```

**Response:** Same shape as login — new `accessToken` + `refreshToken`.

### 4. Logout

```bash
curl -X POST http://140.245.47.255/api/v1/auth/logout \
  -H "Host: auth.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

### 5. Logout All Devices

```bash
curl -X POST http://140.245.47.255/api/v1/auth/logout/all \
  -H "Host: auth.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

---

## JWT Token Structure

The `accessToken` is a HS256 JWT with this payload:

```json
{
  "roles": ["USER"],
  "sub": "f8eb2702-09cc-49b5-b1c7-ca8d31cac6c5",
  "iss": "zalord",
  "iat": 1782645899,
  "exp": 1782646799
}
```

- **`sub`** = user ID (UUID)
- **`roles`** = `["USER"]` or `["ADMIN"]`
- **Expiry:** 15 minutes
- **Issuer:** `zalord`

---

## Service Hostnames

Every request needs a `Host` header matching the service:

| Service      | Host Header              | API Paths                     |
|--------------|--------------------------|-------------------------------|
| Auth         | `auth.zalord.vn`         | `/api/v1/auth/*`              |
| User         | `user.zalord.vn`         | `/api/v1/users/*`             |
| Chat         | `chat.zalord.vn`         | `/api/v1/chat/*`              |
| Message      | `message.zalord.vn`      | `/api/v1/messages/*`          |
| Group        | `group.zalord.vn`        | `/api/v1/groups/*`            |
| Media        | `media.zalord.vn`        | `/api/v1/media/*`             |
| Notification | `notification.zalord.vn` | `/api/v1/notifications/*`     |

---

## User Service

### Get Own Profile

```bash
curl http://140.245.47.255/api/v1/users/me \
  -H "Host: user.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

### Update Profile

```bash
curl -X PUT http://140.245.47.255/api/v1/users/me \
  -H "Host: user.zalord.vn" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "New Name",
    "bio": "Hello world"
  }'
```

### Send Friend Request

```bash
curl -X POST http://140.245.47.255/api/v1/users/friends/request \
  -H "Host: user.zalord.vn" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"targetUserId": "<uuid>"}'
```

### List Friends

```bash
curl http://140.245.47.255/api/v1/users/friends \
  -H "Host: user.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

---

## Group Service

### Create Group

```bash
curl -X POST http://140.245.47.255/api/v1/groups \
  -H "Host: group.zalord.vn" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Group",
    "description": "A test group"
  }'
```

### List Groups

```bash
curl http://140.245.47.255/api/v1/groups \
  -H "Host: group.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

---

## Media Service

### Upload Avatar

```bash
curl -X POST http://140.245.47.255/api/v1/media/upload \
  -H "Host: media.zalord.vn" \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@photo.jpg" \
  -F "type=avatar"
```

### Get Presigned Upload URL

```bash
curl -X POST http://140.245.47.255/api/v1/media/presign \
  -H "Host: media.zalord.vn" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "fileName": "photo.jpg",
    "contentType": "image/jpeg"
  }'
```

---

## Notification Service

### List Notifications

```bash
curl http://140.245.47.255/api/v1/notifications \
  -H "Host: notification.zalord.vn" \
  -H "Authorization: Bearer <accessToken>"
```

---

## Chat Service (WebSocket)

WebSocket connection goes through Kong on port 80:

```
ws://140.245.47.255/ws/chat?token=<accessToken>
```

> **Note:** WebSocket upgrade must include the JWT as a query parameter `token`.

---

## Error Responses

All services return errors in this format:

```json
{
  "status": "error",
  "message": "Description of what went wrong",
  "data": null,
  "errorCode": "ERROR_CODE",
  "timestamp": "2026-06-28T11:24:48.235Z"
}
```

Common error codes:

| HTTP | errorCode | Meaning |
|------|-----------|---------|
| 401 | `INVALID_CREDENTIALS` | Wrong phone/password |
| 401 | — | Missing/expired JWT |
| 400 | `PHONE_ALREADY_REGISTERED` | Phone number taken |
| 400 | `VALIDATION_ERROR` | Missing required fields |
| 500 | `INTERNAL_SERVER_ERROR` | Server error |

---

## Quick Start (JavaScript)

```javascript
const BASE = 'http://140.245.47.255';

async function api(service, path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      'Host': `${service}.zalord.vn`,
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });
  return res.json();
}

// Register
await api('auth', '/api/v1/auth/register', {
  method: 'POST',
  body: JSON.stringify({
    displayName: 'Frontend Dev',
    phoneNumber: '0900000088',
    password: 'Test1234!',
  }),
});

// Login
const { data } = await api('auth', '/api/v1/auth/login', {
  method: 'POST',
  body: JSON.stringify({
    phoneNumber: '0900000088',
    password: 'Test1234!',
  }),
});
const token = data.accessToken;

// Get profile (authenticated)
const profile = await api('user', '/api/v1/users/me', {
  headers: { 'Authorization': `Bearer ${token}` },
});
console.log(profile);
```

---

## Environment Variables (for `.env`)

```env
VITE_API_BASE_URL=http://140.245.47.255/api/v1
VITE_WS_URL=ws://140.245.47.255/ws/chat
```

> **Important:** The frontend must set the `Host` header per service. In browsers, you can't set `Host` headers directly on `fetch`. Two options:
>
> 1. **Use a reverse proxy** (e.g., Nginx) that routes based on path prefix: `/auth/*` → `auth.zalord.vn`, `/users/*` → `user.zalord.vn`, etc.
> 2. **Use a custom header** and configure Kong to route based on a custom header instead of `Host`.
>
> For local dev, the easiest approach is to add entries to `/etc/hosts`:
> ```
> 140.245.47.255 auth.zalord.vn user.zalord.vn chat.zalord.vn message.zalord.vn group.zalord.vn media.zalord.vn notification.zalord.vn
> ```
> Then use the hostnames directly in your API calls.
