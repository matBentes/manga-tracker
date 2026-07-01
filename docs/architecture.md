# Architecture Overview

## System Diagram

```text
┌─────────────────────────────────────────────────────────────────┐
│  Browser (localhost:4200)                                       │
│  Angular SPA                                                    │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  nginx (Docker, port 4200 → 8080)                               │
│  • Serves Angular static files                                  │
│  • Proxies /api/* and /actuator/health → backend:8080           │
└──────────────────────┬──────────────────────────────────────────┘
                       │ HTTP proxied paths
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot (port 8080)                                        │
│  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────┐ │
│  │  Controller  │  │   Service        │  │  Repository (JPA)  │ │
│  │  Manga/Auth  │→ │  MangaSvc        │→ │  MangaRepository   │ │
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

- `controller`: HTTP request/response mapping; delegates to services.
- `service`: business logic, validation, and transactions.
- `repository`: JPA data access.
- `scraper`: pluggable manga source scraping.
- `job`: scheduled scraping and demo library reset.
- `security`: cookie-JWT auth, CSRF, current-user resolution, and add-manga rate limiting.
- `exception`: domain exceptions mapped to HTTP status codes.

### Exception → HTTP Mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes application exceptions:

- `MangaNotFoundException`: `404`
- `DuplicateMangaException`: `409`
- `UnsupportedSourceException`: `400`
- `ScrapingException`: `422`
- `IllegalArgumentException`: `400`
- `RateLimitExceededException`: `429`

---

## Security & Authentication

Authentication is **stateless, cookie-based JWT**. No server-side sessions are kept; every
request is authorized from the signed token in the auth cookie.

- `SecurityConfig`: public/authenticated routes, CSRF, and optional CORS.
- `JwtCookieAuthFilter`: validates the auth cookie and sets authentication.
- `JwtService`: signs and verifies JWTs.
- `UserSeeder`: creates `OWNER` and `DEMO` accounts from env passwords.
- `CurrentUser` / `AuthenticatedUser`: resolves owner-scoped queries.
- `AddMangaRateLimiter`: per-user add-manga sliding-window limit.

Flow:

```text
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
- **CSRF:** `HttpOnly` CSRF cookie (`XSRF-TOKEN`) plus same-origin token bootstrap
  (`GET /api/auth/csrf`); all state-changing API requests, including login/logout/demo-login,
  must send `X-XSRF-TOKEN`.
- **CORS:** disabled (same-origin) unless `app.auth.allowed-origins` is set.
- **Public endpoints:** `/api/auth/csrf`, `/api/auth/login|logout|demo-login`,
  `/api/push/public-key`, `/actuator/health|info`, Swagger UI, and OpenAPI JSON.

---

## Data Flows

### Add Manga Flow

```text
POST /api/manga { sourceUrl }
       │
       ▼
MangaController.addManga()
       │
       ▼
MangaService.addManga(sourceUrl)
  1. ScraperRegistry.resolve(url)   → selects matching MangaScraper
  2. MangaScraper.scrape(url)       → returns ScrapedManga(title, latestChapter, coverImageUrl)
  3. Check owner-scoped sourceUrl   → throws DuplicateMangaException if found
  4. MangaRepository.save()         → persists with currentChapter=0
       │
       ▼
201 Created { manga JSON }
```

### Daily Scraping + Notification Flow

```text
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
                   • PushNotificationService.send(...)  → Web Push to owner's subscriptions
    5. Update manga.lastCheckedAt
    On scraping error: log and continue to next manga
```

When a notification is tapped it opens `/open/{id}`, which marks the manga read (sets
`currentChapter = latestChapter`) and redirects to the manga's source page.

---

## Database Schema

The canonical schema is the Flyway migrations plus JPA entities. Keep this section at the
relationship level so it does not drift from migrations.

- `app_user`: seeded `OWNER` and `DEMO` accounts with BCrypt password hashes.
- `manga`: owner-scoped reading list entry with source URL, chapter state, cover URL,
  `latest_chapter_at`, notification flag, and scrape timestamps. Source URLs are unique per owner.
- `notification_log`: one row per `(manga, chapter)` push attempt to prevent duplicate alerts.
- `push_subscription`: one row per subscribed browser, scoped to the authenticated owner.

The old `app_settings` table was removed with the email and poll-interval settings.

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

Current jobs and required check names live in `.github/workflows/` and
`docs/github-operations.md`. In short: CI formats/lints, runs backend tests with coverage,
runs frontend unit and Playwright E2E checks, runs real-backend integration E2E checks, and
builds/pushes images on `main`.
