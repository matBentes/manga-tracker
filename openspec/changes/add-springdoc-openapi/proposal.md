## Why

`docs/api.md` (386 lines) is hand-maintained and drifts from the real controllers/DTOs every time an endpoint changes. Generating the endpoint contract from code (springdoc-openapi / Swagger) keeps it always-accurate and gives an interactive "Try it out" UI, while `api.md` shrinks to the cross-cutting concepts springdoc expresses poorly (auth/CSRF/rate-limit/error/demo).

## What Changes

- Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0` to `backend/build.gradle`.
- Permit Swagger paths (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`) in `SecurityConfig` above the `authenticated()` catch-all (GET, so CSRF unaffected).
- Add springdoc config (paths) to `application.yml`, plus an `OpenApiConfig` with `@OpenAPIDefinition` and a cookie-JWT `@SecurityScheme` (APIKEY in COOKIE).
- Annotate the 3 controllers (`AuthController`, `MangaController`, `PushController`) with `@Tag`, `@Operation`, `@ApiResponse`; annotate DTOs with `@Schema`.
- Expose the `GlobalExceptionHandler` error envelope as a documented `ErrorResponse` schema referenced by error `@ApiResponse`s.
- Shrink `docs/api.md` (~386 → ~60 lines): keep auth/CSRF, demo-login, rate-limit, error-format concepts + Swagger link; **remove** per-endpoint `### METHOD /path` blocks and `## Schemas` dumps.
- Update pointer prose in `CLAUDE.md` / `AGENTS.md` / `README.md` to send readers to Swagger for endpoints, `api.md` for concepts (links kept, not removed).
- Decide + document production exposure of Swagger (enabled everywhere vs. profile-gated) in the PR.

## Capabilities

### New Capabilities
- `api-documentation`: how the API contract is published — auto-generated OpenAPI/Swagger for the per-endpoint reference, with `docs/api.md` reduced to cross-cutting concepts and a Swagger pointer.

### Modified Capabilities
<!-- None. No existing OpenSpec specs in openspec/specs/; runtime API behavior and endpoints are unchanged (documentation-only). -->

## Impact

- **Code**: `backend/build.gradle`, `security/SecurityConfig.java`, `application.yml`, new `config/OpenApiConfig.java`, new/exposed `ErrorResponse` DTO, annotations on 3 controllers + DTOs.
- **Docs**: `docs/api.md` shrunk; pointer lines in `CLAUDE.md`, `AGENTS.md`, `README.md`.
- **Tests**: `SecurityConfigTest` may need cases for new permit paths.
- **No** Flyway/schema changes, **no** frontend changes, **no** new endpoints.
- **Runtime surface**: Swagger UI + `/v3/api-docs` become reachable (exposure decision required).
