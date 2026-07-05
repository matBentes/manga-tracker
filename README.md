# MangaTracker

Track the manga you read, enriched by the **MangaDex API**, and get a **best-effort push
notification on your phone** when a new English chapter drops.

Search MangaDex, add a title with real metadata (cover, synopsis), and track your own reading
progress and status. Keep an optional "read it here" link if you follow the chapters somewhere
else. A daily job checks MangaDex's English chapter feed for titles you're tracking and pushes a
notification to every device you've subscribed. A neo-brutalist dashboard shows what's new at a
glance.

![Dashboard](docs/images/dashboard.png)

We originally scraped a manga site directly for new chapters; that approach ran into Cloudflare
bot protection that can't be automated unattended, so the app pivoted to a MangaDex-backed
tracker — see [`docs/cloudflare-scraper-investigation.md`](docs/cloudflare-scraper-investigation.md)
for the engineering case study behind the pivot.

## Features

- **Search & add from MangaDex** — search by title, pick a result, and the app pulls in the
  title, cover, and synopsis automatically.
- **Reading progress & status** — track current/latest chapter and a reading status
  (Reading, Completed, On Hold, Dropped, Plan to Read) per title.
- **Optional read-here link** — attach a source URL to jump straight to where you read, or leave
  it blank.
- **Web push notifications** — real browser/phone push (no email). Works on Android and installed iOS PWAs.
- **Best-effort English chapter check** — a daily job (08:00 America/São_Paulo) polls MangaDex's
  English chapter feed for tracked titles; new chapters trigger a push. Non-English chapters and
  half/decimal chapter numbers are not tracked.
- **Per-manga notify toggle** — mute titles you don't care about.
- **Read / unread tracking** — mark chapters read, "NEW" badge for anything you haven't caught up on.
- **Test push** — one button per card to verify notifications reach your device.
- **Installable PWA** — add to home screen, launches like a native app.

## Tech Stack

| Layer     | Technology                                         |
|-----------|----------------------------------------------------|
| Backend   | Spring Boot 3.4 · Java 21 · Gradle · Jakarta EE 10 |
| Frontend  | Angular 22 · TypeScript · SCSS · Service Worker    |
| Database  | PostgreSQL 16 · Flyway migrations                  |
| Push      | Web Push (VAPID) · `nl.martijndwars:web-push`      |
| Metadata  | MangaDex API (search, cover art, English chapter feed) |
| Testing   | JUnit 5 · Mockito · Testcontainers · Playwright (frontend E2E) |
| Container | Docker · docker compose                            |

## Quick Start

```bash
git clone <repo-url>
cd manga-tracker

# 1. generate VAPID keys for web push (see below) and put them in .env
cp .env.example .env
# edit .env with your generated keys, JWT_SECRET, and account passwords

# 2. start the stack
docker compose up
```

App: **<http://localhost:4200>** once all services report healthy.

> First startup builds the images — give it a few minutes.

## Web Push Setup (VAPID)

Push notifications are signed with a **VAPID key pair**. Generate one once:

```bash
npx web-push generate-vapid-keys
```

Put the output in `.env` at the repo root:

```dotenv
VAPID_PUBLIC_KEY=<public key>
VAPID_PRIVATE_KEY=<private key>
VAPID_SUBJECT=mailto:you@example.com
```

docker compose passes these to the backend automatically. Without a public key, browsers cannot subscribe; without the private key, delivery fails when a push is sent.

> **Keep the private key secret.** Never commit `.env`. The public key is safe to expose.

## Testing Push on Your Phone

Service workers require a **secure (HTTPS) context** — `localhost` is exempt, but a
phone on your LAN hitting `http://<your-ip>:4200` is **not**, so push won't work there.
Expose the app over HTTPS with a quick tunnel:

```bash
cloudflared tunnel --url http://localhost:4200
```

This prints a temporary `https://<random>.trycloudflare.com` URL. Then on your phone:

1. Open that URL in **Chrome (Android)** or **Safari (iOS 16.4+)**.
2. *(iOS only)* **Add to Home Screen** and open the app from there — iOS only allows
   push from an installed PWA.
3. Open **Settings**, enable phone notifications, and grant permission.
4. Use each card's **Notify** toggle only to mute or unmute that manga.
5. Tap **Test push** — a notification should appear on your phone.

## Local Development (Without Docker)

**Backend** (Java 21) — needs a local PostgreSQL and `JWT_SECRET`; set `OWNER_PASSWORD` or `DEMO_PASSWORD` to log in:

```bash
cd backend
./gradlew bootRun
```

API on **<http://localhost:8080>**.

**Frontend** (Node matching `frontend/package.json` engines):

```bash
cd frontend
npm install
npm start
```

Dev server on **<http://localhost:4200>**, proxies `/api` to `localhost:8080`.

## Environment Variables

| Variable             | Default                                          | Description                          |
|----------------------|--------------------------------------------------|--------------------------------------|
| `DB_URL`             | `jdbc:postgresql://localhost:5432/manga_tracker` | JDBC connection URL                  |
| `DB_USERNAME`        | `manga_tracker`                                  | PostgreSQL username                  |
| `DB_PASSWORD`        | `manga_tracker`                                  | PostgreSQL password                  |
| `JWT_SECRET`         | *(required)*                                     | JWT signing secret, at least 32 bytes|
| `OWNER_PASSWORD`     | *(empty)*                                        | Seeds the private owner account      |
| `DEMO_PASSWORD`      | *(empty)*                                        | Seeds the public demo account        |
| `AUTH_COOKIE_SECURE` | `true`                                           | Set `false` for HTTP-only local dev  |
| `VAPID_PUBLIC_KEY`   | *(empty)*                                        | VAPID public key for web push        |
| `VAPID_PRIVATE_KEY`  | *(empty)*                                        | VAPID private key (keep secret)      |
| `VAPID_SUBJECT`      | `mailto:…`                                       | VAPID subject (contact mailto/URL)   |

In docker compose, DB vars are set automatically and app secrets come from `.env`.

## Quality Gates

```bash
# backend
cd backend
./gradlew spotlessApply             # format
./gradlew test jacocoTestReport jacocoTestCoverageVerification

# frontend
cd frontend
npm run format:check && npm test && npm run lint
npm run build -- --configuration production
npm run e2e                         # Playwright, mocked backend
```

## Documentation

- [API Concepts](docs/api.md) — auth, CSRF, demo behavior, rate limits, and error format. Full endpoint reference is generated at `http://localhost:8080/swagger-ui.html`.
- [Architecture Overview](docs/architecture.md)
- [Architecture Diagrams](docs/diagrams/README.md)
- [Why We Pivoted From Scraping To MangaDex](docs/cloudflare-scraper-investigation.md)
- [AWS Deployment Runbook](docs/aws-deployment.md)
- [New Developer Onboarding](docs/onboarding.md)
- [Developer Guide](docs/developer-guide.md)
- [Agent Workflow (Codex + Claude)](docs/agent-workflow.md)
