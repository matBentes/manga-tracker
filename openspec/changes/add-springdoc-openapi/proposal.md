## Why

`docs/api.md` (386 lines) is hand-maintained and drifts from the real controllers/DTOs every time an endpoint changes. Generating the endpoint contract from code (springdoc-openapi / Swagger) keeps it always-accurate and gives an interactive "Try it out" UI, while `api.md` shrinks to the cross-cutting concepts springdoc expresses poorly (auth/CSRF/rate-limit/error/demo).

## What Changes

- Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` to `backend/build.gradle`.
- Add explicit `permitAll` matchers for Swagger paths (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`) in `SecurityConfig` for clarity — they already fall under the existing final `anyRequest().permitAll()`, so the authorization model (who can access what) is unchanged. Existing `authenticated()` matchers and the catch-all stay as-is. **Denial status hardened:** an `AuthenticationEntryPoint` now returns 401 (was 403) for unauthenticated protected requests; CSRF failures still return 403. Human-accepted; aligns responses with the OpenAPI contract and fixes the frontend `/login` redirect.
- Add springdoc config (paths) to `application.properties` (no `application.yml` exists), plus an `OpenApiConfig` with `@OpenAPIDefinition` and a cookie-JWT `@SecurityScheme` (APIKEY in COOKIE).
- Annotate the 3 controllers (`AuthController`, `MangaController`, `PushController`) with `@Tag`, `@Operation`, `@ApiResponse`; annotate DTOs with `@Schema`.
- Expose the `GlobalExceptionHandler` error envelope as a documented `ErrorResponse` schema referenced by error `@ApiResponse`s.
- Shrink `docs/api.md` (~386 → ~60 lines): keep auth/CSRF, demo-login, rate-limit, error-format concepts + Swagger link; **remove** per-endpoint `### METHOD /path` blocks and `## Schemas` dumps.
- Update pointer prose in `CLAUDE.md` / `AGENTS.md` / `README.md` to send readers to Swagger for endpoints, `api.md` for concepts (links kept, not removed).
- Production exposure decided pre-apply: **(a) enabled everywhere** (demo app, no secrets in the generated spec). The PR note records the rationale for human sign-off, not a deferred decision.

## Capabilities

### New Capabilities
- `api-documentation`: how the API contract is published — auto-generated OpenAPI/Swagger for the per-endpoint reference, with `docs/api.md` reduced to cross-cutting concepts and a Swagger pointer.

### Modified Capabilities
<!-- None. No existing OpenSpec specs in openspec/specs/. Endpoints and the authorization model are unchanged; the only runtime change is the denial status for unauthenticated protected requests (403 -> 401, human-accepted), captured in the api-documentation spec. -->

## Impact

- **Code**: `backend/build.gradle`, `security/SecurityConfig.java`, `application.properties`, new `config/OpenApiConfig.java`, new `ErrorResponse` record, annotations on 3 controllers + DTOs.
- **Docs**: `docs/api.md` shrunk; pointer lines in `CLAUDE.md`, `AGENTS.md`, `README.md`.
- **Tests**: `SecurityConfigTest` may need cases for new permit paths.
- **No** Flyway/schema changes, **no** new endpoints, **no** frontend code changes (frontend behavior improves: unauthenticated protected requests now hit the interceptor's 401 `/login` redirect instead of the 403 CSRF-retry path).
- **Runtime surface**: Swagger UI + `/v3/api-docs` become reachable (exposure decided: enabled everywhere).
