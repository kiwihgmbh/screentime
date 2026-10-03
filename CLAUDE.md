# CLAUDE.md

Instructions for Claude Code working in this repository.
Read `docs/SPEC.md` before writing code. It holds the full specification.

## What this is

A screen time account for a family. A child books their own screen time,
parents keep the account and compare the log with what the devices report.
The app does not monitor devices. Enforcement stays with the built in
limits of iOS, Android, Windows and the consoles.

## Stack

- Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Flyway
- Database: PostgreSQL 16
- Frontend: Angular 18+, standalone components, signals, Angular Material
- Containers: one Dockerfile per part, docker compose for the full stack

Layout: `/backend`, `/frontend`, `/deploy`, `/docs`.

## Commands

    cd backend  && ./mvnw verify          # build and test the backend
    cd backend  && ./mvnw spring-boot:run # run against a local postgres
    cd frontend && npm ci && npm test     # frontend tests
    cd frontend && npm start              # dev server on :4200
    docker compose up --build             # full stack

## Hard rules

This repository is public. Other families may use it.

1. Never commit secrets. No passwords, tokens, keys, connection strings
   or certificates, not even as defaults or examples that look real.
   Every secret comes from an environment variable. `.env.example`
   carries placeholders only.
2. Never commit personal data. No real names, no real host names, no real
   e-mail addresses, no screenshots with real data. Use `child`, `parent`,
   `screentime.example.com`.
3. No seeded user accounts in Flyway migrations. The first parent account
   is created at startup from `APP_ADMIN_USER` and `APP_ADMIN_PASSWORD`.
   The application refuses to start if they are missing.
4. All timestamps come from the server. A date or time sent by the client
   is ignored, never trusted. Time zone is Europe/Zurich, stored as UTC.
5. A child can only book time for the current day, and only before the
   evening cut off. Any other date returns 403. Parents can book and
   correct any day.
6. Every change to a session writes an audit log entry.

## Working style

- Business rules live in a plain service class with no Spring annotations,
  so they can be tested without a context.
- Write the test before the rule when the rule involves dates, week
  boundaries or budgets. Those are where this project will break.
- Stop after each step and show what you built before moving on.
- Settings are rows in the `settings` table, never constants in code.
