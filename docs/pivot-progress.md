# Pivot Progress — Reading Tracker (resume notes)

Session log for implementing `docs/pivot-plan-reading-tracker.md`. Update this file at each
phase boundary; delete it once the pivot is merged.

Last updated: 2026-07-04 (~21:35 UTC), phase 1 implemented, backend suite GREEN.

## State right now

- **Branch:** `pivot/phase1-backend-mangadex` (from `main` at `77ec138`). Nothing committed yet.
- **Phase 1 code is COMPLETE** — Codex job `task-mr6uce3e-w9aoey` finished (Codex session
  `019f2ee9-b9b9-7e70-8148-dd4bcc96f967`, resumable via `codex resume <that id>`). Codex could
  not run Gradle in its sandbox, so verification fell to Fable locally.
- **Fable's own diff review: PASSED.** Scope discipline was good (backend only, net −900 lines,
  V1–V12 untouched, pre-existing local changes preserved). Details below.
- **Verification status:**
  - `./gradlew spotlessApply` — PASSED (no reformats needed).
  - `./gradlew test jacocoTestReport jacocoTestCoverageVerification` — **PASSED (green,
    coverage gate included)** after the local environment fixes below. Earlier failures were
    environment-only, never code.
- **Security/safety gate (Codex, read-only): DONE.** Findings (fixes delegated to Codex,
  in flight when this was written):
  1. HIGH (accepted, fixing): `sourceUrl` stored unvalidated; frontend `open-manga` assigns it
     to `window.location.href` → stored XSS via `javascript:` URL. Fix: backend accepts only
     absolute http(s) URLs or null. (Frontend scheme/null hardening additionally lands in
     phase 3.)
  2. MEDIUM (accepted, fixing): `/api/manga/search` proxies unbounded user text upstream with
     no per-user rate limit; Retry-After sleep uncapped. Fix: 200-char query cap, per-user
     search limiter (generalizing `AddMangaRateLimiter`, new keys
     `app.ratelimit.search-manga.*` default 30/60s), Retry-After clamped to 5s.
  3. MEDIUM (pre-existing, FIXED by Fable): `.gitignore` ignored `backend-task-def.json` but
     the real file is `backend-taskdef.json` (contains AWS account/ARNs) — both spellings now
     ignored; file no longer shows as untracked.
  4. LOW (pre-existing, DEFERRED to phase 4): backend Dockerfile still installs Chrome + curl
     (scraper-era); remove during the phase-4 Docker cleanup.
  - Gate PASSes: no auth/CSRF regression, no SSRF (fixed base URL, UUID path params), no SQLi/
    deserialization/path traversal/sensitive logging, V13 preserves row data.
- **Security fixes applied by Codex + full suite GREEN again** (spotless + tests + jacoco):
  `sourceUrl` must be absolute http(s) or null; search query capped at 200 chars +
  `SearchMangaRateLimiter` (new `SlidingWindowRateLimiter` base, add-manga behavior/config
  unchanged); MangaDex Retry-After sleep clamped to 5s.
- **Adversarial review (Codex): DONE — 3 medium findings, reconciled:**
  1. ACCEPTED (fix in flight): `latestEnglishChapter` used feed `limit=1`, so a null/"Extra"
     newest entry hides all valid chapters → fetch limit=10 and scan for first integer.
  2. ACCEPTED (fix in flight): add-manga failed entirely if the best-effort feed lookup threw →
     catch `MangaDexUpstreamException` around the feed call only; metadata failure still 502s.
  3. DEFERRED to phase 3 (already planned there, now a MUST-DO): frontend `/open/:id` redirects
     to `manga.sourceUrl` unconditionally — must handle null (in-app fallback) before deploy.
     Safe because the whole pivot ships as one branch/PR.
- **NOT yet done:** rerun suite after adversarial fixes, commit phase 1.

## Local environment fixes applied this session (keep these!)

The repo was previously built only inside Docker, so the host was missing pieces:

1. **Java 21 installed via mise** (host default is Java 26, which Gradle 8.13 can't run on):
   `mise install java@temurin-21` →
   `export JAVA_HOME=/home/maetsu/.local/share/mise/installs/java/temurin-21.0.11+10.0.LTS`
   before any `./gradlew` command. (Not activated in mise config; export manually or run
   `mise use java@temurin-21`.)
2. **Root-owned Gradle dirs chowned back to maetsu** (leftovers from Docker builds):
   `docker run --rm -v .../backend:/w -v ~/.gradle:/g alpine chown -R 1000:1000 /w/build /w/.gradle /g`
   — already done; `./gradlew` now works with the default Gradle home.
3. **`postgres:16` image pulled** (Testcontainers needed it).
4. **Docker API version mismatch (the last blocker at write time):** the Arch Docker daemon
   rejects API < 1.40; the project's docker-java falls back to 1.32 →
   `Could not find a valid Docker environment` in the three Testcontainers repository tests.
   **Fixed** with `echo 'api.version=1.44' > ~/.docker-java.properties` — full suite green
   after this. (Longer-term: bump the Testcontainers version in `backend/build.gradle`.)

## Fable diff review notes (phase 1 implementation)

Verdict: implementation is faithful to the plan + delegation refinements. Reviewed in full:

- `V13__reading_tracker_fields.sql` — exactly per plan; partial unique index
  `uq_manga_owner_mangadex_id ON manga(owner_id, mangadex_id) WHERE mangadex_id IS NOT NULL`.
- `MangaDexClient` — RestClient + 5s/10s timeouts, 200ms request spacing, single 429 retry
  honoring Retry-After, integer-only chapter policy (`^([1-9]\d*)`, logs + skips non-integer),
  English-preferred localized text, cover URL `.512.jpg`. Injectable `Sleeper` for tests.
- `MangaService` — search proxy; add by `mangaDexId` (+ optional sourceUrl/currentChapter/
  readingStatus), dedup via `existsByMangadexIdAndOwnerId` before rate-limiter check (preserves
  old behavior), race-safe via DataIntegrityViolationException → 409; PATCH now updates
  currentChapter/latestChapter/readingStatus with non-negative validation.
- `GlobalExceptionHandler` — scraper handlers replaced by `MangaDexUpstreamException` → 502.
- Null-`sourceUrl` handled in PushMessage/NotificationService/testPush (+ test asserting null).
- Scraper package, ScrapingJob, jsoup + Playwright deps: all deleted.
- Tests: MangaDexClientTest (MockRestServiceServer, no live network), controller 502/409/201
  paths, repository dedup incl. cross-owner and manual-entry cases, SecurityConfigTest covers
  `/api/manga/search` auth.
- Minor observations (acceptable, not blockers): MangaDex 404 for a bad `mangaDexId` surfaces
  as 502 (not 400); `latestChapter = max(feed, startingChapter)` is a sensible invariant guard.

## Remaining pipeline for phase 1 (in order)

1. Confirm green: `cd backend && JAVA_HOME=<mise temurin-21> ./gradlew test jacocoTestReport
   jacocoTestCoverageVerification` (check the docker-java fix worked).
2. Read-only **security/safety gate** via Codex (exact prompt shape in CLAUDE.md).
3. **Adversarial code review**: `/codex:adversarial-review --model gpt-5.5 --effort xhigh --background`.
4. Commit phase 1 on the branch — stage ONLY pivot files. Pre-existing non-pivot uncommitted
   changes to keep OUT of the commit: `.gitignore`, `AGENTS.md`, `CLAUDE.md`, `README.md`,
   `backend/Dockerfile`, `backend-taskdef.json`, `docs/cloudflare-scraper-investigation.md`,
   `docs/diagrams/`, `docs/fable-*`. (The old scraper-file local edits disappear with the
   deletions — fine.)
5. Then phase 2 (notification job), 3 (frontend), 4 (Docker/CI/docs) per the plan. Branch + PR;
   never push to `main`.

## Phase status

| Phase | Scope | Status |
|-------|-------|--------|
| 1 | Backend swap: MangaDexClient + endpoints + V13 + scraper removal + tests | Code done; verification + reviews pending |
| 2 | Notification job (MangaDex English feed) | Not started |
| 3 | Frontend search-and-pick, dashboard, null-sourceUrl open-manga | Not started |
| 4 | Dockerfile/CI cleanup + docs + final verification | Not started |
