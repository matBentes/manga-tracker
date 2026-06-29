# GPT-5.5 Implementation Summary: add-springdoc-openapi

## Summary

Implemented Spring-generated OpenAPI/Swagger documentation for the backend and reshaped the hand-written API docs into cross-cutting concepts only.

## Implemented

- Added `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` to `backend/build.gradle`.
- Added springdoc paths to `backend/src/main/resources/application.properties`.
- Added `OpenApiConfig` with OpenAPI metadata, CSRF caveat, and `auth_token` cookie API-key security scheme.
- Added explicit Swagger/OpenAPI `permitAll` matchers in `SecurityConfig` while preserving the existing explicit protected matchers and final `anyRequest().permitAll()` catch-all.
- Added a 401 authentication entry point so unauthenticated protected endpoints match the documented/API contract status.
- Added `ErrorResponse` schema matching `{"error":"<message>"}` without changing `GlobalExceptionHandler` serialization.
- Annotated `AuthController`, `MangaController`, `PushController`, nested request records, and `Manga` response fields with OpenAPI metadata.
- Shrunk `docs/api.md` to auth, CSRF, demo-login, rate-limit, error-format concepts, plus Swagger/OpenAPI links.
- Updated `README.md`, `AGENTS.md`, and `CLAUDE.md` pointers to send endpoint-contract readers to Swagger and concept readers to `docs/api.md`.

## Plan Adjustment During Apply

- The confirmed plan originally pinned `springdoc-openapi-starter-webmvc-ui:2.6.0`.
- Runtime smoke testing showed `2.6.0` is incompatible with Spring Boot 3.4.4/Spring Framework 6.2: `/v3/api-docs` failed with `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`.
- Updated the implementation and OpenSpec artifacts to `2.8.9`, which resolved the error and passed the OpenAPI smoke test.

## Verification

- `./gradlew spotlessApply` passed.
- `./gradlew dependencies --configuration runtimeClasspath` passed and resolved `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9`.
- Targeted red/green tests added and passing:
  - `OpenApiDocumentationTest`
  - `SecurityConfigTest`
- Non-repository backend tests passed:
  - `./gradlew test --tests "com.mangatracker.backend.controller.*" --tests "com.mangatracker.backend.security.*" --tests "com.mangatracker.backend.service.*" --tests "com.mangatracker.backend.scraper.*" --tests "com.mangatracker.backend.job.*"`
- `bootRun` smoke test passed on port `18080` against local Postgres:
  - `GET /v3/api-docs` returned valid OpenAPI JSON.
  - `HEAD /swagger-ui.html` returned `302` to `/swagger-ui/index.html`.
  - Generated spec has 16 operations grouped by tag: Auth 5, Manga 8, Push 3.
  - Generated spec includes `ErrorResponse` and `auth_token` cookie security scheme.
- `docs/api.md` contains no `### METHOD /path` endpoint blocks and no `## Schemas` dump.
- No new `javax.*` imports were introduced. A pre-existing `javax.crypto.SecretKey` import remains in `JwtService.java` and is unrelated to this change.

## Blocked / Incomplete

- `./gradlew test jacocoTestReport` is not green in this environment because three Testcontainers repository tests fail before execution with `Could not find a valid Docker environment` / `NpipeSocketClientProviderStrategy` errors:
  - `MangaRepositoryTest`
  - `NotificationLogRepositoryTest`
  - `PushSubscriptionRepositoryTest`
- Task 6.2 remains unchecked because no PR was created in this phase. Suggested PR note: Swagger/OpenAPI is enabled everywhere for the demo app because the generated spec contains no secrets and the app already exposes a public demo account; profile-gating can be added later if reviewers request it.

## Next Command

`/dual-opus review-impl add-springdoc-openapi`
