# PRD: Cloudflare Bypass for Manga Scraper

## Introduction

The `SakuraMangasScraper` uses Jsoup (a pure Java HTTP client) to fetch manga pages from `sakuramangas.org`. The site has added Cloudflare Bot Management with interactive JavaScript challenges, returning 403 to all non-browser HTTP requests. The scraper can no longer fetch the initial manga page to extract the security tokens needed for the internal API calls.

The fix: use Playwright (Java) as a headless browser to solve Cloudflare challenges, then continue with the existing token-based API flow.

## Goals

- Restore the ability to add and track manga from `sakuramangas.org`
- Use Playwright (Java) as the headless browser engine for page fetching
- Keep the existing scraper architecture (`PageFetcher`/`ScriptFetcher`/`ApiCaller` interfaces) intact
- Ensure Docker and CI compatibility

## User Stories

### US-001: Add Playwright Java dependency
**Description:** As a developer, I need Playwright available in the backend so I can use a headless browser to fetch pages.

**Acceptance Criteria:**
- [ ] `com.microsoft.playwright:playwright` added to `build.gradle`
- [ ] Browser binaries can be installed via `playwright install chromium`
- [ ] Existing tests still pass (no dependency conflicts)
- [ ] `./gradlew spotlessApply` passes

### US-002: Replace PageFetcher with Playwright-based implementation
**Description:** As the scraper, I need to fetch the initial manga page using a real browser so that Cloudflare challenges are solved automatically.

**Acceptance Criteria:**
- [ ] Production `PageFetcher` uses Playwright to navigate to the manga URL
- [ ] Waits for Cloudflare challenge to resolve (page contains `meta[manga-id]`)
- [ ] Returns page HTML as a Jsoup `Document`
- [ ] Timeout after 30 seconds with clear `ScrapingException` message
- [ ] Test constructor with mocked `PageFetcher` still works unchanged

### US-003: Determine if API calls and script fetch need browser context
**Description:** As a developer, I need to verify whether the subsequent API calls (manga info, chapters) and the `security.oby.js` fetch still work with plain Jsoup after the page is fetched via browser.

**Acceptance Criteria:**
- [ ] Test Jsoup-based `ApiCaller` with tokens from a browser-fetched page
- [ ] Test Jsoup-based `ScriptFetcher` against the security JS URL
- [ ] If either fails: route through Playwright or pass browser cookies to Jsoup
- [ ] If both work: keep Jsoup, document why in code comments
- [ ] Decision documented

### US-004: Manage browser lifecycle
**Description:** As the application, I need a managed browser instance that doesn't leak resources or slow down every request with a cold start.

**Acceptance Criteria:**
- [ ] Browser instance is shared across scrape requests (not launched per-request)
- [ ] Initialized lazily on first scrape request
- [ ] Shut down cleanly on application shutdown
- [ ] Concurrent requests use isolated `BrowserContext` instances
- [ ] Browser is restarted if it crashes or becomes unresponsive

### US-005: Docker and CI compatibility
**Description:** As the deployment pipeline, I need the headless browser to work in Docker and CI.

**Acceptance Criteria:**
- [ ] `Dockerfile` installs Playwright Chromium and system dependencies
- [ ] Docker image size increase is documented
- [ ] CI workflow can run backend tests (browser layer mocked in tests)
- [ ] `docker compose up` still works end-to-end
- [ ] If image size is unacceptable, evaluate and document alternatives (browser sidecar, Alpine Chromium)

## Functional Requirements

- FR-1: Add `com.microsoft.playwright:playwright` to backend `build.gradle`
- FR-2: Replace the production `PageFetcher` lambda with a Playwright-based implementation that solves Cloudflare challenges
- FR-3: Wait for challenge resolution by polling for `meta[manga-id]` presence (up to 30s timeout)
- FR-4: Share a single Playwright `Browser` instance; create isolated `BrowserContext` per scrape call
- FR-5: Clean up browser resources on application shutdown (`@PreDestroy` or `DisposableBean`)
- FR-6: Fall back to Jsoup for API calls unless US-003 proves they also need browser context
- FR-7: Keep the test constructor (`PageFetcher`, `ScriptFetcher`, `ApiCaller` injection) unchanged

## Non-Goals

- No Cloudflare bypass for other scrapers (only `SakuraMangasScraper` is affected)
- No browser pool or request queuing (overkill for single-site use case)
- No frontend changes or error UX improvements
- No attempt to avoid or evade Cloudflare — we use a real browser legitimately

## Technical Considerations

- **Playwright Java** auto-manages browser binaries and has a clean API with built-in wait/selector support
- **Existing architecture:** `SakuraMangasScraper` uses three functional interfaces. Only `PageFetcher` definitely needs to change; the others depend on US-003 findings
- **Thread safety:** Playwright `Browser` is thread-safe. Each request creates its own `BrowserContext` (isolated cookies/state) and closes it after use
- **Testing:** Existing scraper tests use the test constructor with mocked interfaces — they remain unchanged. New Playwright-specific logic should be tested separately or mocked
- **Docker:** Chromium adds ~200-400MB to the image. System libraries (`libnss3`, `libatk-bridge2.0-0`, etc.) are required

## Success Metrics

- Adding `https://sakuramangas.org/obras/chainsaw-man/` via the UI succeeds and shows title + chapter number
- The background scraping job can poll existing tracked manga without 403 errors
- Backend test suite still passes
- Docker deployment works without manual browser installation steps

## Open Questions

- Will Cloudflare challenge frequency change? May need cookie persistence across requests to avoid re-solving every time
- Does `security.oby.js` also have Cloudflare protection? (US-003 will answer this)
- What is the acceptable Docker image size increase? If >500MB is a problem, evaluate the sidecar approach
