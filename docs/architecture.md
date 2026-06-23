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
| Job          | `job`                                        | Scheduled background polling; calls scraper + notification  |
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
