# PRD: Manga Reading Tracker

## Introduction

A web application that lets a single user track their manga reading progress and get notified when new chapters are released. The user adds manga by pasting URLs from supported manga sites (initially Sakura Mangás for pt-BR, with English sources planned), and the system scrapes those sites to detect new chapter releases, sending email notifications when updates are found.

## Goals

- Allow the user to track which manga they're reading and their current chapter
- Detect new chapter releases by scraping/polling manga source sites
- Send email notifications when new chapters are available for followed manga
- Provide a simple, clean web interface for managing the reading list
- Support pt-BR manga sources initially (sakuramangas.org), with English sources later

## Development Methodology

All feature development follows **TDD with Red-Green-Refactor (RGR)**:
1. **Red:** Write a failing test that defines the expected behavior
2. **Green:** Write the minimum code to make the test pass
3. **Refactor:** Clean up the code while keeping all tests green

This applies to every user story — tests come first, implementation follows.

## User Stories

### US-001: Add manga by URL
**Description:** As a user, I want to paste a manga URL so that the system automatically detects the manga title and current chapter count.

**Acceptance Criteria:**
- [ ] Input field accepts a URL from a supported manga site (sakuramangas.org)
- [ ] System scrapes the page using Jsoup to extract manga title and latest available chapter
- [ ] Manga is added to the reading list with chapter progress set to 0
- [ ] User sees a confirmation with the detected manga title
- [ ] Error message shown if URL is unsupported or scraping fails
- [ ] Typecheck/lint passes
- [ ] Verify in browser using dev-browser skill

### US-002: View reading list
**Description:** As a user, I want to see all my tracked manga in a list so I can manage my reading progress.

**Acceptance Criteria:**
- [ ] Dashboard displays all tracked manga with: title, current chapter, latest available chapter, source URL
- [ ] Manga with unread chapters are visually distinguished (e.g., badge or highlight)
- [ ] List is sorted by most recently updated by default
- [ ] Typecheck/lint passes
- [ ] Verify in browser using dev-browser skill

### US-003: Update reading progress
**Description:** As a user, I want to update which chapter I've read so the tracker reflects my actual progress.

**Acceptance Criteria:**
- [ ] User can set their current chapter number for any tracked manga
- [ ] Chapter number is validated (must be a positive integer, cannot exceed latest available)
- [ ] Progress updates immediately in the UI
- [ ] Typecheck/lint passes
- [ ] Verify in browser using dev-browser skill

### US-004: Remove manga from reading list
**Description:** As a user, I want to remove a manga I no longer follow so my list stays clean.

**Acceptance Criteria:**
- [ ] Delete button on each manga entry
- [ ] Confirmation prompt before deletion
- [ ] Manga and all associated data removed from the list
- [ ] Typecheck/lint passes
- [ ] Verify in browser using dev-browser skill

### US-005: Scrape manga sites for new chapters
**Description:** As a system, I need to periodically check manga source URLs for new chapter releases so the user can be notified.

**Acceptance Criteria:**
- [ ] Background job (Spring `@Scheduled`) runs on a configurable interval (default: every 30 minutes)
- [ ] For each tracked manga, scrapes the source URL using Jsoup to detect the latest chapter number
- [ ] Updates the "latest available chapter" in the database when a new chapter is found
- [ ] Handles scraping failures gracefully (retries, logging, does not crash the job)
- [ ] If a hidden API is discovered on the source site, prefer it over HTML scraping
- [ ] Typecheck/lint passes

### US-006: Send email notifications for new chapters
**Description:** As a user, I want to receive an email when a new chapter is released for a manga I follow so I don't miss updates.

**Acceptance Criteria:**
- [ ] Email sent when a new chapter is detected for a tracked manga
- [ ] Email includes: manga title, new chapter number, link to the source
- [ ] Emails are not sent for chapters the user has already read
- [ ] Duplicate emails are not sent for the same chapter
- [ ] Uses Mailhog (or similar) for local development testing
- [ ] Typecheck/lint passes

### US-007: Manage email notification preferences
**Description:** As a user, I want to enable or disable email notifications so I control what I receive.

**Acceptance Criteria:**
- [ ] Toggle to enable/disable all email notifications
- [ ] Per-manga toggle to enable/disable notifications for specific titles
- [ ] Settings persist across sessions
- [ ] Typecheck/lint passes
- [ ] Verify in browser using dev-browser skill

### US-008: Unit and integration tests
**Description:** As a developer, I want unit and integration tests so that core logic is verified automatically.

**Development approach: TDD with Red-Green-Refactor (RGR) cycle:**
1. **Red:** Write a failing test first that defines the expected behavior
2. **Green:** Write the minimum code to make the test pass
3. **Refactor:** Clean up the code while keeping tests green

**Acceptance Criteria:**
- [ ] Tests are written BEFORE implementation code (TDD)
- [ ] Each feature follows the RGR cycle: failing test → passing code → refactor
- [ ] Unit tests for scraper logic (parsing HTML, extracting title/chapter)
- [ ] Unit tests for service layer (add manga, update progress, notification logic)
- [ ] Integration tests for REST API endpoints (Spring Boot test slices)
- [ ] Integration tests for database repository layer with Testcontainers (PostgreSQL)
- [ ] All tests pass in CI
- [ ] Typecheck/lint passes

### US-009: E2E tests with Playwright
**Description:** As a developer, I want end-to-end tests so that critical user flows are verified in a real browser.

**Acceptance Criteria:**
- [ ] Playwright installed and configured for the Angular frontend
- [ ] E2E test: add manga by URL and verify it appears in the reading list
- [ ] E2E test: update reading progress and verify the UI reflects the change
- [ ] E2E test: remove manga and verify it's gone from the list
- [ ] All E2E tests pass in CI
- [ ] Typecheck/lint passes

### US-010: Dockerize the application
**Description:** As a developer, I want Docker containers so the app can be built, tested, and deployed consistently.

**Acceptance Criteria:**
- [ ] Dockerfile for Spring Boot backend (multi-stage build)
- [ ] Dockerfile for Angular frontend (build + nginx serve)
- [ ] docker-compose.yml with backend, frontend, PostgreSQL, and Mailhog services
- [ ] `docker compose up` starts the full stack locally
- [ ] Typecheck/lint passes

### US-011: GitHub Actions CI/CD pipeline
**Description:** As a developer, I want a CI/CD pipeline so that every push is automatically built, tested, and deployable with strict quality gates.

**Acceptance Criteria:**

**Stage 1 — Format & Lint:**
- [ ] Checkstyle enforces Java code formatting (fail on violations)
- [ ] ESLint + Prettier enforces Angular code formatting (fail on violations)
- [ ] Angular typecheck passes (`ng build` with strict mode)

**Stage 2 — Static Analysis:**
- [ ] SonarCloud analysis runs on every PR
- [ ] Quality gate: 80%+ code coverage
- [ ] Quality gate: zero bugs
- [ ] Quality gate: zero code smells
- [ ] Pipeline blocks merge if quality gate fails

**Stage 3 — Tests:**
- [ ] Unit tests (JUnit 5 + Jasmine/Karma)
- [ ] Integration tests (Spring Boot Test + Testcontainers)
- [ ] Playwright E2E tests

**Stage 4 — Code Review:**
- [ ] Automated AI PR review (e.g., Claude or CodeRabbit) posts comments on PR
- [ ] Branch protection: at least 1 human approval required before merge

**Stage 5 — Build & Deploy:**
- [ ] Build Docker images (backend + frontend)
- [ ] Push images to container registry
- [ ] Deploy to staging environment

**General:**
- [ ] Pipeline triggers on push/PR to main branch
- [ ] Pipeline fails fast on any stage failure
- [ ] PR cannot be merged unless all stages pass + human approval

## Functional Requirements

- FR-1: User can add manga by pasting a URL; the system scrapes the page to extract the title and latest chapter number
- FR-2: The reading list displays all tracked manga with title, current progress, latest chapter, and source link
- FR-3: User can update their current chapter number for any tracked manga
- FR-4: User can remove manga from the reading list with confirmation
- FR-5: A background job (`@Scheduled`) periodically scrapes all tracked manga URLs to detect new chapters
- FR-6: When a new chapter is detected, an email notification is sent (if notifications are enabled)
- FR-7: User can toggle email notifications globally and per-manga
- FR-8: Unit and integration tests cover scraper logic, services, and API endpoints
- FR-9: Playwright E2E tests cover critical user flows (add, update, remove manga)
- FR-10: Application is fully Dockerized with docker-compose for local development
- FR-11: GitHub Actions CI/CD pipeline with 5 stages: format/lint → static analysis → tests → code review → build/deploy
- FR-12: Checkstyle enforces Java formatting; ESLint + Prettier enforces Angular formatting
- FR-13: SonarCloud quality gate requires 80%+ coverage, zero bugs, zero code smells
- FR-14: AI-powered automated PR review posts comments on every pull request
- FR-15: Branch protection requires all CI stages to pass + at least 1 human approval before merge

## Non-Goals

- No user authentication (single-user, localhost-only for MVP)
- No manga reader built into the app (user reads on the source site)
- No social features (sharing lists, following other users)
- No mobile app (web-only for MVP)
- No support for downloading or caching manga content
- No recommendation engine or discovery features
- No reading statistics or analytics dashboard

## Design Considerations

- Clean, minimal dashboard focused on the reading list
- Responsive layout that works on mobile browsers
- Clear visual distinction between "up to date" and "new chapters available" manga
- Simple settings page for notification preferences

## Technical Considerations

- **Backend:** Spring Boot (Java)
- **Frontend:** Angular
- **Database:** PostgreSQL
- **Scraping:** Jsoup for HTML parsing; start with HTML scraping, switch to hidden API if discovered
- **Email:** Spring Mail with Mailhog for local development; swap to a real SMTP/service for production later
- **Scheduling:** Spring `@Scheduled` for periodic scraping jobs
- **Architecture:** Modular scraper design — each manga site gets its own parser/scraper class so new sources can be added easily
- Scraping must respect rate limits and robots.txt of source sites
- URL validation must check against a list of supported manga sites
- **Initial supported site:** sakuramangas.org (pt-BR); English sources to be added later
- **Testing:** JUnit 5 + Mockito for unit tests, Spring Boot Test + Testcontainers for integration tests, Playwright for E2E
- **Containers:** Docker multi-stage builds for backend and frontend, docker-compose for local stack
- **CI/CD:** GitHub Actions with 5 stages: format/lint → SonarCloud → tests → AI code review + human approval → Docker build + deploy
- **Java formatting:** Checkstyle
- **Angular formatting:** ESLint + Prettier
- **Static analysis:** SonarCloud (strict gate: 80%+ coverage, zero bugs, zero code smells)
- **Code review:** AI-powered PR review (Claude or CodeRabbit) + mandatory human approval

## Success Metrics

- User can add a manga by URL and see detected title/chapter in under 5 seconds
- New chapter detection within 1 hour of release (depending on poll interval)
- Email notifications delivered within minutes of chapter detection
- Zero duplicate notifications per chapter

## Open Questions

- Should there be a limit on how many manga can be tracked?
- How to handle manga sites that block scraping or change their DOM structure?
- Which English manga source(s) to support first when expanding beyond pt-BR?

## Data Model

### Entity: `manga`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK | Unique identifier |
| `title` | VARCHAR(255) | NOT NULL | Scraped manga title |
| `source_url` | TEXT | NOT NULL, UNIQUE | URL provided by the user |
| `current_chapter` | INTEGER | NOT NULL, DEFAULT 0 | Chapter the user has read up to |
| `latest_chapter` | INTEGER | NOT NULL, DEFAULT 0 | Latest chapter detected by the scraper |
| `notifications_enabled` | BOOLEAN | NOT NULL, DEFAULT TRUE | Per-manga notification toggle |
| `last_checked_at` | TIMESTAMP | NULLABLE | When the scraper last polled this manga |
| `created_at` | TIMESTAMP | NOT NULL | When the manga was added |
| `updated_at` | TIMESTAMP | NOT NULL | When any field last changed |

### Entity: `notification_log`

Prevents duplicate email notifications.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK | Unique identifier |
| `manga_id` | UUID | FK → manga.id | Which manga triggered this notification |
| `chapter_number` | INTEGER | NOT NULL | Chapter that triggered the notification |
| `sent_at` | TIMESTAMP | NOT NULL | When the email was dispatched |

### Entity: `app_settings`

Single-row settings table (seeded on first run).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | INTEGER | PK, DEFAULT 1 | Always 1 (enforced by CHECK constraint) |
| `email_notifications_enabled` | BOOLEAN | NOT NULL, DEFAULT TRUE | Global notification toggle |
| `notification_email` | VARCHAR(255) | NOT NULL | Destination email address |
| `poll_interval_minutes` | INTEGER | NOT NULL, DEFAULT 30 | How often the scraper runs |

### Indexes

- `manga(source_url)` — unique index for deduplication on add
- `notification_log(manga_id, chapter_number)` — unique index to prevent duplicate sends

---

## API Specification

All endpoints return `application/json`. The base path is `/api`.

### Manga

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/manga` | List all tracked manga, sorted by `updated_at DESC` |
| `POST` | `/api/manga` | Add manga by URL (triggers scrape on creation) |
| `PATCH` | `/api/manga/{id}` | Update `current_chapter` or `notifications_enabled` |
| `DELETE` | `/api/manga/{id}` | Remove manga and its notification log entries |

#### `POST /api/manga` — request body
```json
{ "sourceUrl": "https://sakuramangas.org/manga/one-piece/" }
```

#### `POST /api/manga` — response (201 Created)
```json
{
  "id": "uuid",
  "title": "One Piece",
  "sourceUrl": "https://sakuramangas.org/manga/one-piece/",
  "currentChapter": 0,
  "latestChapter": 1112,
  "notificationsEnabled": true,
  "createdAt": "2026-03-01T10:00:00Z"
}
```

#### `POST /api/manga` — error responses
- `400 Bad Request` — unsupported site or malformed URL
- `422 Unprocessable Entity` — scraping succeeded but could not extract title/chapter
- `409 Conflict` — manga with that URL already tracked

#### `PATCH /api/manga/{id}` — request body (all fields optional)
```json
{ "currentChapter": 42, "notificationsEnabled": false }
```

### Settings

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/settings` | Retrieve current app settings |
| `PUT` | `/api/settings` | Replace all settings fields |

#### `PUT /api/settings` — request body
```json
{
  "emailNotificationsEnabled": true,
  "notificationEmail": "user@example.com",
  "pollIntervalMinutes": 60
}
```

### Health

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Spring Boot Actuator health check (used by Docker/CI) |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                        Browser                          │
│                  Angular SPA (port 4200)                │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP (REST + JSON)
┌──────────────────────▼──────────────────────────────────┐
│              Spring Boot Backend (port 8080)            │
│                                                         │
│  ┌─────────────────┐   ┌──────────────────────────────┐ │
│  │  REST Controllers│   │  Scheduling (@Scheduled)     │ │
│  └────────┬────────┘   └──────────────┬───────────────┘ │
│           │                           │                  │
│  ┌────────▼───────────────────────────▼───────────────┐ │
│  │                  Service Layer                      │ │
│  │  MangaService  │  ScraperService  │  NotificationSvc│ │
│  └────────┬────────────────┬────────────────┬──────────┘ │
│           │                │                │            │
│  ┌────────▼──────┐  ┌──────▼──────┐  ┌─────▼──────────┐ │
│  │  JPA Repos    │  │  Scraper    │  │  Spring Mail   │ │
│  │  (Manga,      │  │  Registry   │  │  (JavaMail)    │ │
│  │  NotifLog,    │  └──────┬──────┘  └────────────────┘ │
│  │  AppSettings) │         │                            │ │
│  └────────┬──────┘  ┌──────▼──────────────────────┐    │ │
│           │         │  Site-specific Scrapers       │    │ │
│           │         │  SakuraMangasScraper          │    │ │
│           │         │  [MangaDexScraper, ...]       │    │ │
│           │         └─────────────────────────────-─┘    │ │
└───────────┼──────────────────────────────────────────────┘
            │
  ┌─────────▼─────────┐       ┌──────────────┐
  │   PostgreSQL       │       │   Mailhog    │
  │   (port 5432)      │       │   (port 8025)│
  └───────────────────┘       └──────────────┘
```

---

## Scraper Design

The scraper layer is designed for easy extensibility. Each supported site has its own implementation behind a common interface.

### Interface: `MangaScraper`

```java
public interface MangaScraper {
    /** Returns true if this scraper handles the given URL. */
    boolean supports(String url);

    /** Scrapes the page and returns extracted manga data. */
    ScrapedManga scrape(String url) throws ScrapingException;
}
```

### `ScrapedManga` record

```java
public record ScrapedManga(String title, int latestChapter) {}
```

### `ScraperRegistry`

A Spring `@Component` that holds all `MangaScraper` beans and resolves the correct one for a given URL:

```java
@Component
public class ScraperRegistry {
    private final List<MangaScraper> scrapers;
    // ...
    public MangaScraper resolve(String url) {
        return scrapers.stream()
            .filter(s -> s.supports(url))
            .findFirst()
            .orElseThrow(() -> new UnsupportedSourceException(url));
    }
}
```

### `SakuraMangasScraper`

- Uses Jsoup to fetch and parse `sakuramangas.org` manga pages
- Extracts the title from `<h1>` (or site-specific selector)
- Extracts the latest chapter number from the chapter list
- Falls back to a discovered hidden API endpoint if available (checked first)
- Throws `ScrapingException` on parse failure

### Adding a new scraper

1. Create a class implementing `MangaScraper`
2. Annotate with `@Component`
3. Register the new domain in the supported-sites allowlist (`SupportedSites` enum or config)
4. Add unit tests for the new scraper's HTML parsing

### Error handling strategy

| Failure | Behavior |
|---|---|
| Network timeout | Retry up to 3 times with exponential backoff; log warning |
| HTTP 4xx/5xx | Log error, skip this manga for this polling cycle |
| Parse failure | Log error with URL, mark `last_checked_at`, skip notification |
| All scrapers fail | Job continues for remaining manga; no crash |

---

## Configuration Reference

All configurable values use Spring `application.properties` / environment variables.

| Property | Env Variable | Default | Description |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | — | PostgreSQL JDBC URL |
| `spring.datasource.username` | `DB_USERNAME` | — | DB user |
| `spring.datasource.password` | `DB_PASSWORD` | — | DB password |
| `spring.mail.host` | `MAIL_HOST` | `localhost` | SMTP host (Mailhog in dev) |
| `spring.mail.port` | `MAIL_PORT` | `1025` | SMTP port |
| `app.scraper.poll-interval` | `POLL_INTERVAL_MINUTES` | `30` | Scrape frequency in minutes |
| `app.scraper.request-timeout-ms` | `SCRAPER_TIMEOUT_MS` | `10000` | HTTP timeout per request |
| `app.scraper.retry-max-attempts` | — | `3` | Max retries on network failure |
| `app.notification.from-email` | `NOTIFICATION_FROM_EMAIL` | `tracker@localhost` | Sender address |

---

## Deployment Considerations

### Local development (`docker compose up`)

Services started:
- `db` — PostgreSQL 16
- `mailhog` — Mailhog (SMTP on 1025, UI on 8025)
- `backend` — Spring Boot (port 8080), depends on `db`
- `frontend` — Angular dev server (port 4200), proxies `/api` to backend

### Staging / production

- Replace Mailhog with a real SMTP provider (e.g., SendGrid, SES) via environment variables
- Use a managed PostgreSQL instance (e.g., RDS, Supabase) instead of the Docker container
- Frontend served via nginx (already in the multi-stage Dockerfile)
- Backend health check: `GET /actuator/health` returns `{"status":"UP"}`

### Migration strategy

- Flyway manages database schema migrations
- Migration scripts live in `src/main/resources/db/migration/`
- Naming convention: `V{version}__{description}.sql` (e.g., `V1__create_manga_table.sql`)
