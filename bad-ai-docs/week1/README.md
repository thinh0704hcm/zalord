# Adaptive Auth Plan

This folder turns Stage 1 into an adaptive plan for building the modular monolith first.

Baseline assumption:

- you already understand programming concepts
- you can read Java syntax
- your current weakness is manual Java/Spring implementation fluency because AI has been filling in too much of the code for you

Focus:

- rebuild manual coding fluency with small backend tasks
- learn only the minimum Spring Boot concepts needed to stay unstuck
- Make the backend start reliably
- Build only the simplest possible auth vertical slice
- Avoid premature work on rooms, messaging, presence, microservices, Redis, outbox, or resilience patterns

Files in this folder:

- `adaptive-plan.md` — overall progress states and stop conditions
- `day-by-day-checklist.md` — concrete checklists you can use session by session
- `learning-and-research.md` — what to learn and what to research before coding
- `template-structures.md` — suggested package structure and file templates to follow manually

How to use this folder:

- do not force yourself through a fixed schedule
- identify your current progress state
- stop when that state's stop condition is met
- ask for the next session plan when you feel ready

Current success criteria:

- `backend` starts
- database connection works
- `auth` package structure is simple and understandable
- signup endpoint exists
- login endpoint exists, or is one small step away
- JWT and `/me` are optional stretch goals
- you understand why the code is organized by module, not by global layer folders
- you manually wrote the core auth classes yourself instead of relying on generated code
