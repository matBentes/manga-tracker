# Pivot Plan — From Sakura Scraper to MangaDex-backed Reading Tracker

> Hardened after a Codex adversarial plan review (findings folded in below).

## Why

Scraping `sakuramangas.org` for new chapters is blocked by Cloudflare Bot Management
(interactive challenge) and cannot be done reliably or unattended — see
`docs/cloudflare-scraper-investigation.md`. We are pivoting the app from an auto-scraping
notifier to a **personal manga reading-progress tracker** enriched by the **MangaDex API**,
with **best-effort English-only new-chapter notifications** from MangaDex's chapter feed.

## Goal

A reliable, honest CRUD reading tracker: search MangaDex, add a title with metadata, track
your own progress and status, keep an optional "read it here" link (e.g. Sakura), and get
best-effort English new-chapter push notifications from MangaDex.

## Key design decisions (resolved from the plan review)

- **`source_url` becomes nullable** (an optional "read it here" link, not identity). A new
  migration drops the owner-scoped unique index on `source_url` and moves duplicate detection
  to `mangadex_id`.
- **Duplicate detection is by `(owner_id, mangadex_id)`**, via a partial unique index
  (`WHERE mangadex_id IS NOT NULL`) and a new repository method. Manual entries without a
  mangadex_id are allowed and not dedup'd.
- **Chapter numbers stay `int`, integer-only.** MangaDex chapter values are strings and may be
  decimal (`10.5`), special (`"Extra"`), or null. Policy: parse leading positive integers,
  **skip and log** anything non-integer. Documented as a known limitation (no half-chapter
  tracking). Avoids a larger decimal/string model migration.
- **Notifications are best-effort, English-only**, driven by MangaDex's feed, clearly labeled
  as such in the UI.

## Remove (scraping stack) — NOT a standalone phase (see Phasing)

- `backend/.../scraper/` package: `SakuraMangasScraper`, `PlaywrightBrowserManager`,
  `MangaScraper`, `ScraperRegistry`, `ScrapedManga` (+ tests, `SakuraE2ELiveTest`).
- `backend/.../job/ScrapingJob.java` (replaced by the notification job).
- Playwright dependency in `backend/build.gradle` (`build.gradle:53`).
- Chromium + system-lib install and scraper env in `backend/Dockerfile` (~lines 25, 46) —
  expect ~300–400MB image reduction (note it in the PR).
- Scraper flag in CI: `--scraper.sakura.playwright.enabled=false` in `.github/workflows/ci.yml`
  (~line 286).
- `UnsupportedSourceException` + the scraping path in `GlobalExceptionHandler` (only once no
  longer referenced).

## Add / change

### Data model — new Flyway migration only (never edit existing)

`V13__reading_tracker_fields.sql`:

- `ALTER TABLE manga ADD COLUMN reading_status VARCHAR(32) NOT NULL DEFAULT 'READING';`
- `ALTER TABLE manga ADD COLUMN mangadex_id UUID;`
- `ALTER TABLE manga ALTER COLUMN source_url DROP NOT NULL;`
- Drop the existing owner-scoped unique index on `source_url` (added in
  `V12__make_manga_source_url_unique_per_owner.sql`).
- `CREATE UNIQUE INDEX ... ON manga (owner_id, mangadex_id) WHERE mangadex_id IS NOT NULL;`

Entity (`Manga.java`): add `readingStatus` (`@Enumerated(EnumType.STRING)`), `mangadexId`
(`UUID`, nullable); make `source_url` nullable (`@Column(nullable = true)`). Keep
`ddl-auto=validate` aligned. Keep `current_chapter`/`latest_chapter` as `int`
(user/MangaDex-sourced, not scraped).

### MangaDex integration (backend)

New `MangaDexClient` (`@Service`) against `https://api.mangadex.org` (no auth). See official
docs: <https://api.mangadex.org/docs/> .

- **Search:** `GET /manga?title={q}&includes[]=cover_art&limit=10&offset=0`. From each result:
  id, `attributes.title` (prefer `en`), description, and cover filename from the `cover_art`
  relationship's `attributes.fileName`.
- **Cover URL:** `https://uploads.mangadex.org/covers/{mangaId}/{fileName}` (optionally
  `.512.jpg` thumbnail).
- **Latest English chapter:** `GET /manga/{id}/feed?translatedLanguage[]=en&order[chapter]=desc&limit=1`
  → `attributes.chapter` (string; apply the integer-only policy).
- Timeouts, 429 backoff/retry, and ~5 req/s courtesy limit. Handle pagination where relevant.

### Endpoints (`MangaController`)

- `GET /api/manga/search?q=` → proxied MangaDex search results (title, cover, id, synopsis).
- Add-manga accepts `mangaDexId` (+ optional `sourceUrl`, starting progress/status) → fetch
  metadata → persist; reject duplicate `(owner_id, mangadex_id)`.
- `PATCH` to update `currentChapter`, `latestChapter`, `readingStatus`.
- Update request/response DTOs accordingly; preserve per-user ownership + auth on all routes;
  update OpenAPI annotations.

### Notification job (replaces ScrapingJob)

- New scheduled job (keep daily cadence). New repository query for manga with `mangadexId != null`
  AND `notificationsEnabled` (owner-scoped rows). For each: `latestEnglishChapter`; if it
  exceeds stored `latestChapter`, update and fire the existing
  `NotificationService.notify(manga, chapter)` (already routes push by `ownerId`).
- Per-item try/catch + continue; update `lastCheckedAt` on success and failure; never crash
  the run. English-only / best-effort.

### Frontend (Angular, `inject()` DI — never constructor injection)

- `add-manga` (`add-manga-form.component.ts`, `manga.service.ts`): replace URL-only form with
  MangaDex **search-and-pick** — query, list results with cover, select to pre-fill
  title/cover/synopsis; optional "read here" URL + starting progress/status. Update the add DTO.
- `dashboard` (`dashboard.component.*`): show cover, progress (`current`/`latest`),
  `readingStatus`, and the read-here link; controls to bump `currentChapter` and change status.
- `open-manga` (`open-manga.component.ts`): `/open/:id` must handle a **null `sourceUrl`**
  (no redirect target) gracefully instead of assuming it exists.
- Keep `settings`/push UI; label notifications as best-effort English-only.

### Tests (offset removed scraper coverage — gate is 70%, `build.gradle:87`)

Scraper assumptions also live in `MangaServiceTest`, `ScrapingJobTest`, `MangaControllerTest`,
`SecurityConfigTest` — update/replace these. Add tests for: `MangaDexClient` (mocked HTTP),
the add/search flow, the new notification job, and the migration/repository dedup behavior.
Add replacements before deleting scraper coverage so the gate stays green.

## Out of scope

- No Sakura scraping, no Playwright, no Cloudflare work.
- No non-English notification reliability; no half/decimal-chapter tracking (integer-only).
- No new auth/session/CSRF changes.
- Do not edit existing Flyway migrations; do not push to `main`; branch + PR.

## Success criteria

- Backend compiles at every phase boundary; Playwright dep + Chromium gone; smaller image; CI
  scraper flag removed.
- Search MangaDex, add a title with real metadata, edit progress + status — persisted per user;
  duplicate `(owner, mangadex_id)` rejected.
- Best-effort English notification job detects a new MangaDex English chapter (test, client
  mocked) and triggers web-push.
- `./gradlew spotlessApply test jacocoTestReport jacocoTestCoverageVerification` passes (≥70%).
- Frontend: `npm run format:check`, `npm test`, `npm run lint`, prod `build` pass.

## Compile-safety checklist (touch together, keep it building)

`MangaService` (`:7,29,52`), `MangaController` + tests, `GlobalExceptionHandler`, `ScrapingJob`
(`:5,50`), `MangaRepository` (`existsBySourceUrlAndOwnerId` `:23` → mangadex_id dedup),
removed exceptions, OpenAPI docs, `build.gradle`, `Dockerfile`, `ci.yml`, frontend DTOs +
`open-manga` null-`sourceUrl` path, and the docs listed below.

### Docs to update (during the build, alongside the code — not before)

- `README.md` — reframe as a MangaDex-backed reading tracker (not a scraper).
- `CLAUDE.md` — the "Project Snapshot" line ("backend scrapes for new chapters").
- `docs/architecture.md` and `docs/api.md` — system behavior + endpoints.
- `docs/developer-guide.md` — drop Sakura/Playwright setup + test references.
- `docs/onboarding.md` — remove scraper onboarding steps.
- `docs/diagrams/application.mmd` + `application.source.md` (regenerate the rendered SVG).
- Link `docs/cloudflare-scraper-investigation.md` as the "why we pivoted" case study.
- Leave historical journals as-is: `docs/aws-deploy-journey.md`, `docs/aws-deploy-progress.md`.

## Phasing (one editing executor at a time; each phase compiles + tests green)

1. **Backend swap (single compiling patch):** add `MangaDexClient` + search/add endpoints +
   `V13` migration + entity changes + mangadex_id dedup, AND remove the scraper stack +
   replace the add path in `MangaService`, in the same phase. Update backend tests.
2. **Notification job:** replace `ScrapingJob` with the best-effort MangaDex English job + tests.
3. **Frontend:** search-and-pick add-manga, dashboard progress/status, `open-manga` null-link
   handling.
4. **Build/Docker/CI + docs + final verification.**
