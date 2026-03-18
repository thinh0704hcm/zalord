# Week 1 Learning And Research

## Learn First

Learn only what you need to manually build a very small `auth` module.

Assumption:

- you already know programming concepts
- you can understand code once you see it
- your problem is that you are out of practice writing the glue code yourself

## Learn In This Order

1. Maven basics
2. Spring Boot basics
3. HTTP and controller basics
4. service/repository/entity roles
5. JPA basics
6. password hashing basics
7. JWT basics only after signup/login flow makes sense

Do not try to learn all of Spring in one week.

### 1. Maven Basics

Understand:

- what `pom.xml` is
- how dependencies are added
- what `mvn spring-boot:run` or `./mvnw spring-boot:run` does
- what `mvn test` roughly means

Goal:

- you should be able to run the project without guessing
- you should know where dependencies and build commands come from

### 2. Spring Boot Basics

Understand:

- what `@SpringBootApplication` does
- what a bean roughly is
- how controllers receive HTTP requests
- how services hold business logic
- how repositories access the database
- how dependency injection works

You do not need advanced Spring internals yet.
You only need enough to understand how classes are wired together.

### 3. Spring Web

Understand:

- `@RestController`
- `@RequestMapping`
- `@PostMapping`
- request body binding
- response status codes

Goal:

- you should understand how a signup request reaches your controller and returns JSON

### 4. Controller, Service, Repository, Entity Roles

Understand:

- controller = HTTP entry point
- service = business logic
- repository = database access
- entity = persisted data model

This matters more than fancy architecture vocabulary.
If you understand these four roles clearly, most Week 1 confusion disappears.

### 5. Spring Data JPA

Understand:

- what an entity is
- how `@Entity` maps to a table
- what a repository interface does
- basic `save` and `findByEmail`

Goal:

- you should understand how a `User` record is stored and fetched

### 6. Spring Security Basics

Understand:

- what authentication means
- what authorization means
- how password hashing differs from encryption
- where JWT fits into login flow

Do not go deep into full Spring Security complexity on day 1.

### 7. BCrypt

Understand:

- why passwords are never stored raw
- how hashing and password verification work

Goal:

- you should understand signup hashing and login verification

### 8. JWT Basics

Understand:

- access token purpose
- token payload basics
- expiration
- how a protected endpoint reads identity from a token

Goal:

- enough understanding to implement login and one protected endpoint

## How To Rebuild Manual Fluency

Use this pattern all week:

1. Read a small example.
2. Close it.
3. Recreate it manually.
4. Compare the result.
5. Fix what you missed.

Do not spend the whole week reading. You need writing repetition.

## What To Research Versus What To Practice

Research:

- annotations you do not recognize
- JPA entity/repository syntax
- BCrypt usage
- JWT flow

Practice manually:

- DTO classes
- controller method signatures
- service methods
- repository method names
- simple configuration classes

## Research Questions

Answer these before or during implementation:

### App Structure

- Should `auth` use `api/service/domain/infrastructure`, or `api/service` only for Week 1?
- Is `AuthModule.java` actually useful, or just ceremony?
- Which package should contain shared security code later: `common.security` or inside `auth` first?

### Auth Design

- Will signup immediately return a JWT, or will login be the only token-issuing endpoint first?
- Do you need refresh tokens in Week 1, or can they wait until Week 2?
- Will your current-user endpoint be `/api/auth/me` or `/api/users/me`?

### Data Model

- What is the minimum `User` table needed for Week 1?
- Do you want soft delete now, or later?
- Do you want display names required or optional?

### Testing

- Will you test manually with Postman/Bruno/curl first?
- What is the first test you want automated?

## Suggested Research Order

1. Maven basics
2. Spring Boot request flow
3. controller/service/repository/entity roles
4. JPA entity and repository basics
5. BCrypt password hashing
6. JWT login flow
7. Spring Security filter chain basics

## Do Not Research Yet

Avoid these in Week 1 unless blocked:

- RabbitMQ
- Kafka
- Resilience4j
- distributed transactions
- outbox pattern
- Redis pub/sub
- service discovery
- API gateway patterns
- advanced Spring Modulith events

## Week 1 Knowledge Check

By the end of the week, you should be able to answer:

- What does Maven do in this project?
- What does Spring Boot give you that plain Java does not?
- Why is `auth` a module instead of spreading auth files across global folders?
- What is the difference between controller, service, and repository?
- What happens during signup from HTTP request to database write?
- What happens during login from password check to token generation?
- Why is a modular monolith still a monolith?
- Which parts of the auth slice can you now write manually without AI help?
