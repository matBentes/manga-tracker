# Pivot Retrospective — What We Learned Replacing the Scraper with MangaDex

> Companion to [`cloudflare-scraper-investigation.md`](cloudflare-scraper-investigation.md)
> (why we pivoted) and [`pivot-plan-reading-tracker.md`](pivot-plan-reading-tracker.md) (the
> plan). This is the retrospective: what happened, what it cost, and what we'd repeat.

## The short version

The original product promise — "paste a manga URL, we scrape it and notify you of new
chapters" — died against Cloudflare Bot Management: an interactive challenge that cannot be
solved unattended, IP-bound tokens, and encrypted internal APIs. Instead of an arms race we
could not win (and would have to re-win every week), we pivoted to a design that is *boringly
reliable*: the MangaDex public API for search/metadata and a best-effort English-chapter
notification feed, with the user's own reading progress as the core value.

The pivot shipped as four phases on one branch (PR #32), each phase compiling and green before
the next started. Net effect: the scraping stack (Playwright, Chromium, jsoup, scraper
package, scraping job) is gone; the backend Docker image shrank from **2.06 GB to 607 MB**;
and every user-facing behavior is either fully reliable (CRUD, search, progress) or explicitly
labeled best-effort (English-only chapter notifications).

## Engineering lessons

### 1. Knowing when to stop is a feature

Two full investigation rounds (documented in the case study) established that unattended
Sakura scraping was not "hard" but *structurally infeasible*: the cheapest workaround still
required a human to solve a browser challenge roughly daily — which is worse than the site's
own notification bell. Writing that conclusion down honestly, with evidence, turned a failed
feature into an engineering-judgment artifact and made the pivot decision easy to defend.

### 2. Design the pivot to be subtractive

The reading-tracker pivot *removed* more code than it added (phase 1 alone was net −900
lines). The `Manga` entity already had `currentChapter`/`latestChapter`; the push-notification
pipeline already routed by owner. The pivot mostly deleted the fragile part and connected the
solid parts to a stable upstream. Lesson: before adding a new subsystem, check how much of the
old one is genuinely load-bearing.

### 3. Make best-effort semantics explicit, everywhere

MangaDex chapter numbers are strings that may be decimal ("10.5"), special ("Extra"), or null.
Rather than widening our data model, we kept integer chapters and wrote the policy down:
parse leading positive integers, skip and log the rest, label notifications "best-effort,
English-only" in the UI, the API docs, and the README. Half-promises that look like full
promises are how trust dies; labeled best-effort is fine.

### 4. Each verification layer caught a different bug class

This is the strongest lesson of the project. Nothing was redundant:

| Layer | What it caught |
| ------- | ---------------- |
| Unit tests | Chapter-parsing policy, dedup, limiter windows |
| Security review gate | Stored XSS via the optional read-here URL (`javascript:` → `window.location.href`); unbounded search proxy (no rate limit, uncapped `Retry-After` sleeps) |
| Adversarial review | Notification permanently lost if delivery failed after `latestChapter` advanced; `limit=1` feed query blinded by one "Extra" chapter |
| Playwright e2e (mocked) | An Angular change-detection regression: debounced-search results never rendered until the next unrelated user event — unit tests passed because they call `detectChanges()` manually |
| Integration run (real containers) | **The backend container could not boot**: `MangaDexClient` grew a second (test) constructor and Spring couldn't choose. Every test suite was green; only booting the real Spring context in the real image caught it |

Corollary: a test pyramid without a "boot the real thing" layer has a hole exactly where
production incidents live. (Follow-up on the list: a real-context smoke test in CI.)

### 5. Fix change detection with the framework, not against it

The Angular bug above had a tempting wrong fix — sprinkle `ChangeDetectorRef.detectChanges()`
until symptoms stop (the dashboard accumulated 16 such calls before review). The durable fix
was converting component state to **signals**, whose writes notify Angular's scheduler
directly, independent of zone timing. When a framework fights you, reach for its newer
first-class primitive before reaching for its escape hatch.

### 6. Multi-agent delegation works when the gates are non-negotiable

The pivot was built with an orchestrator/executor split (Claude as planner/reviewer, Codex —
later Sonnet, after a usage limit — as implementer). What made it work was not the models but
the fixed pipeline: plan → adversarial plan review → scoped delegation → orchestrator diff
review → security gate → adversarial review → full verification → commit. Every phase's
executor produced at least one issue that a gate caught. Delegation without gates would have
shipped all of them.

## What it cost / what we got

- **Removed:** scraper package + tests, `ScrapingJob`, Playwright + jsoup deps, Chromium from
  the image, scraper CI flags, ~1.4 GB of image weight.
- **Added:** `MangaDexClient` + `MangaDexNotificationJob` + search/add/progress API +
  search-and-pick UI + reading status, with per-user rate limits and URL validation.
- **Unchanged promises:** per-user auth and data isolation, web-push delivery, the demo flow.
- **Follow-ups tracked in [`pivot-progress.md`](pivot-progress.md):** real-context smoke
  test, dashboard signals refactor, SCSS budget, ECS task downsizing.
