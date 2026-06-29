# Task Plan — Cloudflare Bypass for SakuraMangasScraper

## Source
- **PRD:** `tasks/prd-cloudflare-bypass-scraper.md`
- **Stories / requirements in scope:** `US-001`, `US-002`, `US-003`, `US-004`, `US-005`, `FR-1` through `FR-7`

## Goal
- Restore the ability to scrape manga from `sakuramangas.org` by using Playwright Java as a headless browser to solve Cloudflare challenges, while keeping existing tests and architecture intact.

## Context
- The PRD (`tasks/prd-cloudflare-bypass-scraper.md`) covers the full rationale.
- `sakuramangas.org` added Cloudflare Bot Management with interactive JS challenges. The current Jsoup-based `PageFetcher` gets a 403 with `cf-mitigated: challenge` header.
- Only the initial page fetch is confirmed blocked. API calls and script fetch may or may not need browser context (US-003 will determine this at implementation time).
- The scraper already uses three functional interfaces (`PageFetcher`, `ScriptFetcher`, `ApiCaller`) with a test constructor that accepts mocked versions. This architecture must be preserved.
- 14 existing tests in `SakuraMangasScraperTest.java` use the test constructor and must remain unchanged.

## Scope
### In Scope
- Add `com.microsoft.playwright:playwright` dependency to `backend/build.gradle`
- Create a `PlaywrightBrowserManager` Spring bean for browser lifecycle management
- Replace the production `PageFetcher` lambda in `SakuraMangasScraper` with a Playwright-based implementation
- Verify whether `ScriptFetcher` and `ApiCaller` still work with plain Jsoup after browser page fetch (US-003)
- Update `backend/Dockerfile` with Chromium + system dependencies
- Ensure CI mocks the browser layer (no real browser in unit tests)

### Out Of Scope
- No changes to other scrapers
- No browser pool or request queuing
- No frontend changes or error UX improvements
- No Cloudflare evasion — we use a real browser legitimately
- No changes to the test constructor or existing test suite

## Constraints And Decisions
- **Dependency:** Use `com.microsoft.playwright:playwright` (the official Java binding), not Selenium or other alternatives.
- **Architecture:** The three functional interfaces (`PageFetcher`, `ScriptFetcher`, `ApiCaller`) and the test constructor (`SakuraMangasScraper(PageFetcher, ScriptFetcher, ApiCaller)`) must remain unchanged.
- **Browser lifecycle:** A single `Browser` instance is shared across scrape requests, initialized lazily, and shut down on application shutdown. Each request creates an isolated `BrowserContext`.
- **Wait strategy:** After navigating, wait for `meta[manga-id]` to appear in the DOM (up to 30 seconds timeout). This confirms Cloudflare challenge is resolved.
- **Imports:** Use `jakarta.persistence.*` (never `javax`). Run `spotlessApply` before committing.
- **Flyway:** No schema changes needed for this task.
- **Docker base image:** The runtime stage currently uses `eclipse-temurin:21-jre-alpine`. Alpine does not support Playwright Chromium natively. Switch to `eclipse-temurin:21-jre` (Debian-based) or install Chromium + deps on Alpine. Document the image size impact.
- **US-003 decision:** If API calls and script fetch work with plain Jsoup after browser page fetch, keep Jsoup for those. If not, route through Playwright or pass browser cookies. Document the decision in code comments.

## Expected Files
- `backend/build.gradle` — Add `com.microsoft.playwright:playwright` dependency
- `backend/src/main/java/com/mangaTracker/backend/scraper/PlaywrightBrowserManager.java` — New Spring bean managing browser lifecycle (lazy init, shutdown, context creation, crash recovery)
- `backend/src/main/java/com/mangaTracker/backend/scraper/SakuraMangasScraper.java` — Replace the production `PageFetcher` lambda to use `PlaywrightBrowserManager` instead of Jsoup; optionally update `ScriptFetcher`/`ApiCaller` based on US-003 findings
- `backend/src/test/java/com/mangaTracker/backend/scraper/PlaywrightBrowserManagerTest.java` — Unit tests for the browser manager (mocked Playwright API)
- `backend/Dockerfile` — Install Chromium and required system libraries; likely switch runtime base from Alpine to Debian-based
- `.github/workflows/ci.yml` — Ensure backend tests still pass (browser layer is mocked, no Playwright install needed in CI for unit tests)

## Implementation Plan

### Step 1: Add Playwright dependency (US-001)
1. Add `implementation 'com.microsoft.playwright:playwright:1.52.0'` to `backend/build.gradle` under `// Scraping`.
2. Run `./gradlew dependencies --no-daemon` to verify resolution.
3. Run `./gradlew spotlessApply` and `./gradlew test` to confirm no conflicts with existing tests.

### Step 2: Create PlaywrightBrowserManager (US-004)
1. Create `PlaywrightBrowserManager` as a `@Component` Spring bean in the `scraper` package.
2. Implement lazy initialization: `Playwright.create()` and `playwright.chromium().launch()` on first call, not at bean creation.
3. Implement `fetchPage(String url)` that:
   - Creates an isolated `BrowserContext` (new cookies/state each time)
   - Creates a `Page`, navigates to the URL
   - Waits for `meta[manga-id]` selector (up to 30s timeout)
   - Gets page content and parses it with `Jsoup.parse()` to return a `Document`
   - Closes the `BrowserContext` in a `finally` block
4. Implement `@PreDestroy` shutdown that closes browser and Playwright instances.
5. Add basic crash recovery: if `browser.isConnected()` returns false, re-launch.
6. Thread safety: synchronize browser initialization; `BrowserContext` creation is already isolated per-request.

### Step 3: Replace production PageFetcher (US-002)
1. Change `SakuraMangasScraper` to accept an optional `PlaywrightBrowserManager` via constructor injection for the production path.
2. The production constructor (no-arg, used by Spring) should `@Autowired` the `PlaywrightBrowserManager` and wire the `pageFetcher` lambda to call `browserManager.fetchPage(url)`.
3. The test constructor remains exactly as-is: `SakuraMangasScraper(PageFetcher, ScriptFetcher, ApiCaller)`.
4. Increase the page-fetch timeout from `TIMEOUT_MS` (15s) to 30s for the Playwright path, since Cloudflare challenges take time.

### Step 4: Determine API/script fetch strategy (US-003)
1. With the Playwright `PageFetcher` working, manually test whether:
   - `ScriptFetcher` (Jsoup GET to `security.oby.js`) still returns valid JS
   - `ApiCaller` (Jsoup POST to manga info/chapters APIs) still returns valid data
2. If both work with plain Jsoup: keep them as-is, add a code comment documenting why.
3. If either fails: either route through Playwright (use `page.evaluate()` or `page.request()`) or extract cookies from the browser context and pass them to Jsoup connections.
4. Document the decision clearly in the code.

### Step 5: Docker compatibility (US-005)
1. Change the runtime stage base image from `eclipse-temurin:21-jre-alpine` to `eclipse-temurin:21-jre` (Debian-based) to support Playwright Chromium.
2. Add a `RUN` step to install Playwright Chromium and its system dependencies:
   ```dockerfile
   RUN npx playwright install --with-deps chromium
   ```
   Or use Playwright's Java CLI: `RUN java -cp app.jar com.microsoft.playwright.CLI install --with-deps chromium`
3. Document the image size increase in a comment in the Dockerfile.
4. Test with `docker compose up` end-to-end.

### Step 6: CI compatibility (US-005)
1. Backend unit tests mock the `PlaywrightBrowserManager` — no real browser needed.
2. Verify that `./gradlew test` in CI still passes without Playwright browser binaries installed.
3. If integration E2E tests need Playwright Java (unlikely — they test the frontend), add browser install to the CI workflow.

## Risks
- **Alpine incompatibility:** Playwright does not officially support Alpine Linux. Switching to Debian-based image is the safest path but increases image size by ~200-400MB.
- **Cloudflare challenge changes:** Cloudflare may change challenge type or frequency. The wait-for-selector approach handles JS challenges but may not handle CAPTCHAs. Monitor and adapt.
- **Cookie persistence:** If Cloudflare requires cookies for subsequent requests (ScriptFetcher, ApiCaller), the Jsoup-only path for those will fail. US-003 testing will catch this.
- **Browser memory:** A long-running Chromium process in a container consumes ~100-200MB RAM. Acceptable for single-site use but should be documented.
- **Playwright version pinning:** Pin the Playwright version in `build.gradle` to avoid unexpected browser binary updates.
- **CI flakiness:** If any test accidentally depends on a real browser, CI will fail. Ensure all tests use the test constructor with mocked interfaces.

## Verification

**Success commands:**
- `cd backend && ./gradlew spotlessApply`
- `cd backend && ./gradlew test jacocoTestReport`
- `cd backend && ./gradlew jacocoTestCoverageVerification`

**Manual verification:**
- Start the app with `./dev.sh` or `docker compose up`
- Add `https://sakuramangas.org/obras/chainsaw-man/` via the UI
- Confirm the manga title and chapter count appear correctly (no 403 error)
- Check backend logs for successful Playwright page fetch and Cloudflare challenge resolution

**Evidence:**
- Screenshot of successful manga addition in the UI
- Backend log snippet showing successful scrape flow

**Quality gates:**
- `backend`

**Max fix attempts:**
- `3`

**Watch targets:**
- `backend/build.gradle` — Playwright dependency version must be pinned
- `backend/src/main/java/com/mangaTracker/backend/scraper/SakuraMangasScraper.java` — test constructor must remain unchanged; no `javax.persistence` imports
- `backend/src/main/java/com/mangaTracker/backend/scraper/PlaywrightBrowserManager.java` — must have `@PreDestroy` shutdown; must create isolated `BrowserContext` per request
- `backend/Dockerfile` — runtime base must support Chromium; image size increase documented
- `backend/src/test/java/com/mangaTracker/backend/scraper/SakuraMangasScraperTest.java` — all 14 existing tests must pass unchanged

## Review Gate

**Implementer review:**
- Codex runs `review this`

**Independent review:**
- Claude runs `/supervise`

**Review evidence:**
- Implementer self-review summary or link
- Independent review summary or link

**Agreement rule:**
- `agree-pass` = both reviews say ready
- `agree-fail` = both reviews say blockers remain
- `disagree` = reviewers differ materially; stop and reconcile before fixing or pushing

**Fix owner:**
- original implementer unless the user redirects

**Re-review trigger:**
- Any fix after review requires both agents to review again

## Acceptance Checklist
- [ ] Source PRD and stories / requirements references are accurate
- [ ] Playwright dependency added and resolves without conflicts
- [ ] Production PageFetcher uses Playwright to solve Cloudflare challenges
- [ ] Browser lifecycle managed (lazy init, shared instance, clean shutdown, crash recovery)
- [ ] US-003 decision documented (Jsoup vs Playwright for API/script calls)
- [ ] Test constructor and all 14 existing tests unchanged and passing
- [ ] Docker image builds and runs with Chromium support
- [ ] `spotlessApply` passes
- [ ] `./gradlew test jacocoTestReport` passes
- [ ] Manual verification: adding a manga from sakuramangas.org succeeds
- [ ] Review evidence is captured
- [ ] Both reviews reach `agree-pass`

## Handoff Notes
- US-003 is a discovery step — the implementer must test and document the outcome before proceeding with step 4. If API calls need browser cookies, the implementation approach for `ScriptFetcher`/`ApiCaller` changes significantly.
- The Dockerfile base image switch (Alpine → Debian) is the biggest infrastructure change. Test `docker compose up` thoroughly.
- Cookie persistence across requests (Open Question from PRD) is deferred unless US-003 testing reveals it's needed now.
- Image size increase should be measured and noted in the PR description.
