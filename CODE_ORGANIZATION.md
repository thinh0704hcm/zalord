# Question: What code-organization architecture is the backend using, and why does it fit this system better than Clean Architecture or Vertical Slice Architecture?

> Note: "architecture" here means **how code is organized inside one service**, not system architecture (microservices vs monolith — that's a separate question).

---

## 1. Current choice: Layered Architecture (3-tier)

Every Java/Spring Boot service uses the same layout:

```
service-name/src/main/java/zalord/<service>_service/
├── controller/        ← Layer 1: Presentation (HTTP endpoint, request/response)
│   └── MessageController.java
├── dto/               ← Data Transfer Object (input/output shape)
├── service/           ← Layer 2: Business logic
│   ├── IMessageService.java          (interface)
│   └── impl/
│       └── MessageServiceImpl.java   (implementation)
├── repository/        ← Layer 3: Data access (Spring Data JPA)
│   └── MessageRepository.java
├── model/             ← Entity (JPA @Entity)
│   └── Message.java
│
├── grpc/              ← Infra adapter (outbound gRPC client)
├── eventbus/          ← Infra adapter (broker publisher)
├── worker/            ← Background scheduler
├── exception/         ← Custom exceptions + handler
└── config/            ← Spring bean wiring
```

**Dependency flow** only goes **downward**:

```
controller ──► service ──► repository ──► database
     │             │
     │             ├──► grpc client (call another service)
     │             └──► eventbus publisher (write to outbox / broker)
     │
     └──► dto (downward), entity → dto (upward)
```

This is classic **Layered Architecture** — separates 3 responsibilities:
1. **Presentation** (controller): receive HTTP, parse body, call service
2. **Business** (service): business logic, transaction boundary
3. **Persistence** (repository): query DB

Service-to-service calls (gRPC) and event publishing (broker) are treated as **outbound adapters** — written in `grpc/` and `eventbus/` packages, injected into the service like any other dependency.

Go services (`chat`, `user`, `notification`) are also layered but flatter: `handler/` (controller equivalent), `events/` (eventbus), `session/`/`presence/` (state).

---

## 2. The 3 choices side-by-side

### 2.1 Layered Architecture (current)

```
controller/MessageController.java       ← ONE file for ALL message endpoints (send, recall, edit)
service/impl/MessageServiceImpl.java    ← ONE class containing send(), recall(), edit()
repository/MessageRepository.java        ← ONE interface querying everything about messages
```

**Organized by "kind"** of code: all controllers in one place, all services in another, all repositories in another.

### 2.2 Clean Architecture (Hexagonal/Onion)

```
domain/
├── Message.java                       ← pure entity, NO Spring/JPA dependency
└── port/
    ├── MessageRepositoryPort.java     ← interface "store message"
    └── EventPublisherPort.java        ← interface "publish event"

application/
└── usecase/
    ├── SendMessageUseCase.java        ← logic, depends on port not impl
    └── RecallMessageUseCase.java

adapter/
├── inbound/
│   └── MessageController.java         ← HTTP, calls UseCase
└── outbound/
    ├── JpaMessageRepository.java      ← implements MessageRepositoryPort
    └── RabbitEventPublisher.java      ← implements EventPublisherPort
```

**Domain at the center**, dependencies always point **inward**. Outer layers (HTTP, DB, broker) are plugins — swappable without touching domain.

### 2.3 Vertical Slice Architecture (VSA)

```
features/
├── send-message/
│   ├── SendMessageRequest.java        ← DTO for this feature only
│   ├── SendMessageEndpoint.java       ← HTTP route + handler inline
│   ├── SendMessageHandler.java        ← logic + DB call inline
│   └── SendMessageResponse.java
│
├── recall-message/
│   ├── RecallMessageRequest.java
│   ├── RecallMessageEndpoint.java
│   ├── RecallMessageHandler.java
│   └── RecallMessageResponse.java
│
└── edit-message/
    └── ...
```

**Organized by "feature"**. Each slice is self-contained from HTTP down to DB. No shared classes between slices.

---

## 3. Detailed comparison

| Criterion | **Layered** (current) | **Clean Architecture** | **Vertical Slice** |
|---|---|---|---|
| **Files per feature** | 3 (controller method + service method + repo method) | 5-7 (entity + port × 2 + usecase + adapter × 2 + dto) | 3-4 (request + endpoint + handler + response) |
| **Sharing logic across features** | Easy — same service class | Via shared use case base or domain service | Hard — slices are isolated, often duplicated |
| **Sharing dependencies** (e.g. `messageRepo`, `mediaGrpc`, `RabbitMQConfig`) | Inject once into service class | Inject once into use case (still OK) | **Every slice must re-inject** — verbose |
| **Spring annotation alignment** | ✅ Perfect: `@RestController`, `@Service`, `@Repository`, `@Transactional` all assume layered | ⚠️ Must "fight" Spring — domain can't use JPA annotations | ⚠️ Spring has no built-in MediatR equivalent, must roll your own |
| **Test isolation** | Integration test with Postgres test container | Pure unit test of domain, mock every port | Unit test handler with mocks |
| **Adding a new feature** | Add method to controller + service + repo | Create new use case, possibly new port | Create new slice folder, copy template |
| **Schema refactor** | Edit one place (`Message.java` + `MessageRepository.java`) | Edit domain entity + edit adapter | Edit N slices (each slice has its own query) |
| **Learning curve** | Low — well-known convention | High — must grasp port/adapter/usecase | Medium — simple but cuts against Spring habits |
| **Duplicate filenames** | No (1 per service/feature) | Possible (entity vs port vs adapter same name) | Yes (every slice has Request/Response/Handler) |

---

## 4. Why Layered fits Zalord

### 4.1 Zalord services are small (5-10 endpoints each)

`message-service` has ~8 main endpoints (send, recall, edit, list, mark-read, reply, ...). Spreading into Clean creates 30-40 files for 8 endpoints = noise > value.

VSA also needs 24-32 files (3-4 files per endpoint), while those 8 endpoints **share heavily**: `messageRepo`, `outboxRepo`, `mediaGrpc`, `RabbitMQConfig.MESSAGE_CREATED_ROUTING_KEY`, `attachmentRepo`, `memberRepo` — VSA forces each slice to re-inject these 6 dependencies.

### 4.2 Heavy logic sharing — can't slice cleanly

Inside `MessageServiceImpl`:
- `send()` needs: validate members, validate media, write message, write outbox, build event
- `recall()` needs: validate sender, validate not-recalled, mark recalled, **write outbox** (same routing key!), build event
- `reply()` needs: everything from `send()` + validate parent message, snapshot preview

**Same dependencies, same outbox pattern, same eventbus setup**. Putting them in one service class lets us share helpers (`enqueueOutbox`, `snapshotPreview`, `toResponse`). Splitting into 3 slices means copy-pasting the outbox setup 3 times and re-injecting `outboxRepo`/`RabbitMQConfig` 3 times.

### 4.3 Cross-cutting concerns suit layered

| Concern | Implementation | Why layered fits |
|---|---|---|
| **Transactional Outbox** | `enqueueOutbox()` private method in service | Every feature writes outbox the same way → centralize at service layer |
| **Circuit Breaker (R4j)** | `@CircuitBreaker` on gRPC client method | Wrap once in grpc package, every caller benefits |
| **JWT auth** | Kong injects `X-User-Id` header, controller reads | Common to all endpoints → install in one place (controller or filter) |
| **Tracing (OTel)** | Auto agent instruments by layer (Spring MVC, JDBC, Hibernate, gRPC) | OTel semantic conventions assume layered structure |
| **Metrics (Micrometer)** | Spring Boot Actuator auto-records HTTP/JDBC/JVM | Same — conventions are layered-based |

VSA and Clean require re-wiring these concerns into each slice/use case → losing the convention reward.

### 4.4 Spring Boot is convention-over-configuration

Spring **rewards** following the layered convention:
- `@RestController` → auto-registers routes
- `@Service` → default bean scope
- `@Repository` → automatic exception translation
- `@Transactional` → AOP proxy fits layered (apply at service layer, not controller or repository)

Clean Architecture **forbids** these annotations in the domain (because domain shouldn't know about Spring). Result: you write an extra adapter layer just to map JPA entities ↔ domain entities, losing ergonomics for an abstraction nobody uses.

### 4.5 Team of 3, 3 months, no churn

Clean and VSA shine when:
- Team is large (>10), lots of juniors, need strong guardrails
- Long lifetime (>2 years), many refactors expected
- Multiple UIs / multiple persistence backends

Zalord: team of 3, 3-month deadline, 1 UI (web), 1 DB (Postgres + Scylla). No pressure forces Clean or VSA — **simpler structure = ship faster**.

---

## 5. When would Layered stop fitting?

Layered has 3 commonly cited weaknesses:

| Layered weakness | Affects Zalord? |
|---|---|
| Service class grows into a "god class" as features pile up | ❌ No — Zalord services stay small (~300 lines max) |
| Hard to unit-test purely because of Spring coupling | ⚠️ Yes — but integration tests against real DB are **better** than mock-heavy unit tests for Zalord (real bugs are races + constraints; mocks miss them) |
| Hard to swap persistence | ❌ No plans to swap Postgres |

→ **None of these hit a threshold that forces a change in Zalord's context.**

If Zalord scales up later — `message-service` grows to 50+ endpoints with very different flows, or the team grows past 10 devs — migrating to Clean (for domain-heavy services) or VSA (for endpoint-heavy services) becomes reasonable.

---

## 6. Conclusion

| Question | Answer |
|---|---|
| **What architecture is in use?** | **Layered Architecture (3-tier)** — controller/service/repository, dependencies flow downward only |
| **Why not Clean?** | Services are small + we follow Spring convention. Clean creates 5-7 files per feature instead of 3, loses framework ergonomics, and no use case justifies it (no DB swap, no multiple UIs) |
| **Why not Vertical Slice?** | Features inside one service **share dependencies heavily** (repo + grpc + eventbus + config). VSA would force re-injecting 6 things into every slice → verbose. Shared logic (e.g. `enqueueOutbox`) ends up duplicated |
| **When would we switch?** | When a service has >50 endpoints (consider VSA) or we need to swap framework/DB (consider Clean) — not at that threshold yet |

**Bottom line**: pick **simple-and-correct for the scope** over **pure architecture orthodoxy**. Layered + Spring conventions is the fastest path to a working, defendable system in 3 months with 3 devs.
