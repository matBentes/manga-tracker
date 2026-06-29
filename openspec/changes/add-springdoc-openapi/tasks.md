## 1. Dependency & config

- [x] 1.1 Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` to `backend/build.gradle` `dependencies {}`; confirm resolution with `./gradlew dependencies`
- [x] 1.2 Add springdoc paths to `backend/src/main/resources/application.properties` (`springdoc.swagger-ui.path=/swagger-ui.html`, `springdoc.api-docs.path=/v3/api-docs`) — properties format, NOT yaml (no `application.yml` exists)
- [x] 1.3 Create `config/OpenApiConfig.java` with `@OpenAPIDefinition` (title/version/short description) and a cookie-JWT `@SecurityScheme` (type APIKEY, in COOKIE, name = auth cookie); include CSRF caveat in the description

## 2. Security wiring

- [x] 2.1 Add explicit `permitAll` matchers for `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` in `SecurityConfig`, placed before the explicit `authenticated()` matchers. Note: docs paths already fall under the final `anyRequest().permitAll()`, so this is clarity/future-proofing, not a behavior change. Do NOT add to any CSRF-ignoring set; do NOT alter the existing `authenticated()` matchers or the catch-all.
- [x] 2.2 Add/adjust `SecurityConfigTest` cases: docs paths reachable unauthenticated; the explicitly-protected paths (`/api/auth/me`, `/api/manga/**`, `/api/push/**`) still return 401 unauthenticated

## 3. Error schema

- [x] 3.1 Introduce `record ErrorResponse(String error)` matching `GlobalExceptionHandler`'s emitted body `{"error": "<message>"}` (handler returns `Map.of("error", ...)` today). Schema-only is fine; optionally switch the handler to return the record (byte-identical, zero runtime change)
- [x] 3.2 Reference it via `@ApiResponse(content=@Content(schema=@Schema(implementation=ErrorResponse.class)))` on error responses

## 4. Annotate controllers & DTOs

- [x] 4.1 `@Tag` per controller (Auth / Manga / Push) and `@Operation(summary=...)` on each of the 16 endpoints
- [x] 4.2 `@ApiResponse` for the real non-200 cases per endpoint (404 not-found, 409 duplicate, 429 rate-limit, **422 scraping failure on `POST /api/manga`**, 401 unauth, 400 validation/unsupported-source), referencing the error schema
- [x] 4.3 `@Schema(description=...)` on non-obvious DTO fields (manga response, push subscription, login request); mark required fields

## 5. Docs reshape

- [x] 5.1 Shrink `docs/api.md` to ~60 lines: keep Authentication (cookie-JWT + CSRF), demo-login/reset, add-manga rate-limit, Error Format; add "Full endpoint reference → http://localhost:8080/swagger-ui.html (spec: /v3/api-docs)"
- [x] 5.2 Remove every `### METHOD /path` block and the `## Schemas` dumps
- [x] 5.3 Update pointer prose in `CLAUDE.md`, `AGENTS.md`, `README.md` to send readers to Swagger for endpoints / `api.md` for concepts; keep the links

## 6. Production exposure (decided: enabled everywhere)

- [x] 6.1 Implement option (a) enabled everywhere — no profile gating, no `springdoc.*.enabled=false`. Decision already made pre-apply; do NOT re-open it during implementation
- [ ] 6.2 Record the (a) decision + rationale (demo app, no secrets in spec) in the PR description for human sign-off

## 7. Verification

- [x] 7.1 `cd backend && ./gradlew spotlessApply`
- [ ] 7.2 `./gradlew test jacocoTestReport` green; `SecurityConfigTest` passes
- [x] 7.3 `./gradlew bootRun`: `GET /v3/api-docs` returns JSON and `/swagger-ui.html` loads; confirm all 16 endpoints grouped by tag + error schema present
- [x] 7.4 Confirm no `javax.*` imports introduced; diff `api.md` shows only concept prose (no per-endpoint blocks)
