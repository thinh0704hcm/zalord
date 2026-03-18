# Week 1 Template Structures

These are suggested structures to copy manually as reference, not strict rules.

Since your issue is manual fluency, the simplest structure that you can write and explain is better than the “best” structure that slows you down.

## Best Week 1 Structure

Use this if you want the lowest confusion:

```text
backend/src/main/java/io/giano/
  GianoApplication.java
  auth/
    api/
      AuthController.java
      LoginRequest.java
      SignUpRequest.java
      AuthResponse.java
    service/
      AuthService.java
    domain/
      User.java
      UserRepository.java
```

This is enough for Week 1.

## Recommended Week 1 Backend Shape

```text
backend/src/main/java/io/giano/
  GianoApplication.java
  auth/
    api/
      AuthController.java
      LoginRequest.java
      SignUpRequest.java
      AuthResponse.java
    application/
      AuthService.java
    domain/
      User.java
    infrastructure/
      UserRepository.java
    package-info.java
  common/
    security/
      JwtService.java
      SecurityConfig.java
```

Use this only if the simpler structure already makes sense to you.

## Simpler Alternative

If the split above feels too heavy for Week 1, use this:

```text
backend/src/main/java/io/giano/
  GianoApplication.java
  auth/
    api/
      AuthController.java
      LoginRequest.java
      SignUpRequest.java
      AuthResponse.java
    service/
      AuthService.java
    domain/
      User.java
      UserRepository.java
```

This is acceptable for Week 1 if it helps you move forward.

For Week 1, this simpler alternative should be the default choice.

## Recommended Responsibility Split

### `AuthController`

Owns:

- HTTP endpoints
- request parsing
- returning response DTOs

Should not own:

- password hashing rules
- login business logic
- direct database logic

### `AuthService`

Owns:

- signup logic
- login logic
- duplicate email checks
- calling password hasher
- calling token generator

Should not own:

- HTTP annotations
- low-level SQL details

### `User`

Owns:

- auth-related persisted user data

Should not own:

- controller concerns
- request DTO concerns

### `UserRepository`

Owns:

- saving users
- finding users by email or id

Should not own:

- JWT generation
- controller response building

## Suggested Auth Endpoints For Week 1

```text
POST /api/auth/signup
POST /api/auth/login
```

Minimum enough for Week 1:

- signup
- login

Stretch goal:

- `GET /api/auth/me`

## Suggested Auth Build Order

Create files in this order:

1. `User`
2. `UserRepository`
3. `SignUpRequest`
4. `LoginRequest`
5. `AuthResponse`
6. `AuthService`
7. `AuthController`
8. `JwtService`
9. `SecurityConfig`

If you are behind schedule, stop after step 7 and move JWT to Week 2.

## Manual Practice Rule

When creating the files above:

- write them yourself from a reference, not by pasting generated code
- keep each class small
- compile often
- fix one error at a time
- prefer boring code over clever code

## Suggested Response Shapes

### Signup/Login

```json
{
  "accessToken": "token",
  "userId": 1,
  "email": "user@example.com",
  "displayName": "Thinh"
}
```

### Current User

```json
{
  "id": 1,
  "email": "user@example.com",
  "displayName": "Thinh"
}
```

## Suggested Module Rule For Week 1

Keep it simple:

- `auth` owns auth
- future modules must not directly edit auth internals
- shared technical code can go to `common`
- do not build shared abstractions too early

## What To Postpone

Leave these for later:

- refresh token persistence
- WebSocket auth
- module events
- strict Modulith verification
- room membership authorization
- distributed concerns
- deep package layering
- perfect Spring Security setup
