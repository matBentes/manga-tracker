## 1. Dependency & config

- [ ] 1.1 Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` to `backend/build.gradle` `dependencies {}`; confirm resolution with `./gradlew dependencies`
- [ ] 1.2 Add springdoc paths to `application.yml` (`springdoc.swagger-ui.path=/swagger-ui.html`, `springdoc.api-docs.path=/v3/api-docs`), matching existing file format
- [ ] 1.3 Create `config/OpenApiConfig.java` with `@OpenAPIDefinition` (title/version/short description) and a cookie-JWT `@SecurityScheme` (type APIKEY, in COOKIE, name = auth cookie); include CSRF caveat in the description

## 2. Security wiring

- [ ] 2.1 Permit `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` in `SecurityConfig` above the `authenticated()` catch-all; do NOT add to any CSRF-ignoring set
- [ ] 2.2 Add/adjust `SecurityConfigTest` cases: docs paths permitted; a sample protected endpoint still requires auth

## 3. Error schema

- [ ] 3.1 Provide a documented `ErrorResponse` schema matching `GlobalExceptionHandler`'s emitted body (introduce a record if no dedicated DTO is serialized today)
- [ ] 3.2 Reference it via `@ApiResponse(content=@Content(schema=@Schema(implementation=ErrorResponse.class)))` on error responses

## 4. Annotate controllers & DTOs

- [ ] 4.1 `@Tag` per controller (Auth / Manga / Push) and `@Operation(summary=...)` on each of the 16 endpoints
- [ ] 4.2 `@ApiResponse` for the real non-200 cases per endpoint (404 not-found, 409 duplicate, 429 rate-limit, 401 unauth, 400 validation), referencing the error schema
- [ ] 4.3 `@Schema(description=...)` on non-obvious DTO fields (manga response, push subscription, login request); mark required fields

## 5. Docs reshape

- [ ] 5.1 Shrink `docs/api.md` to ~60 lines: keep Authentication (cookie-JWT + CSRF), demo-login/reset, add-manga rate-limit, Error Format; add "Full endpoint reference → http://localhost:8080/swagger-ui.html (spec: /v3/api-docs)"
- [ ] 5.2 Remove every `### METHOD /path` block and the `## Schemas` dumps
- [ ] 5.3 Update pointer prose in `CLAUDE.md`, `AGENTS.md`, `README.md` to send readers to Swagger for endpoints / `api.md` for concepts; keep the links

## 6. Production exposure decision

- [ ] 6.1 Choose enabled-everywhere (a) or profile-gated (b); if (b), set `springdoc.api-docs.enabled`/`swagger-ui.enabled=false` in `prod` profile
- [ ] 6.2 Document the chosen option in the PR description for human sign-off

## 7. Verification

- [ ] 7.1 `cd backend && ./gradlew spotlessApply`
- [ ] 7.2 `./gradlew test jacocoTestReport` green; `SecurityConfigTest` passes
- [ ] 7.3 `./gradlew bootRun`: `GET /v3/api-docs` returns JSON and `/swagger-ui.html` loads; confirm all 16 endpoints grouped by tag + error schema present
- [ ] 7.4 Confirm no `javax.*` imports introduced; diff `api.md` shows only concept prose (no per-endpoint blocks)
