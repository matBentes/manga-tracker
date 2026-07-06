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

## MangaDex-Backed Endpoints

Manga metadata comes from the [MangaDex API](https://api.mangadex.org/docs/), called
server-side — the frontend never talks to MangaDex directly.

- `GET /api/manga/search?q=` — proxies a MangaDex title search; returns MangaDex id, title,
  cover URL, and synopsis for each result.
- `POST /api/manga` — adds a manga by `mangaDexId` (required), with optional `sourceUrl`
  (an optional "read it here" link — no longer required or unique on its own), starting
  `currentChapter`, and starting `readingStatus`. Duplicate detection is per-user on
  `(ownerId, mangaDexId)`; adding the same MangaDex title twice returns `409`.
- `PATCH /api/manga/{id}` — updates `currentChapter`, `latestChapter`, `readingStatus`, and/or
  `notificationsEnabled`.

Chapter numbers are stored as integers. MangaDex chapter values are strings and may be decimal
or non-numeric; only a leading positive integer is parsed, so half/decimal chapters are not
tracked.

Full request/response contracts are in Swagger, not duplicated here.

## Rate Limit

Adding manga and searching MangaDex are both rate-limited per authenticated user, and the login
endpoints are rate-limited per client IP (login also per username) before any credential check.
Defaults are controlled by:

- `app.ratelimit.add-manga.max` (default `20`)
- `app.ratelimit.add-manga.window-seconds` (default `60`)
- `app.ratelimit.search-manga.max` (default `30`)
- `app.ratelimit.search-manga.window-seconds` (default `60`)
- `app.ratelimit.login.max` (default `10`)
- `app.ratelimit.login.window-seconds` (default `900`)
- `app.ratelimit.demo-login.max` (default `60`)
- `app.ratelimit.demo-login.window-seconds` (default `900`)

When a limit is exceeded, the API returns `429` with the shared error envelope.

## Error Format

Handled application exceptions use this JSON envelope:

```json
{ "error": "Human-readable error message" }
```

Common statuses:

- `400`: bad request, validation failure, or an invalid `sourceUrl` (must be an absolute
  http(s) URL, if provided).
- `401`: missing or invalid auth cookie.
- `403`: missing or invalid CSRF token on state-changing requests.
- `404`: resource not found.
- `409`: duplicate manga — the same `(ownerId, mangaDexId)` is already tracked.
- `429`: rate limit exceeded (add-manga or search).
- `502`: MangaDex upstream request failed.

Spring Security and framework-level errors may return an empty or default body.
