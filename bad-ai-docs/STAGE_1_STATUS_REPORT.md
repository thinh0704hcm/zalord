# Stage 1 Status Report: Modular Monolith Baseline

## Summary

Stage 1 is not yet a valid comparison baseline for the later microservices extraction.

The project has solid early groundwork in schema design, auth entrypoints, chat domain modeling, chat service orchestration, and local infrastructure. However, the backend currently does not compile, protected API usage is not functional with JWT, messaging is not exposed over HTTP or WebSocket, and the frontend is still a starter scaffold.

## What Is Done

| Area | Status | Notes |
| --- | --- | --- |
| Database schema | Partial | Core tables exist for `auth.users`, `messaging.chats`, `messaging.messages`, and `messaging.chat_members` in [init.sql](/home/thinh0704hcm/zalord/infrastructure/postgres/init.sql). |
| Soft delete support | Done | Implemented on `User`, `Chat`, `ChatMember`, and `Message` entities via Hibernate soft-delete annotations. |
| JSON payload model | Done | Message payload polymorphism exists in [MessagePayload.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/domain/interfaces/MessagePayload.java). |
| Auth controller | Done | Register and login endpoints exist in [AuthController.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/auth/api/controller/AuthController.java). |
| Auth service | Partial | Register/login flow and JWT generation exist in [AuthService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/auth/application/AuthService.java) and [JwtService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/common/security/JwtService.java). |
| Exception handling | Done | Global exception mapping exists in [GlobalExceptionHandler.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/common/exception/GlobalExceptionHandler.java). |
| Chat domain model | Done | Chat, member, role, type, and message entities are defined under [backend/src/main/java/io/zalord/messaging](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging). |
| Chat service | Partial | Create, update, delete, promote, demote, transfer, leave, and remove flows exist in [ChatService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/application/ChatService.java). |
| Message repository | Partial | Basic latest-message and cursor-style slice queries exist in [MessageRepository.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/infrastructure/MessageRepository.java). |
| Local infra | Done | PostgreSQL, Redis, and PgBouncer are defined in [docker-compose.yml](/home/thinh0704hcm/zalord/docker-compose.yml). |

## What Is Incomplete or Blocking

| Area | Severity | Problem |
| --- | --- | --- |
| Backend build | Critical | The backend does not compile because [MessageService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/application/MessageService.java) is unfinished and references unresolved `MessageResponse`. |
| JWT request authentication | Critical | [SecurityConfig.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/common/security/SecurityConfig.java) requires authentication, but [JwtAuthFilter.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/common/security/JwtAuthFilter.java) is empty and not wired into the filter chain. |
| Messaging REST API | Critical | There is no controller exposing chat or message operations. Only auth has HTTP endpoints. |
| Message service | Critical | `createMessage()` is unimplemented in [MessageService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/application/MessageService.java). |
| Message response DTO | Critical | [MessageResponse.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/api/dto/MessageResponse.java) is empty. |
| WebSocket/STOMP | Critical | `spring-boot-starter-websocket` is in [pom.xml](/home/thinh0704hcm/zalord/backend/pom.xml), but there is no broker config, no STOMP endpoint, and no message handler. |
| Frontend app | Critical | The frontend is still the Vite starter screen in [App.tsx](/home/thinh0704hcm/zalord/frontend/src/App.tsx). |
| Integration coverage | High | Current tests only cover auth; there are no end-to-end flows for chat creation or message sending. |
| Load baseline | High | No latency/throughput baseline has been captured yet for later comparison. |
| User service | Low | [UserService.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/auth/application/UserService.java) is still a skeleton. |

## Correctness Issues To Fix Before Calling Stage 1 "Done"

- The schema is not fully valid yet. [init.sql](/home/thinh0704hcm/zalord/infrastructure/postgres/init.sql) makes `chat_members(member_id)` unique, which would prevent one user from joining multiple chats.
- The messages index in [init.sql](/home/thinh0704hcm/zalord/infrastructure/postgres/init.sql) is also unique on `(chat_id, created_at)`, which can reject legitimate concurrent messages.
- Native upsert logic in [ChatMemberRepository.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/infrastructure/ChatMemberRepository.java) is likely incomplete because the modifying query is not marked as a modifying operation.
- Pagination code in [ChatRepository.java](/home/thinh0704hcm/zalord/backend/src/main/java/io/zalord/messaging/infrastructure/ChatRepository.java) imports the wrong `Pageable` type.
- Messaging currently depends directly on auth-domain types, so the codebase is not yet as extraction-ready as it appears.

## Recommended Completion Checklist

- Make the backend compile cleanly.
- Implement JWT bearer-token validation and inject the authenticated principal into Spring Security.
- Add REST controllers for chat and messaging operations.
- Implement `MessageService.createMessage()` and update `chat.lastActivityAt`.
- Define `MessageResponse` fields and mapping.
- Add WebSocket/STOMP configuration and a message handler for real-time delivery.
- Replace the frontend starter page with a minimal usable client: login, chat list, chat view, send message.
- Add integration tests for auth, create chat, send message, and unauthorized access.
- Capture Stage 1 performance numbers before any service extraction.
- Clean up schema and repository correctness issues so the monolith is a reliable baseline.

## Baseline Verdict

Stage 1 is best described as "foundational backend and infrastructure in place, but not yet feature-complete or baseline-valid."

It is reasonable to continue building from here, but microservices extraction should wait until the monolith is compilable, testable, and usable end to end.
