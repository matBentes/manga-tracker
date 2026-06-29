# Opus Plan Confirmation: add-springdoc-openapi

## Verdict

confirmed — artifacts updated, ready for `/dual-gpt apply`.

## Accepted Findings (all 4 verified against real code)

### 1. Stale security model (GPT High) — ACCEPTED
- Verified `SecurityConfig.java`: NO `authenticated()` catch-all. Explicit `authenticated()` matchers for `/api/auth/me`, `/api/manga/**`, `/api/push/**`, then final `anyRequest().permitAll()`. Swagger paths already reachable under that catch-all.
- Fixed: `proposal.md`, `design.md`, `spec.md`, `tasks.md` now state docs paths are already permitted; new explicit `permitAll` matchers are clarity/future-proofing only, NOT a behavior change. The `authenticated()` matchers and catch-all stay unchanged. Added spec scenario "Documentation change does not alter authorization model" + task 2.2 locking protected paths with 401 tests.

### 2. Missing 422 scraping error (GPT High) — ACCEPTED
- Verified `GlobalExceptionHandler.java`: `ScrapingException` → `HttpStatus.UNPROCESSABLE_ENTITY` (422) on `POST /api/manga`.
- Fixed: error envelope requirement + scenario list now `400, 401, 404, 409, 422, 429`; added "Scraping failure documented as 422" scenario; task 4.2 adds explicit 422 case; design error set updated.

### 3. Wrong config file (GPT Medium) — ACCEPTED
- Verified: backend uses `application.properties`; no `application.yml` exists.
- Fixed: proposal/design/tasks now target `application.properties` (properties format, not yaml).

### 4. Exposure decision deferred too late (GPT Medium) — ACCEPTED
- Fixed: decided pre-apply = **(a) enabled everywhere** (demo app, public demo account, no secrets in generated spec). Recorded in design Risks + tasks §6; PR note becomes human sign-off evidence, not a deferred decision. Implementer must NOT re-open it.

## Rejected Findings

None. All 4 findings verified valid against source.

## Open-Question Resolutions

- Catch-all behavior → **unchanged**. No hardening; `anyRequest().permitAll()` stays. Any tightening is a separate security change, out of scope.
- `ErrorResponse` → newly introduced `record ErrorResponse(String error)` matching `{"error": "<message>"}`. Schema-only by default; optional byte-identical handler switch allowed.
- Production exposure → (a) enabled everywhere (see finding 4).
- `@Schema(requiredMode = REQUIRED)` on package-private nested request records → left to implementer judgment (apply where it improves the generated contract).

## Risks Carried Into Apply

- New permit matchers must not widen access → task 2.2 asserts protected paths still 401.
- Documented error shape must match real handler output → derive `ErrorResponse` from actual serialized body; verify a forced 404.
- Boot 3.4.4 ↔ springdoc 2.6.0 compatibility → pin 2.6.0, verify `./gradlew dependencies` + smoke-test `/v3/api-docs`.

## Next Command

`/dual-gpt apply add-springdoc-openapi`
