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
- **Adversarial fixes applied + suite GREEN + PHASE 1 COMMITTED** as `a8d3324` on
  `pivot/phase1-backend-mangadex` (pivot files only; pre-existing local changes remain
  uncommitted in the working tree).
- **Phase 2 DONE, committed `403e8ac`**: `MangaDexNotificationJob` (daily 08:00 São Paulo).
  Combined security+adversarial review found one HIGH (notify failure permanently suppressed a
  chapter notification because latestChapter advanced before notify) — fixed by keying the
  notify decision to `currentChapter` + NotificationLog dedup, so failures retry next run.
  Also fixed: distinct `mangadexId` fetched once per run across owners. Rejected as
  speculative for this single-node app: paging, per-run caps, scheduler locks.
- **Phase 3 (frontend) implemented by Codex; verification found a real CD bug.**
  Checks: format:check/lint/prod build/unit tests all GREEN locally (after a small spec fix:
  a detached `<select>` needs an `<option>` before `value=` sticks). Prod build has one
  pre-warning: dashboard SCSS 941 bytes over the 4 kB budget (trim in phase 4).
  **E2E caught a genuine regression:** the debounced search (Subject → debounceTime →
  switchMap(http)) updates plain fields in-zone but NO change-detection tick follows — results
  never render until an unrelated user event. Verified: state correct + `ng.applyChanges`
  renders (bindings fine); rAF alive; not eventCoalescing; old code renders delayed responses
  fine → regression is in the new component. Dashboard masked the same symptom with 16 manual
  `detectChanges()` calls (left as-is, tech debt). **Fix in flight (Codex
  `task-mr7am1k2-11ai62`): convert add-form rendered state to signals** — signal writes notify
  the CD scheduler regardless of zone stability.
  Env notes: frontend needs Node 24 via mise (`~/.local/share/mise/installs/node/24.15.0/bin`,
  host Node 25 violates engines); `npm ci` + `npx playwright install chromium` done locally.
- **Signal fix VERIFIED: full frontend chain GREEN** (format:check, 25+ unit tests, lint, prod
  build, e2e 3/3 including the previously-failing search flow).
- **Codex usage limit hit (resets ~3:43 AM)** → per the new CLAUDE.md rule, phase 3 review gate
  is running on a **Sonnet 5 subagent** fallback. Remaining: reconcile review findings, commit
  phase 3, then phase 4 (Dockerfile/CI/docs + cross-service e2e + PR).

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
| 1 | Backend swap: MangaDexClient + endpoints + V13 + scraper removal + tests | DONE — committed `a8d3324`, gates passed |
| 2 | Notification job (MangaDex English feed) | DONE — committed `403e8ac`, reviewed + green |
| 3 | Frontend search-and-pick, dashboard, null-sourceUrl open-manga | DONE — committed `777362a`; incl. signals CD fix + stale-response guard |
| 4 | Dockerfile/CI cleanup + docs + final verification | DONE — committed `10a1c96` |

## Final state (all phases complete)

- **PR #32 open: https://github.com/matBentes/manga-tracker/pull/32**
- Learnings write-up: [`pivot-retrospective.md`](pivot-retrospective.md) (linked from README).
- Commits: `a8d3324` (phase 1), `403e8ac` (phase 2), `777362a` (phase 3), `10a1c96` (phase 4).
- Final verification, all green: backend `spotlessApply test jacocoTestReport
  jacocoTestCoverageVerification`; frontend `format:check`/`test`/`lint`/prod `build`/`e2e`;
  `./run-e2e-integration.sh --down` 8/8 + smoke checks against freshly built images.
- Backend image: 2.06GB → 607MB after dropping Chrome.
- Two bugs only the integration run caught: MangaDexClient had two constructors and Spring
  couldn't boot the container (fixed with @Autowired on the production constructor — no test
  slice boots the full context; a real-context smoke test is a good follow-up), and a brittle
  `getByText` selector in integration.spec.ts (fixed with `exact: true`).
- Phase 4 was executed by a Sonnet 5 subagent (Codex usage limit); it also fixed the container
  healthcheck (wget → curl, wget only existed via the Chrome install) and made a one-line
  factual fix in `openspec/specs/production-deployment/spec.md` (out-of-band spec edit,
  flagged in the PR for ratification).
- Follow-ups (non-blocking): real-context Spring smoke test; trim dashboard SCSS (941B over
  the 4kB budget warning); dashboard's 16 manual detectChanges() calls → signals refactor;
  ECS task CPU/memory could be downsized now that Chrome is gone (see aws-deployment.md).
