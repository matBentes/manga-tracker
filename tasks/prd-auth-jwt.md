# PRD: JWT Authentication & Per-User Data Isolation

## Introduction

manga-tracker currently has no authentication: any caller can read, add, and delete manga. This blocks a public deploy. This feature adds JWT-based login so the app can run on the public internet — serving the owner's private manga list and a sandboxed demo account for recruiters — without exposing open write access.

Deploy targets: AWS EC2 (now, for hands-on experience during the credit window) and Oracle Cloud always-free ARM VM (permanent home). Same Docker Compose stack on both.

## Goals

- Require authentication for all manga read/write endpoints.
- Scope every manga to an owning user; users only see and mutate their own manga.
- Ship a seeded owner account and a seeded demo account — no public registration.
- Demo account is fully interactive but reset to a clean seeded state nightly.
- Close the pending SSRF in the scraper via a host allowlist.
- Enforce HTTPS in production (also required for Web Push).

## Decisions (locked)

- **Registration:** No public signup. Users seeded via Flyway/seed only (owner + demo).
- **Token storage:** JWT in `httpOnly` + `Secure` + `SameSite` cookie. Not readable by JS.
- **Token lifetime:** Single access token (no refresh token), expiry 7 days.
- **Demo hygiene:** Demo can write freely; nightly cron wipes and reseeds demo data.

## User Stories

### US-001: Users table + seed migration
**Description:** As the system, I need a users table so credentials and ownership can persist.

**Acceptance Criteria:**
- [ ] Flyway migration creates `app_user` table: `id` (UUID v7 PK), `username` (unique, not null), `password_hash` (not null), `role` (`OWNER` | `DEMO`, not null), `created_at`.
- [ ] Migration adds nullable `owner_id` FK column to `manga` table referencing `app_user(id)`.
- [ ] New migration only — no edits to existing migrations.
- [ ] `jakarta.persistence.*` imports only.
- [ ] `./gradlew test` passes; schema validates on startup (`ddl-auto=validate`).

### US-002: Password hashing + user persistence
**Description:** As the system, I need to store passwords securely so credentials aren't recoverable.

**Acceptance Criteria:**
- [ ] `AppUser` JPA entity + Spring Data repository (`findByUsername`).
- [ ] Passwords hashed with BCrypt (Spring Security `PasswordEncoder`).
- [ ] Owner + demo users seeded on startup if absent; passwords sourced from env vars (`OWNER_PASSWORD`, `DEMO_PASSWORD`), never hardcoded or committed.
- [ ] Unit test: seeded user has a BCrypt hash, not plaintext.

### US-003: Login endpoint issues JWT cookie
**Description:** As a user, I want to log in so I get an authenticated session.

**Acceptance Criteria:**
- [ ] `POST /api/auth/login` accepts `{username, password}`, verifies against BCrypt hash.
- [ ] On success: signs a JWT (subject=userId, claim=role, 7-day expiry) and sets it in an `httpOnly`, `Secure`, `SameSite=Strict` cookie. No token in response body.
- [ ] On failure: 401, no cookie, generic message (no user-enumeration leak).
- [ ] JWT signing secret from env (`JWT_SECRET`), min 256-bit, never committed.
- [ ] `POST /api/auth/logout` clears the cookie.
- [ ] `GET /api/auth/me` returns current `{username, role}` from the token, or 401.
- [ ] Test: valid creds → 200 + cookie; bad creds → 401; tampered token → 401.

### US-004: Secure all manga endpoints + ownership scoping
**Description:** As a user, I want my manga private so other users can't see or change them.

**Acceptance Criteria:**
- [ ] Spring Security filter validates the JWT cookie on every `/api/manga/**` request; unauthenticated → 401.
- [ ] CSRF protection enabled for state-changing requests (cookie-based auth needs it).
- [ ] List/get endpoints return only manga where `owner_id` = current user.
- [ ] Create assigns `owner_id` = current user.
- [ ] Update/delete reject (404, not 403 — no existence leak) manga owned by another user.
- [ ] Tests: cross-user read returns empty/404; cross-user delete denied; create stamps owner.

### US-005: Demo account write behavior + nightly reset
**Description:** As a recruiter, I want to try the app fully, on data that stays clean for the next visitor.

**Acceptance Criteria:**
- [ ] Demo user can add/delete manga (full interactive experience).
- [ ] Scheduled job (`@Scheduled`, daily, configurable cron) deletes all demo-owned manga and reseeds a fixed demo set.
- [ ] Reseed is idempotent and only touches demo-owned rows; owner data untouched.
- [ ] Test: after reset, demo owns exactly the seeded set.

### US-006: Scraper host allowlist (SSRF fix)
**Description:** As the system, I must only scrape approved hosts so a malicious URL can't pivot the server into internal targets.

**Acceptance Criteria:**
- [ ] `SakuraMangasScraper.supports()` parses the URL and checks scheme is `http`/`https` and host equals (or is a subdomain of) an allowlisted host — replacing the substring `contains("sakuramangas.org")` check.
- [ ] Add-manga rejects URLs whose host isn't allowlisted (400).
- [ ] Tests: `https://sakuramangas.org/...` allowed; `https://evil.com/?x=sakuramangas.org` rejected; `file://`, `http://169.254.169.254/...` rejected.

### US-007: Angular login page + auth guard + interceptor
**Description:** As a user, I want a login screen and to be kept out of the app until I authenticate.

**Acceptance Criteria:**
- [ ] Login route/component: username + password, calls `/api/auth/login` with `withCredentials: true`.
- [ ] Auth guard protects dashboard routes; unauthenticated → redirect to `/login`.
- [ ] HTTP interceptor sets `withCredentials: true` so the cookie rides along; on 401 redirects to `/login`. Uses `inject()`, not constructor injection.
- [ ] Header shows logged-in username + logout button; logout calls `/api/auth/logout`.
- [ ] CSRF token wired for state-changing calls.
- [ ] `npm run lint` passes (`prefer-inject`).
- [ ] Verify in browser using Playwright MCP: login → dashboard, logout → login, deep link while logged out → login.

### US-008: Add-manga rate limit
**Description:** As the owner, I want add-manga throttled so a logged-in demo user can't hammer the scraper.

**Acceptance Criteria:**
- [ ] Per-user rate limit on `POST /api/manga` (e.g. N/min); over limit → 429.
- [ ] Test: N+1 rapid adds → 429 on the last.

## Functional Requirements

- FR-1: All `/api/manga/**` endpoints require a valid JWT cookie.
- FR-2: Manga are owned; queries and mutations are scoped to the authenticated user.
- FR-3: Login issues a signed JWT in an httpOnly/Secure/SameSite cookie; 7-day expiry; single token, no refresh.
- FR-4: No registration endpoint; owner + demo seeded from env-supplied passwords.
- FR-5: Demo data reset and reseeded nightly by a scheduled job.
- FR-6: Scraper only accepts allowlisted hosts (scheme + host parsed, not substring).
- FR-7: Production enforces HTTPS; cookies marked Secure.
- FR-8: `POST /api/manga` rate-limited per user.

## Non-Goals

- Public self-service registration / signup form.
- Refresh tokens, "remember me", token rotation, revocation lists.
- Password reset / email verification flows.
- OAuth / social login.
- Role-based admin UI beyond OWNER vs DEMO.
- Multi-tenant org/team features.

## Technical Considerations

- **Stack:** Spring Boot 3 + Spring Security, Angular 18, PostgreSQL, Flyway, Docker Compose.
- **Secrets:** `JWT_SECRET`, `OWNER_PASSWORD`, `DEMO_PASSWORD` via env only — never committed (consistent with existing VAPID handling).
- **HTTPS:** terminated at nginx + certbot in front of the stack (per the Oracle/AWS deploy blog notes); `Secure` cookie requires it in prod, localhost exempt.
- **CSRF:** httpOnly-cookie auth requires CSRF protection on mutating endpoints — Spring Security `CookieCsrfTokenRepository` + Angular `withCredentials`.
- **TDD:** one test → make it pass → repeat. Run `./gradlew spotlessApply` before committing Java.
- **Migrations:** new Flyway file only; never edit existing.

## Success Metrics

- Unauthenticated request to any manga endpoint → 401 (100%).
- Cross-user data access impossible (verified by tests).
- Demo dashboard identical at the start of each day regardless of prior tampering.
- SSRF allowlist tests pass; non-allowlisted hosts rejected.
- App reachable over HTTPS on the deployed host; owner can log in and Web Push works on phone.

## Open Questions

- Deploy DB: Postgres in a container (cheapest, matches dev) vs RDS (more resume signal, ~$12/mo). Lean container first, document RDS as a later upgrade.
- Rate-limit implementation: in-memory bucket (single instance, fine now) vs Redis (needed only if scaled horizontally). Lean in-memory.
- Demo reset time: pick a low-traffic hour (e.g. 04:00 server TZ) — confirm TZ during deploy.

## Handoff

On approval, derive a task plan from `tasks/plan-template.md` referencing this PRD and a story slice (suggested first slice: US-001 → US-004, the auth core). Put verification commands + the two-agent Review Gate in that plan.
