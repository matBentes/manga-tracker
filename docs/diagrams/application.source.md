# Application Architecture Source

Canonical source for the application architecture diagram.

## Scope

Local/dev and containerized application architecture, based on `docs/architecture.md`, `README.md`, and `docker-compose.yml`.

## Components

| Component | Technology | Responsibility |
|-----------|------------|----------------|
| Browser / PWA | Angular SPA + service worker | User interface, installed PWA behavior, push subscription, push tap handling |
| Frontend container | nginx | Serves Angular static assets and proxies `/api/*` to the backend |
| Backend API | Spring Boot 3.4 / Java 21 | HTTP API, auth, CSRF, manga tracking, push subscription, MangaDex integration |
| Controllers | Spring MVC | Request mapping for manga, auth, and push endpoints |
| Services | Spring services | Business logic, validation, notification, push delivery, user/account behavior |
| Security | Spring Security | Cookie JWT auth, CSRF, CORS, current-user resolution, add-manga and search rate limiting |
| MangaDexClient | Spring service, REST client | Search, manga metadata, and English chapter feed lookups against `api.mangadex.org` |
| Scheduled jobs | Spring `@Scheduled` | Daily best-effort English chapter checks (MangaDex) and demo account reset |
| Repositories | Spring Data JPA | Persistence access for manga, users, notifications, and push subscriptions |
| Database | PostgreSQL 16 + Flyway | Stores app users, manga, notification logs, and push subscriptions |
| Browser push service | Web Push provider | Delivers VAPID-signed notifications to subscribed browsers/devices |

## Main Connections

| From | To | Protocol / Mechanism | Notes |
|------|----|----------------------|-------|
| Browser / PWA | Frontend nginx | HTTP in local/dev, HTTPS in production | Loads SPA assets |
| Browser / PWA | Frontend nginx `/api/*` | HTTP requests with cookies and CSRF headers | Same-origin API access through proxy |
| Frontend nginx | Backend API | HTTP proxy | Proxies `/api/*` |
| Backend API | PostgreSQL | JDBC / SQL | Flyway migrates on startup; Hibernate validates schema |
| Backend API | MangaDex (`api.mangadex.org`) | HTTPS REST | Search titles, fetch metadata/cover art, poll the English chapter feed |
| Backend API | Browser push service | Web Push / VAPID | Sends notifications to saved subscriptions |
| Browser push service | Browser / PWA | Push delivery | User taps notification and opens `/open/{id}` |

## Important Flows

### Search And Add Manga

1. Browser queries `GET /api/manga/search?q=` and picks a MangaDex result.
2. Browser posts `{ mangaDexId, sourceUrl?, currentChapter?, readingStatus? }` to `/api/manga`.
3. Backend validates auth, CSRF, and the add-manga rate limit.
4. `MangaService` checks the `(ownerId, mangaDexId)` dedup index, then calls `MangaDexClient`
   for title/cover metadata and the latest English chapter.
5. Backend saves the manga (optional `sourceUrl`, defaulted `reading_status`).

### Daily MangaDex Check And Notify

1. Scheduled job runs daily at 08:00 America/Sao_Paulo.
2. Backend polls `MangaDexClient` for the latest English chapter of each distinct tracked
   `mangaDexId`.
3. If a newer chapter is found, backend updates the manga(s) tracking that title.
4. Notification log prevents duplicate notifications for the same manga/chapter.
5. Backend sends Web Push to subscribed devices. Failures for one title never stop the run.

### Authentication Boundary

- Auth is stateless and cookie-based with JWT.
- CSRF uses an `HttpOnly` `XSRF-TOKEN` cookie plus `X-XSRF-TOKEN` on state-changing requests.
- Manga and push subscriptions are scoped by authenticated owner.
