# API Documentation

Base URL: `http://localhost:8080` direct, or `http://localhost:4200` through the dev/prod frontend proxy.

Full endpoint reference: `http://localhost:8080/swagger-ui.html`

Machine-readable OpenAPI spec: `http://localhost:8080/v3/api-docs`

The per-endpoint contract is generated from the Spring controllers and DTOs. Keep endpoint details in code annotations, not in this file.

## Authentication

Authentication is stateless and cookie-based. A successful login sets an `httpOnly`, `SameSite=Strict` `auth_token` cookie containing a signed JWT. Browsers send it automatically on later requests; there is no `Authorization` header flow.

Two seeded roles exist:

- `OWNER`: private library account.
- `DEMO`: public, passwordless demo account for recruiters.

Protected API areas require a valid auth cookie:

- `/api/auth/me`
- `/api/manga/**`
- `/api/push/subscribe`
- `/api/push/unsubscribe`

Public API areas include:

- `/api/auth/csrf`
- `/api/auth/login`, `/api/auth/logout`, `/api/auth/demo-login`
- `/api/push/public-key`
- `/actuator/health`, `/actuator/info`
- `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`

## CSRF

CSRF protection uses an `HttpOnly` `XSRF-TOKEN` cookie plus an explicit same-origin bootstrap endpoint.

Before any state-changing request (`POST`, `PATCH`, `DELETE`), call `GET /api/auth/csrf` and send the returned token in the `X-XSRF-TOKEN` header. This includes login, logout, and demo-login.

Swagger UI can browse the contract without authentication. For "Try it out" on state-changing operations, first fetch the CSRF token and include `X-XSRF-TOKEN`; otherwise the backend returns `403`.

## Demo Account

`POST /api/auth/demo-login` logs into the public `DEMO` account without a password and sets the same `auth_token` cookie as normal login.

The demo library is reset nightly by `DemoResetJob`, so it is safe for public experimentation.

## Rate Limit

Adding manga is rate-limited per authenticated user. Defaults are controlled by:

- `app.ratelimit.add-manga.max` (default `20`)
- `app.ratelimit.add-manga.window-seconds` (default `60`)

When the limit is exceeded, the API returns `429` with the shared error envelope.

## Error Format

All JSON error responses use the same envelope:

```json
{ "error": "Human-readable error message" }
```

Common statuses:

- `400`: bad request, validation failure, or unsupported source.
- `401`: missing or invalid auth cookie.
- `403`: missing or invalid CSRF token on state-changing requests.
- `404`: resource not found.
- `409`: duplicate manga URL.
- `422`: scraper could not extract manga data.
- `429`: rate limit exceeded.
