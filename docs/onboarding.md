# New Developer Onboarding

Use this guide for your first day in the MangaTracker codebase. It focuses on human onboarding; agent workflow details live in `docs/agent-workflow.md`.

## First Hour

1. Read `README.md` for the product summary and quick start.
2. Read `docs/architecture.md` for the backend/frontend/database map.
3. Read `docs/api.md` for auth, CSRF, error format, and where to find Swagger.
4. Copy `.env.example` to `.env` and fill the required secrets.
5. Start the app with Docker Compose.

```bash
cp .env.example .env
docker compose up
```

Open the frontend at <http://localhost:4200> after the services become healthy.

## Required Tools

- Docker Desktop or another Docker runtime.
- Java 21 for backend work.
- Node matching `frontend/package.json` engines; Node 24.15+ matches CI.
- npm for frontend dependency management.
- Playwright browsers when running frontend E2E locally.

Install Playwright browsers on first use:

```bash
cd frontend
npx playwright install --with-deps chromium
```

## Running Locally

### Full Stack With Docker

Use this when you want the app to behave like production locally:

```bash
docker compose up
```

Docker Compose starts PostgreSQL, the Spring Boot backend, and the Angular frontend served through nginx.

### Backend Only

Use this when changing Java code and you already have PostgreSQL available:

```bash
cd backend
./gradlew bootRun
```

The API runs at <http://localhost:8080>.

### Frontend Only

Use this when changing Angular code and the backend is already running:

```bash
cd frontend
npm install
npm start
```

The dev server runs at <http://localhost:4200> and proxies `/api` to the backend.

## Test Commands

Run the smallest useful check while developing, then run the relevant full gate before opening a PR.

### Backend

```bash
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

Repository tests use Testcontainers, so Docker must be running.

### Frontend

```bash
cd frontend
npm run format
npm test
npm run lint
npm run e2e
```

### Real Backend E2E

Run this when a change touches frontend/backend behavior together:

```bash
./run-e2e-integration.sh --down
```

## Common Changes Map

| Task | Start Here | Notes |
|------|------------|-------|
| Add or change an API endpoint | `backend/src/main/java/com/mangatracker/backend/controller/` | Keep endpoint contracts in controller annotations and DTOs; Swagger is generated. |
| Change manga business rules | `backend/src/main/java/com/mangatracker/backend/service/MangaService.java` | Preserve owner scoping: cross-user access should look like `404`. |
| Add a database change | `backend/src/main/resources/db/migration/` | Add a new Flyway migration. Never edit an existing migration. |
| Add a new scraper | `backend/src/main/java/com/mangatracker/backend/scraper/` | Implement `MangaScraper`; Spring discovers the bean automatically. |
| Change login/auth behavior | `backend/src/main/java/com/mangatracker/backend/security/` and `frontend/src/app/services/auth.service.ts` | Remember CSRF and `withCredentials`. |
| Change dashboard UI | `frontend/src/app/dashboard/` | Keep service calls in `frontend/src/app/services/`. |
| Change push notifications | `backend/src/main/java/com/mangatracker/backend/service/PushNotificationService.java` and `frontend/src/app/services/push.service.ts` | Test on HTTPS for real phones. |

## Troubleshooting

### Docker Or Testcontainers Fail

Confirm Docker is running before backend repository tests or integration E2E. Testcontainers needs access to the Docker daemon.

### Push Subscription Fails

Check that `.env` has `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, and `VAPID_SUBJECT`. Without VAPID keys, the app can run but push subscribe/test endpoints cannot complete.

### Phone Push Does Not Work On LAN HTTP

Browser push requires a secure context. `localhost` is exempt, but `http://<your-ip>:4200` from a phone is not. Use an HTTPS tunnel such as Cloudflare Tunnel when testing on a phone.

### State-Changing Requests Return 403

The backend uses CSRF protection. The frontend should call `GET /api/auth/csrf` and send `X-XSRF-TOKEN` on `POST`, `PATCH`, and `DELETE` requests. Check `frontend/src/app/interceptors/auth.interceptor.ts` first.

### Frontend E2E Cannot Find Browser

Install Playwright's Chromium browser:

```bash
cd frontend
npx playwright install --with-deps chromium
```

### Scraping Is Flaky

Sakura scraping depends on a rendered page and Cloudflare behavior. Start with `SakuraMangasScraperTest` for parser behavior and `PlaywrightBrowserManagerTest` for browser setup. Treat live-site failures as integration/environment issues until reproduced with saved HTML.

## Review Checklist

Before opening a PR:

- Run the relevant format, lint, and test commands.
- Record any skipped checks and why.
- Keep changes focused; avoid mixing feature work with workflow or formatting cleanup.
- For API changes, verify Swagger at <http://localhost:8080/swagger-ui.html>.
