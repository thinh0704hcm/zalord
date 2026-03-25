# Adaptive Checklist

Use this alongside `adaptive-plan.md`. That file explains the states. This file tells you exactly what to do and when to stop.

Rule: check a box only when you personally verified it, not when AI said it should work.

---

## State 1 Session — Run the project and understand its shape

Goal: the app boots, and you can explain the request path from memory.

- [ ] run `./mvnw spring-boot:run` from `backend/`
- [ ] app starts without errors
- [ ] open `pom.xml` and identify: Spring Boot starter web, Spring Data JPA, security, database driver
- [ ] read `zalordApplication.java` — one line on what it does
- [ ] confirm root package is `io.zalord`
- [ ] read one small Spring Boot `@RestController` example from official docs or Baeldung
- [ ] close the example and write one sentence each on: controller, service, repository, entity
- [ ] write the request path from memory: HTTP → controller → service → repository → database

Stop condition: you can trace the request path without looking at any doc.

Do not start writing auth classes today.

---

## State 2 Session — Make scope decisions

Goal: all immediate auth decisions are written down before any class is created.

- [ ] configure database connection in `application.properties` or `application.yml`
- [ ] app still boots after adding datasource config
- [ ] write down your `User` table fields (use the minimum from `adaptive-plan.md`)
- [ ] write down your immediate endpoints: `POST /api/auth/signup`, `POST /api/auth/login`
- [ ] confirm: refresh tokens are later, not now
- [ ] confirm: Spring Security filter chain is later unless signup/login are finished early
- [ ] write down one sentence on what is out of scope right now

Stop condition: you have a written list of fields, endpoints, and one confirmed deferral decision. No new code today unless datasource config required it.

---

## State 3 Session — Write `User` and `UserRepository` by hand

Classes to write today, in this order:

1. `User`
2. `UserRepository`

- [ ] create `auth/domain/User.java`
- [ ] write `User` manually: `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, fields from Day 2 decision
- [ ] `User` compiles with `./mvnw compile`
- [ ] create `auth/domain/UserRepository.java` (or `auth/service/` if using the simpler structure)
- [ ] write `UserRepository` manually: extends `JpaRepository<User, Long>`, add `findByEmail`
- [ ] `UserRepository` compiles
- [ ] app still boots

Stop condition: both classes compile and the app boots. Stop here even if time remains. Do not start DTOs today.

If `User` does not compile after two focused attempts: write down the exact error, look up only that annotation or type, fix it, compile again.

---

## State 4 Session — Write DTOs and `AuthService` skeleton by hand

Classes to write today, in this order:

1. `SignUpRequest`
2. `LoginRequest`
3. `AuthResponse`
4. `AuthService` (skeleton only — method stubs, no logic yet)

- [ ] create `auth/api/SignUpRequest.java`: `email`, `password`, `displayName` fields
- [ ] create `auth/api/LoginRequest.java`: `email`, `password` fields
- [ ] create `auth/api/AuthResponse.java`: `accessToken`, `userId`, `email`, `displayName` fields
- [ ] all three DTO classes compile
- [ ] create `auth/service/AuthService.java` with `@Service`
- [ ] write `signUp(SignUpRequest request)` method stub — return type your choice, body can throw `UnsupportedOperationException` for now
- [ ] write `login(LoginRequest request)` method stub — same
- [ ] `AuthService` compiles
- [ ] app still boots

Stop condition: four classes compile and the app boots. Do not write `AuthController` today unless all four are done with time left over.

---

## State 5 Session — Write `AuthController` and complete signup end-to-end

Classes to write today, in this order:

1. `AuthController`
2. Fill in `AuthService.signUp` logic

- [ ] create `auth/api/AuthController.java` with `@RestController`, `@RequestMapping("/api/auth")`
- [ ] inject `AuthService` via constructor
- [ ] write `POST /signup` endpoint that calls `authService.signUp(request)` and returns a response
- [ ] `AuthController` compiles
- [ ] app boots
- [ ] fill in `AuthService.signUp`:
  - [ ] check for duplicate email via `userRepository.findByEmail`
  - [ ] hash password with `BCryptPasswordEncoder`
  - [ ] save new `User` via `userRepository.save`
  - [ ] return an `AuthResponse` (no token yet is acceptable)
- [ ] test signup manually with curl or Bruno/Postman
- [ ] a new user row appears in the database

Stop condition: a real signup request creates a real database row. If JWT is not ready, return a placeholder token or empty string — do not block on it.

If signup is blocked by a Spring wiring error: paste only the error line (not the full stack) into your notes. Look up that specific error. Do not restructure the project to escape it.

---

## State 6 Session — Complete login end-to-end

- [ ] fill in `AuthService.login`:
  - [ ] find user by email — return error if not found
  - [ ] verify raw password against stored hash with `BCryptPasswordEncoder.matches`
  - [ ] return an `AuthResponse`
- [ ] test login manually — correct credentials succeed
- [ ] test login manually — wrong password returns an error response, not a 500
- [ ] if JWT is ready: generate a real token and include it in `AuthResponse`
- [ ] if JWT is not ready: return a placeholder and note it as a later session task

Stop condition: login with correct credentials returns a 2xx response. Login with wrong credentials returns a 4xx response without crashing.

---

## State 7 Session — Stabilize, rewrite what you do not understand, and plan next work

- [ ] read every auth class you wrote this week
- [ ] for each class: can you explain it in one sentence without looking at notes?
- [ ] rewrite any class you cannot explain — do it from scratch, not by patching
- [ ] rename any class or method that confused you this week
- [ ] if JWT is not done: implement `JwtService` now
- [ ] if JWT is done: implement `GET /api/auth/me` as stretch goal
- [ ] write a short list of what Week 2 must address

Stop condition for this phase: signup works, login works, and you can explain every auth class you wrote.

JWT and `/me` are stretch goals. Do not consider Week 1 failed if they are not done.

---

## Class Writing Order Summary

For reference, the full sequence across the phase:

| State | Class |
|-----|-------|
| 3 | `User` |
| 3 | `UserRepository` |
| 4 | `SignUpRequest` |
| 4 | `LoginRequest` |
| 4 | `AuthResponse` |
| 4 | `AuthService` (skeleton) |
| 5 | `AuthController` |
| 5 | `AuthService` (signup logic) |
| 6 | `AuthService` (login logic) |
| 7 | `JwtService` (if not done earlier) |
| 7 | `SecurityConfig` (stretch) |

---

## Stop Conditions At A Glance

| State | You are done when |
|-----|-------------------|
| 1 | you trace the request path from memory |
| 2 | scope decisions are written, app boots with datasource |
| 3 | `User` and `UserRepository` compile, app boots |
| 4 | four classes compile, app boots |
| 5 | real signup creates a real database row |
| 6 | correct login returns 2xx, wrong login returns 4xx |
| 7 | signup works, login works, every class is explainable |

---

## When To Ask For Help

Ask when:

- a compile error has the same root cause after two separate fix attempts
- you do not understand what a Spring annotation actually does after reading the official docs once
- login logic does something you cannot explain in plain words

Do not ask when:

- the error message tells you exactly what is wrong
- you have not read the error message yet
- you have not tried compiling yet
