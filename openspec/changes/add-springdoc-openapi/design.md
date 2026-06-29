## Context

Spring Boot 3.4.4, Java 21 backend. Three controllers (`AuthController`, `MangaController`, `PushController`) under `com.mangatracker.backend.controller`; 16 endpoints total. Errors flow through `controller/GlobalExceptionHandler`, which serializes `Map.of("error", ex.getMessage())` — a single `error` string field — with status mapping: MangaNotFound→404, Duplicate→409, RateLimitExceeded→429, **Scraping→422**, UnsupportedSource→400, IllegalArgument→400.

Security in `security/SecurityConfig`: cookie-CSRF via `CookieCsrfTokenRepository` + `CsrfCookieFilter`. Authorization rules: `permitAll` for `/api/auth/{login,logout,demo-login,csrf}`, `/actuator/{health,info}`, `/api/push/public-key`; `authenticated()` for the **explicit** matchers `/api/auth/me`, `/api/manga/**`, `/api/push/**`; then a final **`anyRequest().permitAll()`** catch-all (NOT authenticated). So Swagger paths are already reachable under the catch-all today — no hardening exists or is intended here.

Config lives in `backend/src/main/resources/application.properties` (no `application.yml`). `docs/api.md` (386 lines) is hand-written and referenced by `CLAUDE.md`, `AGENTS.md`, `README.md` — must stay present. No springdoc today.

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

- **springdoc-openapi-starter-webmvc-ui 2.8.9** over manual OpenAPI YAML or Swagger annotations alone. Rationale: 2.6.0 resolved but failed at runtime on Spring Boot 3.4.4/Spring Framework 6.2 with `NoSuchMethodError: ControllerAdviceBean.<init>(Object)` while generating `/v3/api-docs`; 2.8.9 resolves that compatibility issue and smoke-tests successfully. Alternative (hand-written OpenAPI file) rejected — reintroduces the drift this change removes. Confirm resolution with `./gradlew dependencies` if needed.
- **Docs paths already reachable; add explicit permit matchers for clarity only.** `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` are GET and already fall under the final `anyRequest().permitAll()`, so no behavior change is required to expose them. Add explicit `permitAll` matchers for them anyway (above the explicit `authenticated()` matchers) to document intent and survive any future tightening of the catch-all. Do NOT add to any CSRF-ignoring set, and do NOT change the explicit `authenticated()` matchers or the catch-all — this stays documentation-only. Lock the protected paths with tests so the new matchers can't accidentally widen access.
- **Cookie-JWT security scheme as APIKEY in COOKIE** via `@SecurityScheme` in a new `config/OpenApiConfig` with `@OpenAPIDefinition` (title/version/description). Rationale: auth is a JWT carried in a cookie, not a bearer header; APIKEY-in-COOKIE is the accurate OpenAPI representation and makes Swagger "Authorize" meaningful. CSRF caveat documented in the API description (state-changing calls need `X-XSRF-TOKEN` from `GET /api/auth/csrf`).
- **Named `ErrorResponse` schema** mirroring `GlobalExceptionHandler`'s body. The handler today returns `Map.of("error", ...)`, no dedicated type — introduce `record ErrorResponse(String error)` matching the emitted JSON exactly (`{"error": "<message>"}`) and reference it via `@ApiResponse(content=@Content(schema=@Schema(implementation=ErrorResponse.class)))`. Switching the handler to return the record is optional and byte-identical (zero runtime change); if not switched, the record exists purely as the documentation schema. Annotate only the non-200 cases each endpoint actually returns — **404/409/429/422/401/400** (422 = `ScrapingException` on `POST /api/manga`) — avoid blanket noise.
- **`api.md` shrink, not delete.** Keep Authentication, demo-login/reset, rate-limit, Error Format, and a Swagger pointer; drop `### METHOD /path` blocks and `## Schemas`. Preserves the three inbound links.

## Risks / Trade-offs

- [Public docs exposure once permitted] → **Decided: (a) enabled everywhere.** Rationale: hobby/demo app already exposes a public demo account, and the spec is generated from controllers that carry no secrets. The PR note records this for human sign-off rather than deferring the implementation choice. Option (b) — gate via `springdoc.api-docs.enabled`/`swagger-ui.enabled=false` in a `prod` profile — remains a one-line follow-up if a reviewer objects.
- [`SecurityConfigTest` regressions from new permit matchers] → Add/adjust test cases asserting docs paths are permitted and a sample protected path still requires auth.
- [Dependency/version incompatibility with Boot 3.4.4] → Pin 2.8.9; verify with `./gradlew dependencies`; smoke-test `/v3/api-docs` returns JSON.
- [Documented error shape diverging from actual handler output] → Derive `ErrorResponse` from the real serialized body; verify a forced error (e.g. 404) matches the schema.

## Migration Plan

1. Add dependency → SecurityConfig permits → springdoc config + `OpenApiConfig`.
2. Annotate controllers/DTOs + `ErrorResponse`.
3. Shrink `api.md`; update pointer prose in `CLAUDE.md`/`AGENTS.md`/`README.md`.
4. Verify: `./gradlew spotlessApply`, `./gradlew test jacocoTestReport`; `bootRun` then `GET /v3/api-docs` JSON + `/swagger-ui.html` loads with all 16 endpoints + error schema; no `javax.*`.
5. Rollback: revert the branch — no schema/state migration, so rollback is code-only and safe.

## Resolved (was Open Questions)

- **Production exposure** → (a) enabled everywhere. Decided pre-apply; PR note = sign-off evidence.
- **`ErrorResponse` source** → newly introduced `record ErrorResponse(String error)`; handler currently returns `Map.of("error", ...)`. Schema-only by default; optional byte-identical handler switch allowed.
- **Catch-all behavior** → unchanged. `anyRequest().permitAll()` stays; this change does not tighten it to `authenticated()`. Any such hardening is a separate security change, out of scope here.

## Open Questions

- `@Schema(requiredMode = REQUIRED)` on package-private request records nested in controllers: apply where it improves the generated contract, but not mandatory — leave to implementer judgment.
