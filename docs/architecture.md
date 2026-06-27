# Architecture Overview

## System Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser (localhost:4200)                                       │
│  Angular SPA                                                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  nginx (Docker, port 4200 → 80)                                 │
│  • Serves Angular static files                                  │
│  • Proxies /api/* → backend:8080                                │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP /api/*
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot (port 8080)                                        │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐ │
│  │  Controller  │  │   Service        │  │  Repository (JPA)  │ │
│  │  MangaCtrl   │→ │  MangaSvc        │→ │  MangaRepository   │ │
│  │  PushCtrl    │  │  PushSubsSvc     │  │  PushSubsRepository│ │
│  │              │  │  Notif/PushSvc   │  │  NotifLogRepository│ │
│  └──────────────┘  └──────┬───────────┘  └──────────┬─────────┘ │
│                           │                          │          │
│  ┌──────────────┐         │                          ▼          │
│  │  Scraper     │         │                 PostgreSQL 16       │
│  │  MangaScraper│◄────────┘                                     │
│  │  SakuraScrpr │                                               │
│  └──────────────┘                                               │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  ScrapingJob (@Scheduled daily 08:00)  →  ScraperRegistry │  │
│  │                                        →  NotificationSvc │  │
│  │                                        →  PushNotifSvc    │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │ SQL                                  │ Web Push (VAPID)
         ▼                                      ▼
┌─────────────────┐                  ┌──────────────────────┐
│  PostgreSQL 16  │                  │  Browser push service │
│  (port 5432)    │                  │  (FCM / etc.) → PWA   │
└─────────────────┘                  └──────────────────────┘
```

## Backend Layers

| Layer        | Package                                      | Responsibility                                              |
|--------------|----------------------------------------------|-------------------------------------------------------------|
| Controller   | `controller`                                 | HTTP request/response mapping; delegates to services        |
| Service      | `service`                                    | Business logic, validation, transaction management          |
| Repository   | `repository`                                 | JPA data access; extends `JpaRepository`                    |
| Scraper      | `scraper`                                    | Web scraping; pluggable via `MangaScraper` interface        |
| Job          | `job`                                        | Daily scheduled scrape (08:00); demo library reset; calls scraper + push notify |
| Security     | `security`                                   | Cookie-JWT auth filter, JWT signing, user seeding, current-user resolution, add-manga rate limiting |
| Exception    | `exception`                                  | Domain exception classes mapped to HTTP status codes        |

### Exception → HTTP Mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes all error responses:

| Exception                  | HTTP Status |
|----------------------------|-------------|
| `MangaNotFoundException`   | 404         |
| `DuplicateMangaException`  | 409         |
| `UnsupportedSourceException` | 400       |
| `ScrapingException`        | 422         |
| `IllegalArgumentException` | 400         |
| `RateLimitExceededException` | 429       |

---

## Security & Authentication

Authentication is **stateless, cookie-based JWT**. No server-side sessions are kept; every
request is authorized from the signed token in the auth cookie.

| Component               | Package    | Responsibility                                                        |
|-------------------------|------------|----------------------------------------------------------------------|
| `SecurityConfig`        | `security` | Spring Security filter chain: which paths are public vs. authenticated, CSRF, CORS |
| `JwtCookieAuthFilter`   | `security` | Reads the JWT cookie on each request, validates it, sets the authentication |
| `JwtService`            | `security` | Signs and verifies the JWT (HMAC); encodes `username` + `role`       |
| `UserSeeder`            | `security` | On startup creates the OWNER and DEMO accounts from env passwords (BCrypt-hashed) |
| `CurrentUser` / `AuthenticatedUser` | `security` | Resolves the authenticated user (and its `id`) for owner-scoped queries |
| `AddMangaRateLimiter`   | `security` | Per-user sliding-window limit on add-manga (default 20 / 60s) → 429  |

Flow:

```
POST /api/auth/login { username, password }
       │  constant-time BCrypt check (decoy hash if user unknown → no username enumeration)
       ▼
Set-Cookie: <jwt>  (httpOnly, SameSite=Strict, Secure in prod, path=/)
       │
       ▼  every later request carries the cookie automatically
JwtCookieAuthFilter validates JWT → SecurityContext authentication
       │
       ▼
/api/manga/** resolved against CurrentUser.id  →  owner-scoped reads & writes
```

- **Roles:** `OWNER` (private library) and `DEMO` (public, passwordless; nightly reset by `DemoResetJob`).
- **Owner scoping:** `manga.owner_id` FKs to `app_user`; queries filter by the authenticated
  user's id, so one user never sees another's manga (cross-owner access returns `404`).
- **CSRF:** double-submit cookie (`XSRF-TOKEN` / `X-XSRF-TOKEN`), with login/logout/demo-login exempt.
- **CORS:** disabled (same-origin) unless `app.auth.allowed-origins` is set.
- **Public endpoints:** `/api/auth/login|logout|demo-login`, `/api/push/**`, `/actuator/health|info`.

---

## Data Flows

### Add Manga Flow

```
POST /api/manga { sourceUrl }
       │
       ▼
MangaController.addManga()
       │
       ▼
MangaService.addManga(sourceUrl)
  1. ScraperRegistry.resolve(url)   → selects matching MangaScraper
  2. MangaScraper.scrape(url)       → returns ScrapedManga(title, latestChapter, coverImageUrl)
  3. Check for existing sourceUrl   → throws DuplicateMangaException if found
  4. MangaRepository.save()         → persists with currentChapter=0
       │
       ▼
201 Created { manga JSON }
```

### Daily Scraping + Notification Flow

```
@Scheduled (cron "0 0 8 * * *", zone America/Sao_Paulo)
       │
       ▼
ScrapingJob.runDailyCheck()
  For each manga in the DB:
    1. ScraperRegistry.resolve(manga.sourceUrl)
    2. MangaScraper.scrape(manga.sourceUrl)   → ScrapedManga
    3. Refresh manga.coverImageUrl if scraped
    4. If scrapedLatest > manga.latestChapter:
         • Update manga.latestChapter + manga.updatedAt
         • NotificationService.notify(manga, newLatest)
              └─ If manga.notificationsEnabled AND not already sent
                 (notification_log unique constraint):
                   • Save NotificationLog entry
                   • PushNotificationService.send(...)  → Web Push to all subscriptions
    5. Update manga.lastCheckedAt
    On scraping error: log and continue to next manga
```

When a notification is tapped it opens `/open/{id}`, which marks the manga read (sets
`currentChapter = latestChapter`) and redirects to the manga's source page.

---

## Database Schema

### `app_user`

| Column          | Type         | Constraints                          |
|-----------------|--------------|--------------------------------------|
| `id`            | UUID         | PK                                   |
| `username`      | VARCHAR(255) | NOT NULL, UNIQUE                     |
| `password_hash` | VARCHAR(255) | NOT NULL (BCrypt)                    |
| `role`          | VARCHAR(32)  | NOT NULL (`OWNER` / `DEMO`)          |
| `created_at`    | TIMESTAMP    | NOT NULL, DEFAULT now()              |

Seeded at startup by `UserSeeder` from `OWNER_PASSWORD` / `DEMO_PASSWORD` (added in migration V10).

### `manga`

| Column                | Type          | Constraints                              |
|-----------------------|---------------|------------------------------------------|
| `id`                  | UUID          | PK, default `gen_random_uuid()`          |
| `title`               | VARCHAR(255)  | NOT NULL                                 |
| `source_url`          | TEXT          | NOT NULL, UNIQUE                         |
| `current_chapter`     | INTEGER       | NOT NULL, DEFAULT 0                      |
| `latest_chapter`      | INTEGER       | NOT NULL, DEFAULT 0                      |
| `cover_image_url`     | TEXT          | NULLABLE                                 |
| `notifications_enabled`| BOOLEAN      | NOT NULL, DEFAULT TRUE                   |
| `owner_id`            | UUID          | NULLABLE, FK → `app_user(id)`, indexed (V10) |
| `last_checked_at`     | TIMESTAMP     | NULLABLE                                 |
| `created_at`          | TIMESTAMP     | NOT NULL, DEFAULT now()                  |
| `updated_at`          | TIMESTAMP     | NOT NULL, DEFAULT now()                  |

### `notification_log`

| Column           | Type      | Constraints                                          |
|------------------|-----------|------------------------------------------------------|
| `id`             | UUID      | PK, default `gen_random_uuid()`                      |
| `manga_id`       | UUID      | NOT NULL, FK → `manga(id)` ON DELETE CASCADE         |
| `chapter_number` | INTEGER   | NOT NULL                                             |
| `sent_at`        | TIMESTAMP | NOT NULL                                             |

Unique constraint on `(manga_id, chapter_number)` prevents duplicate notifications.

### `push_subscription`

| Column        | Type      | Constraints                     |
|---------------|-----------|---------------------------------|
| `id`          | UUID      | PK                              |
| `endpoint`    | TEXT      | NOT NULL, UNIQUE                |
| `p256dh`      | TEXT      | NOT NULL                        |
| `auth`        | TEXT      | NOT NULL                        |
| `created_at`  | TIMESTAMP | NOT NULL                        |

One row per subscribed browser. The `app_settings` table was removed (V8) along with the email
and poll-interval settings.

---

## Pluggable Scraper Design

```java
public interface MangaScraper {
    boolean supports(String url);
    ScrapedManga scrape(String url) throws ScrapingException;
}
```

- Each scraper is a Spring `@Component` implementing `MangaScraper`.
- Spring auto-wires all implementations into `ScraperRegistry` via `List<MangaScraper>`.
- `ScraperRegistry.resolve(url)` streams the list and returns the first scraper whose `supports(url)` returns `true`, or throws `UnsupportedSourceException`.
- To add support for a new site, implement `MangaScraper`, annotate it `@Component`, and it is automatically picked up — no changes to `ScraperRegistry` are needed.

Currently supported sources:

| Scraper                  | Domain              |
|--------------------------|---------------------|
| `SakuraMangasScraper`    | sakuramangas.org    |

---

## CI Pipeline

```
push / pull_request
        │
        ▼
┌───────────────────┐
│  Stage 1          │
│  Format & Lint    │  Spotless (Java) · Checkstyle · ESLint · Prettier · ng build
└────────┬──────────┘
         │ on success
         ▼
┌───────────────────┐
│  Stage 2          │
│  Tests            │  JUnit 5 · Testcontainers · SonarCloud · Playwright E2E
└────────┬──────────┘
         │ on success (parallel)
    ┌────┴─────────────────────┐
    ▼                          ▼
┌──────────────┐    ┌──────────────────────┐
│  Stage 3     │    │  Stage 4             │
│  AI Code     │    │  Build & Deploy      │  (main branch only)
│  Review      │    │  Docker → GHCR       │
└──────────────┘    └──────────────────────┘
```

- Stage 1 auto-commits Spotless formatting fixes with `[skip ci]`.
- Stage 2 runs a real PostgreSQL 16 service container for Testcontainers-based repository tests.
- Stage 3 runs only on pull requests (posts review comments via Claude).
- Stage 4 runs only on pushes to `main`; pushes images to GitHub Container Registry (`ghcr.io`).
