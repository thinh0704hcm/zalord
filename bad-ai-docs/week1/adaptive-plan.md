# Adaptive Auth Plan

This folder is organized around progress states, not a calendar.

Use it like this:

- identify your current state
- work only until that state's stop condition is met
- stop there
- ask for the next session plan only when you want it

## Baseline Assumption

- you already understand programming concepts
- you can read Java syntax
- your current weakness is manual Java/Spring implementation fluency because AI has been filling in too much of the code for you

## Main Objective

Rebuild manual coding fluency while constructing the smallest useful auth slice for Stage 1.

Target scope:

- `POST /api/auth/signup`
- `POST /api/auth/login`

Stretch scope later:

- JWT refinement
- `GET /api/auth/me`

Out of scope for now:

- room
- messaging
- presence
- WebSocket
- Redis
- microservices
- outbox
- resilience tooling

## Working Rules

- one Spring Boot app
- one PostgreSQL database
- one `auth` module
- no distributed patterns
- no empty wrapper classes unless they serve a clear purpose
- no “clean architecture” complexity unless you can explain each layer in plain words

## Manual Coding Rules

- reduce scope aggressively
- prefer hand-written code over “perfect” design
- write small classes fully by hand
- read examples, then recreate them manually
- do not hide confusion behind architecture vocabulary
- a simpler package structure is better than a more “correct” one that slows you down

## Progress States

### State 1: Project Runs

Focus:

- run the backend
- understand the current backend shape
- trace request flow at a high level

Stop condition:

- app starts
- you can explain `HTTP -> controller -> service -> repository -> database`

### State 2: Scope Is Fixed

Focus:

- choose the minimum auth scope
- decide table fields and endpoints
- configure datasource

Stop condition:

- auth scope decisions are written down
- datasource config exists

### State 3: Persistence Exists

Focus:

- manually write persistence classes

Classes:

- `User`
- `UserRepository`

Stop condition:

- both compile
- app boots

### State 4: DTOs And Service Skeleton Exist

Focus:

- manually write DTOs and service skeleton

Classes:

- `SignUpRequest`
- `LoginRequest`
- `AuthResponse`
- `AuthService`

Stop condition:

- all compile
- app boots

### State 5: Signup Works

Focus:

- manually write controller
- finish signup end-to-end

Classes:

- `AuthController`

Stop condition:

- signup request creates a real user row

### State 6: Login Works

Focus:

- finish login end-to-end

Stop condition:

- correct login returns success
- wrong login returns a controlled 4xx-style failure

### State 7: Ownership And Stability

Focus:

- stabilize what you wrote
- rewrite any class you still do not understand
- optionally add JWT refinement or `/me`

Stop condition:

- signup works
- login works
- you can explain every auth class you wrote

## Dependency Order

Even with an adaptive schedule, preserve this order:

1. app boots
2. database connects
3. `User` exists
4. signup works
5. login works
6. JWT works
7. `/me` works

## Success Criteria

The current phase is successful if:

- the backend boots consistently
- the auth package structure no longer feels random
- you can explain each auth class in one sentence
- the app can create a user
- login is implemented or one small step away
- you manually wrote the important classes yourself
