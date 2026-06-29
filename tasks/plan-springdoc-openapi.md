# Plan: Replace hand-written api.md with springdoc-openapi (Swagger)

**Owner:** GPT/OpenCode implements. Opus planned + will review.
**Branch:** new branch off `main` (e.g. `feat/openapi-swagger`). Do NOT stack on PR #17 (`chore/externalize-agent-workflows`) — keep API-docs change separate from repo cleanup. Open its own PR.
**Goal:** Auto-generate the API contract from controllers/DTOs so it never drifts. Keep `docs/api.md` as a thin cross-cutting doc (auth/CSRF/rate-limit/error/demo) that links Swagger UI; remove the per-endpoint tables that springdoc now owns.

## Context / current state

- Spring Boot 3.4.4, Java 21 (`backend/build.gradle`). No springdoc/swagger present today.
- Controllers (3): `AuthController`, `MangaController`, `PushController` under `com.mangatracker.backend.controller`.
- Error envelope produced by `controller/GlobalExceptionHandler.java`. Exceptions: `DuplicateMangaException`, `MangaNotFoundException`, `RateLimitExceededException`, `ScrapingException`, `UnsupportedSourceException`, `WebPushException`.
- Security: `security/SecurityConfig.java`. Cookie-CSRF via `CookieCsrfTokenRepository` + `CsrfCookieFilter`. Current `permitAll`: `/api/auth/{login,logout,demo-login,csrf}`, `/actuator/{health,info}`, `/api/push/public-key`. Everything else authenticated.
- `docs/api.md` (386 lines) is referenced by `CLAUDE.md`, `AGENTS.md`, `README.md` — **file must keep existing** (shrunk, not deleted), or those links break.

## Non-negotiables (from CLAUDE.md)

- `jakarta.*`, never `javax.*`.
- Do not edit/delete Flyway migrations (this change adds none — no schema impact).
- Angular DI via `inject()` (no frontend code in this task).
- Branch + PR, no direct push to `main`.

## Step 1 — Add dependency

`backend/build.gradle`, in `dependencies {}`:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

(2.6.x is the Boot 3.2–3.4 compatible line. Confirm resolution with `./gradlew dependencies` if needed.)

## Step 2 — Permit Swagger paths in SecurityConfig

Add to the `permitAll` chain in `SecurityConfig` (the static UI + JSON spec are GET, so CSRF does not block them):

- `/swagger-ui/**`
- `/swagger-ui.html`
- `/v3/api-docs/**`

Keep them above the catch-all `authenticated()` matcher. Do **not** add them to any CSRF-ignoring set (not needed for GET docs).

## Step 3 — springdoc config

Add to `backend/src/main/resources/application.yml` (or `.properties` — match existing format):

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

Add an `@OpenAPIDefinition` + cookie-auth `@SecurityScheme` bean/config class (e.g. `config/OpenApiConfig.java`) describing:
- API title / version / short description.
- Security scheme: cookie-based JWT (`type = APIKEY`, `in = COOKIE`, name = the auth cookie). Mark protected operations so Swagger "Authorize" reflects the cookie.
- Note in description: CSRF — state-changing requests need the `X-XSRF-TOKEN` header (from `GET /api/auth/csrf`); "Try it out" on POST/PATCH/DELETE will 403 without it.

## Step 4 — Annotate controllers + DTOs

For `AuthController`, `MangaController`, `PushController`:
- `@Tag` per controller (Auth / Manga / Push).
- `@Operation(summary=...)` on each endpoint.
- `@ApiResponse` for the non-200 cases each endpoint actually returns (404 manga-not-found, 409 duplicate, 429 rate-limit, 401 unauth, 400 validation).
- Reference the shared error schema (Step 5) on error responses.

On DTOs (Manga response, PushSubscription, login request, etc.): `@Schema(description=...)` on non-obvious fields; mark required fields. Prefer annotations over prose.

## Step 5 — Error envelope schema

Expose the `GlobalExceptionHandler` error body as a documented `@Schema` (e.g. an `ErrorResponse` record/DTO if one is not already returned) so springdoc renders the real error shape. Wire `@ApiResponse(content=@Content(schema=@Schema(implementation=ErrorResponse.class)))` on error responses.

## Step 6 — Shrink docs/api.md (~386 → ~60 lines)

**Keep** (springdoc expresses these poorly):
- `## Authentication` — cookie-JWT + CSRF flow, login sequence.
- Demo-login / demo-account reset behavior.
- Rate-limit rule on add-manga.
- `## Error Format` — envelope semantics + link to schema.
- New top section: "Full endpoint reference: run backend, open `http://localhost:8080/swagger-ui.html` (spec: `/v3/api-docs`)."

**Remove**: every `### METHOD /path` per-endpoint block and the `## Schemas` dumps (now auto-generated).

Update one-line pointers in `CLAUDE.md` / `AGENTS.md` / `README.md` if they imply api.md is the full endpoint list (point to Swagger for endpoints, api.md for concepts). Do not remove the links.

## Step 7 — Production exposure decision (call out, don't silently choose)

Swagger UI + `/v3/api-docs` will be publicly reachable once permitted. Options — pick and note in PR:
- (a) Leave enabled everywhere (fine for this hobby/demo app).
- (b) Gate behind a Spring profile (`springdoc.api-docs.enabled` / `swagger-ui.enabled` false in `prod`).

Default recommendation: (a) enabled, since the app already exposes a public demo account — but flag it for human sign-off.

## Verification (run before claiming done)

```bash
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport
# manual: ./gradlew bootRun, then GET http://localhost:8080/v3/api-docs returns JSON
#         and /swagger-ui.html loads; confirm all 16 endpoints + error schema present
```

- Confirm `SecurityConfigTest` still passes (new permit paths may need a case).
- Confirm no `javax.*` imports introduced.
- Diff api.md: only concept prose remains; no per-endpoint blocks.

## Out of scope

- No Flyway/schema changes.
- No frontend changes.
- No new endpoints — documentation only.

## Acceptance criteria

1. `/swagger-ui.html` lists all 16 endpoints grouped by tag, with auth scheme + error schema.
2. `docs/api.md` reduced to cross-cutting concepts + Swagger link; still referenced, not deleted.
3. Backend tests + spotless green; no `javax.*`.
4. Production-exposure choice documented in PR description.
