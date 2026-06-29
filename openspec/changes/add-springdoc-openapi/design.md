## Context

Spring Boot 3.4.4, Java 21 backend. Three controllers (`AuthController`, `MangaController`, `PushController`) under `com.mangatracker.backend.controller`; 16 endpoints total. Errors flow through `controller/GlobalExceptionHandler` (Duplicate/MangaNotFound/RateLimitExceeded/Scraping/UnsupportedSource/WebPush). Security in `security/SecurityConfig`: cookie-CSRF via `CookieCsrfTokenRepository` + `CsrfCookieFilter`; current `permitAll` = `/api/auth/{login,logout,demo-login,csrf}`, `/actuator/{health,info}`, `/api/push/public-key`; everything else `authenticated()`. `docs/api.md` (386 lines) is hand-written and referenced by `CLAUDE.md`, `AGENTS.md`, `README.md` — must stay present. No springdoc today.

Constraints (CLAUDE.md non-negotiables): `jakarta.*` only; no Flyway edits (none here); branch + PR, no direct push to `main`.

## Goals / Non-Goals

**Goals:**
- Per-endpoint contract generated from code, never hand-maintained.
- Interactive Swagger UI + raw `/v3/api-docs` JSON.
- Real error envelope and cookie-JWT auth scheme reflected in the spec.
- `api.md` reduced to cross-cutting concepts, links intact.

**Non-Goals:**
- No new/changed endpoints, request/response behavior, or runtime logic (documentation-only).
- No Flyway/schema changes. No frontend changes.
- No client SDK generation.

## Decisions

- **springdoc-openapi-starter-webmvc-ui 2.6.0** over manual OpenAPI YAML or Swagger annotations alone. Rationale: 2.6.x is the Boot 3.2–3.4 compatible line; the starter bundles UI + JSON and auto-introspects Spring MVC. Alternative (hand-written OpenAPI file) rejected — reintroduces the drift this change removes. Confirm resolution with `./gradlew dependencies` if needed.
- **Permit docs paths in SecurityConfig, not CSRF-ignore.** `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` are GET; add to `permitAll` above the `authenticated()` catch-all. Do NOT add to any CSRF-ignoring set — unnecessary for GET and would widen surface.
- **Cookie-JWT security scheme as APIKEY in COOKIE** via `@SecurityScheme` in a new `config/OpenApiConfig` with `@OpenAPIDefinition` (title/version/description). Rationale: auth is a JWT carried in a cookie, not a bearer header; APIKEY-in-COOKIE is the accurate OpenAPI representation and makes Swagger "Authorize" meaningful. CSRF caveat documented in the API description (state-changing calls need `X-XSRF-TOKEN` from `GET /api/auth/csrf`).
- **Named `ErrorResponse` schema** mirroring `GlobalExceptionHandler`'s body, referenced via `@ApiResponse(content=@Content(schema=@Schema(implementation=ErrorResponse.class)))`. If the handler does not already serialize a dedicated type, introduce a record matching the emitted JSON so the documented shape equals the real shape. Annotate only the non-200 cases each endpoint actually returns (404/409/429/401/400) — avoid blanket noise.
- **`api.md` shrink, not delete.** Keep Authentication, demo-login/reset, rate-limit, Error Format, and a Swagger pointer; drop `### METHOD /path` blocks and `## Schemas`. Preserves the three inbound links.

## Risks / Trade-offs

- [Public docs exposure once permitted] → Decision deferred to PR sign-off. Default (a) enabled everywhere (hobby/demo app already exposes a public demo account); option (b) gate via `springdoc.api-docs.enabled` / `swagger-ui.enabled=false` in a `prod` profile. Call out explicitly, do not silently choose.
- [`SecurityConfigTest` regressions from new permit matchers] → Add/adjust test cases asserting docs paths are permitted and a sample protected path still requires auth.
- [Dependency/version incompatibility with Boot 3.4.4] → Pin 2.6.0; verify with `./gradlew dependencies`; smoke-test `/v3/api-docs` returns JSON.
- [Documented error shape diverging from actual handler output] → Derive `ErrorResponse` from the real serialized body; verify a forced error (e.g. 404) matches the schema.

## Migration Plan

1. Add dependency → SecurityConfig permits → springdoc config + `OpenApiConfig`.
2. Annotate controllers/DTOs + `ErrorResponse`.
3. Shrink `api.md`; update pointer prose in `CLAUDE.md`/`AGENTS.md`/`README.md`.
4. Verify: `./gradlew spotlessApply`, `./gradlew test jacocoTestReport`; `bootRun` then `GET /v3/api-docs` JSON + `/swagger-ui.html` loads with all 16 endpoints + error schema; no `javax.*`.
5. Rollback: revert the branch — no schema/state migration, so rollback is code-only and safe.

## Open Questions

- Production exposure: enabled everywhere (a) or profile-gated (b)? Needs human sign-off in PR (default recommendation: a).
- Does `GlobalExceptionHandler` already return a dedicated DTO, or is `ErrorResponse` newly introduced to match its body? Confirm during implementation.
