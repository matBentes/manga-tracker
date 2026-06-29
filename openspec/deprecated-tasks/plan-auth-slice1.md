# Task Plan: Auth Slice 1 (US-001 → US-004)

## Source
- PRD: `tasks/prd-auth-jwt.md`
- Slice: US-001 (users table + Flyway V10) → US-002 (BCrypt + env-seeded users) → US-003 (login → JWT cookie, /me, /logout) → US-004 (secure all manga endpoints + per-user ownership scoping + CSRF).

## Goal
Real JWT auth core: one OWNER account + one DEMO account (seeded from env), login issues an httpOnly/Secure/SameSite JWT cookie, every `/api/manga/**` endpoint requires a valid cookie, and manga are scoped per user. No public signup.

## Context (verified facts)
- Backend package: `com.mangaTracker.backend`. Spring Boot 3.4.4, Java 21, Gradle.
- **spring-security is NOT yet a dependency.** Add `org.springframework.boot:spring-boot-starter-security` and a JWT lib (`io.jsonwebtoken:jjwt-api`/`jjwt-impl`/`jjwt-jackson:0.12.x`) to `backend/build.gradle`.
- Flyway migrations exist V1–V9 (`backend/src/main/resources/db/migration/`). **Next file = `V10__...sql`.** Naming style: `V{n}__snake_case.sql`. NEVER edit/delete existing migrations.
- Entity ID style (match existing `Manga.java`): `@Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.TIME)` (UUID v7), `jakarta.persistence.*` only.
- `ddl-auto=validate` — schema must match migrations exactly or startup fails.
- Existing manga endpoints in `controller/MangaController.java`; service `service/MangaService.java`; repo `repository/MangaRepository.java`; entity `model/Manga.java`. Errors via `controller/GlobalExceptionHandler.java`.
- Tests use Testcontainers (Docker must run).

## Scope
**In:**
- `V10` migration: `app_user` table (`id` UUID PK, `username` unique not null, `password_hash` not null, `role` not null, `created_at` not null) + nullable `owner_id` FK on `manga` referencing `app_user(id)`.
- `model/AppUser.java` entity + `Role` enum (OWNER, DEMO) + `repository/AppUserRepository.java` (`findByUsername`).
- BCrypt `PasswordEncoder` bean; startup seeder that creates owner + demo if absent, passwords from env `OWNER_PASSWORD`/`DEMO_PASSWORD` (skip seeding / log a warning if env unset — never hardcode).
- `auth/AuthController.java`: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`.
- JWT service: sign (subject=userId, claim role, 7-day expiry) + verify; secret from env `JWT_SECRET` (min 256-bit; fail fast if missing/short in prod).
- Spring Security config: stateless, JWT-cookie auth filter on `/api/manga/**` and `/api/auth/me`; `/api/auth/login` + `/api/auth/logout` public; CSRF via `CookieCsrfTokenRepository.withHttpOnlyFalse()` for state-changing requests.
- Add `owner_id` to `Manga` entity; scope `MangaService` list/get/update/delete/create to current user; cross-user update/delete → **404 (not 403)**.
- Tests (TDD, one at a time): seeded user has BCrypt hash; login valid→200+cookie, bad→401, tampered token→401; unauthenticated manga request→401; cross-user read empty / cross-user delete 404; create stamps owner_id.

**Out (later slices):** demo nightly reset (US-005), scraper SSRF allowlist (US-006), Angular login UI (US-007), rate limit (US-008). Do NOT build these now.

## Constraints & Decisions (non-negotiable)
- `jakarta.persistence.*` only — never `javax`.
- New Flyway file `V10` only; never touch V1–V9.
- Cookie: `httpOnly` + `Secure` + `SameSite=Strict`. No token in response body.
- `Secure` must be conditional so localhost/dev (http) still works; prod requires HTTPS. Make it config-driven (e.g. `app.auth.cookie-secure`, default true, dev profile false).
- Login failure: 401, generic message, no user enumeration, no cookie.
- Secrets env-only (`JWT_SECRET`, `OWNER_PASSWORD`, `DEMO_PASSWORD`) — never committed, never logged.
- Existing manga rows have NULL owner_id (column nullable) — that's fine; don't backfill in this slice. New owner-scoped queries simply won't return them. (Owner can re-add, or a later migration can assign.) Document this in handoff notes.
- TDD: write one failing test, make it pass, repeat. No horizontal slicing.
- Constructor injection IS the Spring convention here (this is backend Java — the `inject()` rule is Angular-only).

## Verification
Run from `backend/`:
- `./gradlew spotlessApply` (must run before done — CI rejects unformatted)
- `./gradlew checkstyleMain checkstyleTest` (maxWarnings=0)
- `./gradlew test jacocoTestReport` (Testcontainers; coverage min 70%)
- App boots with `ddl-auto=validate` (schema matches V10).

Manual sanity (curl, optional): login with seeded creds → 200 + Set-Cookie; `GET /api/manga` without cookie → 401; with cookie → 200.

Max fix attempts: 3. If a gate still fails after 3, stop and report the failure verbatim.

## Review Gate
1. Implementer self-reviews against acceptance criteria above.
2. Parent (Claude) independently reviews the full diff against PRD US-001–US-004 + security constraints (cookie flags, 404-not-403, no enumeration, env-only secrets, no secret logging, CSRF wired, jakarta-only, V10-only).
3. Agree-pass → done. Agree-fail → fix. Disagree → stop and reconcile.

## Handoff Notes (fill in on completion)
- List every file created/modified.
- State how `Secure` cookie flag is toggled for dev vs prod.
- Note the NULL-owner_id existing-rows decision and any follow-up needed.
- Confirm which gates passed and paste any that failed.
