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
│  │  MangaDex    │         │                 PostgreSQL 16       │
│  │  MangaDexClnt│◄────────┘                                     │
│  └──────────────┘                                               │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  MangaDexNotificationJob (@Scheduled daily 08:00)          │  │
│  │    → MangaDexClient (latest English chapter)               │  │
│  │    → NotificationSvc → PushNotifSvc                        │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │ SQL                                  │ Web Push (VAPID)
         ▼                                      ▼
┌─────────────────┐                  ┌──────────────────────┐
│  PostgreSQL 16  │                  │  Browser push service │
│  (port 5432)    │                  │  (FCM / etc.) → PWA   │
└─────────────────┘                  └──────────────────────┘
```

MangaDex (`api.mangadex.org`) is called directly by the backend for search, metadata, and the
English chapter feed — there is no site scraping and no headless browser in this architecture.

## Backend Layers

- `controller`: HTTP request/response mapping; delegates to services.
- `service`: business logic, validation, transactions, and the `MangaDexClient` (search, manga
  metadata, latest English chapter feed against `api.mangadex.org`).
- `repository`: JPA data access.
- `job`: scheduled MangaDex notification check and demo library reset.
- `security`: cookie-JWT auth, CSRF, current-user resolution, add-manga and search rate limiting.
- `exception`: domain exceptions mapped to HTTP status codes.

### Exception → HTTP Mapping

`GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes application exceptions:

- `MangaNotFoundException`: `404`
- `DuplicateMangaException`: `409`
- `MangaDexUpstreamException`: `502` (MangaDex request failed or returned an unexpected response)
- `IllegalArgumentException`: `400` (includes an invalid/malformed `sourceUrl`)
- `RateLimitExceededException`: `429` (add-manga and search-manga limits)

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

### Search + Add Manga Flow

```text
GET /api/manga/search?q={query}
       │
       ▼
MangaController.searchManga()
       │
       ▼
MangaService.searchManga(query)
  1. Validate + normalize query (non-blank, max length)
  2. SearchMangaRateLimiter.check(ownerId)   → 429 if exceeded
  3. MangaDexClient.search(query)            → GET api.mangadex.org/manga?title=...
       │
       ▼
200 OK [ { mangaDexId, title, coverImageUrl, synopsis }, ... ]
```

```text
POST /api/manga { mangaDexId, sourceUrl?, currentChapter?, readingStatus? }
       │
       ▼
MangaController.addManga()
       │
       ▼
MangaService.addManga(mangaDexId, sourceUrl, currentChapter, readingStatus)
  1. Require mangaDexId; validate optional sourceUrl (absolute http(s) URL or null)
  2. Check (owner_id, mangadex_id) dedup   → throws DuplicateMangaException if already tracked
  3. AddMangaRateLimiter.check(ownerId)    → 429 if exceeded
  4. MangaDexClient.getManga(mangaDexId)   → title, cover art
  5. MangaDexClient.latestEnglishChapter(mangaDexId)  → best-effort; missing/upstream failure
     does not block adding the manga
  6. MangaRepository.save()   → persists with readingStatus defaulting to READING
       │
       ▼
201 Created { manga JSON }
```

### Daily MangaDex Notification Flow

```text
@Scheduled (cron "0 0 8 * * *", zone America/Sao_Paulo)
       │
       ▼
MangaDexNotificationJob.runDailyCheck()
  Load all manga with mangadexId != null AND notificationsEnabled = true, grouped by mangadexId
  (one MangaDex feed call per distinct title, shared across owners tracking the same title):
    1. MangaDexClient.latestEnglishChapter(mangadexId)
         └─ on failure: log, stamp lastCheckedAt, continue to the next title
    2. For each manga tracking that title:
         • Update manga.lastCheckedAt
         • If fetchedChapter > manga.latestChapter: update latestChapter + latestChapterAt
         • If fetchedChapter > manga.currentChapter: NotificationService.notify(manga, fetchedChapter)
              └─ If manga.notificationsEnabled AND not already sent
                 (notification_log unique constraint):
                   • Save NotificationLog entry
                   • PushNotificationService.send(...)  → Web Push to owner's subscriptions
    Per-item try/catch; a failure for one manga or title never stops the run.
```

Notifications are **best-effort and English-only**: they are driven by MangaDex's English
chapter feed, and chapter numbers that aren't a leading positive integer (decimal, "Extra",
null, etc.) are skipped and logged rather than tracked.

When a notification is tapped it opens `/open/{id}`, which marks the manga read (sets
`currentChapter = latestChapter`) and redirects to the manga's `sourceUrl` if one is set; since
`sourceUrl` is optional, a manga without a read-here link stays on the app instead of redirecting.

---

## Database Schema

The canonical schema is the Flyway migrations plus JPA entities. Keep this section at the
relationship level so it does not drift from migrations.

- `app_user`: seeded `OWNER` and `DEMO` accounts with BCrypt password hashes.
- `manga`: owner-scoped reading list entry with `reading_status`, nullable `mangadex_id`
  (MangaDex UUID), nullable `source_url` (optional "read it here" link, no longer unique per
  owner on its own), chapter state (`current_chapter`/`latest_chapter`, integer-only),
  `latest_chapter_at`, cover URL, notification flag, and `last_checked_at`. Duplicate detection
  is a partial unique index on `(owner_id, mangadex_id) WHERE mangadex_id IS NOT NULL` — manual
  entries without a `mangadex_id` are not dedup'd.
- `notification_log`: one row per `(manga, chapter)` push attempt to prevent duplicate alerts.
- `push_subscription`: one row per subscribed browser, scoped to the authenticated owner.

The old `app_settings` table was removed with the email and poll-interval settings. `V13` added
`reading_status`/`mangadex_id`, dropped the old owner-scoped unique index on `source_url` alone
(from `V12`), made `source_url` nullable, and added the `(owner_id, mangadex_id)` partial unique
index.

---

## MangaDex Integration

`MangaDexClient` (`@Service`) is the only external integration: it calls
`https://api.mangadex.org` (no auth) for search, manga metadata, and the English chapter feed.
See `https://api.mangadex.org/docs/` for the upstream API.

- **Search:** `GET /manga?title={q}&includes[]=cover_art&limit=10&offset=0` → id, title (prefers
  the `en` translation), description, and a cover URL built from the `cover_art` relationship's
  `fileName` (`https://uploads.mangadex.org/covers/{mangaId}/{fileName}`).
- **Latest English chapter:** `GET /manga/{id}/feed?translatedLanguage[]=en&order[chapter]=desc&limit=1`
  → `attributes.chapter`. Chapter values are strings and may be decimal, non-numeric, or null;
  the client parses a leading positive integer and otherwise treats the chapter as unavailable
  (skip and log) — there is no half/decimal-chapter tracking.
- Any upstream failure (timeout, non-2xx, malformed response) raises `MangaDexUpstreamException`
  (`502`). The add-manga flow tolerates a failed "latest chapter" lookup and still adds the
  manga; search and the notification job propagate/log the failure per call.

---

## CI Pipeline

Current jobs and required check names live in `.github/workflows/` and
`docs/github-operations.md`. In short: CI formats/lints, runs backend tests with coverage,
runs frontend unit and Playwright E2E checks, runs real-backend integration E2E checks, and
builds/pushes images on `main`.
