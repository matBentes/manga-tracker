# api-documentation Specification

## Purpose
TBD - created by archiving change add-springdoc-openapi. Update Purpose after archive.
## Requirements
### Requirement: Auto-generated OpenAPI specification
The backend SHALL publish a machine-readable OpenAPI 3 specification generated from the live controllers and DTOs, so the per-endpoint contract cannot drift from the code.

#### Scenario: OpenAPI JSON available
- **WHEN** a client issues `GET /v3/api-docs` against a running backend
- **THEN** the server returns HTTP 200 with a valid OpenAPI 3 JSON document describing all 16 endpoints across the Auth, Manga, and Push controllers

#### Scenario: Spec reflects current code
- **WHEN** a controller endpoint or DTO field is added, removed, or changed
- **THEN** the generated `/v3/api-docs` document reflects that change without manual edits to any docs file

### Requirement: Interactive Swagger UI
The backend SHALL serve an interactive Swagger UI for browsing and trying the API, reachable without authentication.

#### Scenario: Swagger UI loads
- **WHEN** a user opens `http://localhost:8080/swagger-ui.html` on a running backend
- **THEN** the UI loads and lists all 16 endpoints grouped by tag (Auth, Manga, Push)

#### Scenario: Docs paths bypass authentication
- **WHEN** an unauthenticated client requests `/swagger-ui/**`, `/swagger-ui.html`, or `/v3/api-docs/**`
- **THEN** the request is permitted (not redirected to login or rejected with 401), and the existing explicitly-protected paths (`/api/auth/me`, `/api/manga/**`, `/api/push/**`) still require authentication

#### Scenario: Documentation change does not alter authorization model
- **WHEN** the SecurityConfig authorization rules are reviewed after the change
- **THEN** the final `anyRequest().permitAll()` and the explicit `authenticated()` matchers for `/api/auth/me`, `/api/manga/**`, `/api/push/**` are unchanged in behavior, and docs paths are reachable (whether via the existing catch-all or explicit permit matchers added for clarity)

#### Scenario: Unauthenticated protected requests return 401
- **WHEN** an unauthenticated client requests a protected path (`/api/auth/me`, `/api/manga/**`, `/api/push/**`) with a valid CSRF token but no auth cookie
- **THEN** the server returns HTTP 401 Unauthorized via the configured `AuthenticationEntryPoint` (intentionally hardened from the prior 403, accepted by the human reviewer), while requests missing a CSRF token still return 403, matching the status codes the OpenAPI document advertises

### Requirement: Documented authentication scheme
The OpenAPI document SHALL declare the cookie-based JWT authentication scheme and mark protected operations, so Swagger's "Authorize" control reflects how the API is actually secured.

#### Scenario: Cookie security scheme present
- **WHEN** the OpenAPI document is generated
- **THEN** it defines a security scheme of type APIKEY located in COOKIE named after the auth cookie, and protected operations reference it

#### Scenario: CSRF requirement documented
- **WHEN** a reader views the API description or a state-changing operation in Swagger UI
- **THEN** the docs state that POST/PATCH/DELETE require the `X-XSRF-TOKEN` header obtained from `GET /api/auth/csrf`, and that "Try it out" returns 403 without it

### Requirement: Documented error envelope schema
The OpenAPI document SHALL expose the shared error envelope returned by `GlobalExceptionHandler` as a named schema, referenced by the error responses each endpoint can return. The schema SHALL match the real serialized body, a single `error` string field (`{"error": "<message>"}`).

#### Scenario: Error schema referenced on failure responses
- **WHEN** an endpoint can return 400, 401, 404, 409, 422, or 429
- **THEN** the corresponding `@ApiResponse` in the OpenAPI document references the shared `ErrorResponse` schema describing the real error shape

#### Scenario: Scraping failure documented as 422
- **WHEN** `POST /api/manga` can fail with a `ScrapingException`
- **THEN** the operation documents a `422 Unprocessable Entity` response referencing the `ErrorResponse` schema, matching `GlobalExceptionHandler`'s mapping

### Requirement: docs/api.md scoped to cross-cutting concepts
`docs/api.md` SHALL remain present but be reduced to cross-cutting concepts plus a pointer to Swagger, and SHALL NOT duplicate the per-endpoint reference that OpenAPI now owns.

#### Scenario: File retained and reshaped
- **WHEN** the change is complete
- **THEN** `docs/api.md` still exists and contains the Authentication (cookie-JWT + CSRF) flow, demo-login/reset behavior, add-manga rate-limit rule, error-format semantics, and a "Full endpoint reference" pointer to `http://localhost:8080/swagger-ui.html`

#### Scenario: Per-endpoint blocks removed
- **WHEN** `docs/api.md` is reviewed after the change
- **THEN** it contains no `### METHOD /path` per-endpoint blocks and no `## Schemas` dumps

#### Scenario: Inbound links preserved
- **WHEN** `CLAUDE.md`, `AGENTS.md`, and `README.md` are checked
- **THEN** their links to `docs/api.md` still resolve, with prose pointing readers to Swagger for endpoints and `api.md` for concepts

