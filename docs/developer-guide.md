# Developer Guide

## Project Structure

```text
manga-tracker/
├── README.md
├── docker-compose.yml
├── docs/
│   ├── api.md
│   ├── architecture.md
│   └── developer-guide.md       ← you are here
│
├── backend/                     Spring Boot application
│   ├── Dockerfile
│   ├── build.gradle
│   ├── config/checkstyle/       Checkstyle rules
│   └── src/
│       ├── main/
│       │   ├── java/com/mangatracker/backend/
│       │   │   ├── BackendApplication.java
│       │   │   ├── controller/  HTTP layer (auth, manga, push, global exceptions)
│       │   │   ├── service/     Business logic
│       │   │   ├── repository/  JPA repositories
│       │   │   ├── model/       JPA entities
│       │   │   ├── job/         Scheduled MangaDex notification and demo reset jobs
│       │   │   ├── security/    Cookie-JWT auth, CSRF, current-user helpers
│       │   │   └── exception/   Domain exceptions
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/ Flyway migrations
│       └── test/                JUnit 5 unit and integration tests
│
└── frontend/                    Angular 22 application
    ├── Dockerfile
    ├── nginx.conf
    ├── angular.json
    ├── eslint.config.js
    ├── playwright.config.ts
    ├── src/
    │   ├── app/                 Standalone components, routes, services,
    │   │                         guards, and interceptors
    │   └── environments/        environment.ts / environment.prod.ts
    └── e2e/                     Playwright E2E tests
        ├── manga.spec.ts        Mocked unit-style E2E tests
        └── integration.spec.ts  Integration tests (requires real backend)
```

---

## Running Tests

### Backend

```bash
cd backend

# Unit + integration tests with coverage
./gradlew test jacocoTestReport

# Check coverage threshold (70% minimum)
./gradlew jacocoTestCoverageVerification

# Individual test class
./gradlew test --tests "com.mangatracker.backend.service.MangaServiceTest"
./gradlew test --tests "com.mangatracker.backend.service.MangaDexClientTest"
./gradlew test --tests "com.mangatracker.backend.job.MangaDexNotificationJobTest"
```

Backend integration tests (repository layer) use **Testcontainers** to spin up a real PostgreSQL
container automatically. Docker must be available when running these tests.

Test reports: `backend/build/reports/tests/test/index.html`
Coverage reports: `backend/build/reports/jacoco/test/html/index.html`

### Frontend

```bash
cd frontend

# Vitest unit tests
npm test

# Playwright E2E tests (mocks backend — no real server needed)
npm run e2e

# Run a single test file
npx playwright test e2e/manga.spec.ts

# Interactive UI mode
npx playwright test --ui
```

Install Playwright browsers on first run:

```bash
npx playwright install --with-deps chromium
```

### Frontend — Integration E2E (real backend)

These tests hit the real backend and database via `docker compose`. Use the runner script from the project root:

```bash
# Run integration tests (services stay up after)
./run-e2e-integration.sh

# Run integration tests then tear down services
./run-e2e-integration.sh --down
```

The script will:

1. Start all services via `docker compose up -d`
2. Wait for the backend health check and frontend readiness
3. Run proxy/auth/health smoke checks
4. Run `npx playwright test e2e/integration.spec.ts`

You can also run them manually if services are already up:

```bash
cd frontend
npx playwright test e2e/integration.spec.ts
```

---

## MangaDex Integration

Manga metadata (search results, cover art, latest English chapter) comes from the MangaDex API
via `MangaDexClient` (`backend/src/main/java/com/mangatracker/backend/service/MangaDexClient.java`),
not from scraping any manga site directly.

- `MangaDexClientTest` covers search, metadata lookup, and English-chapter parsing against
  mocked HTTP responses.
- `MangaDexNotificationJobTest` covers the daily best-effort English-chapter notification job.

The optional `sourceUrl` field on a tracked manga is just a "read it here" link the user
supplies — it is never fetched or scraped by the backend.

---

## Code Style

### Java (Backend)

- **Formatter:** [Spotless](https://github.com/diffplug/spotless) with `google-java-format`
- **Indentation:** 2 spaces (Google Java style)
- **Linter:** Checkstyle with rules in `backend/config/checkstyle/checkstyle.xml`

Apply formatting before committing:

```bash
cd backend
./gradlew spotlessApply
```

CI auto-commits Spotless fixes with `[skip ci]` if you forget.

### TypeScript / Angular (Frontend)

- **Formatter:** [Prettier](https://prettier.io/) — `printWidth=100`, single quotes, trailing commas
- **Linter:** [ESLint](https://eslint.org/) with `@angular-eslint` rules
- **Key rule:** Use `inject()` function instead of constructor injection (`@angular-eslint/prefer-inject`)

Check and fix before committing:

```bash
cd frontend
npm run format        # Prettier auto-fix
npm test              # Vitest unit tests
npm run lint          # ESLint check
```

---

## CI, Checks, And Branch Policy

For current workflow jobs, required check names, autofix behavior, and branch policy, use
`docs/github-operations.md` and `.github/workflows/` as the source of truth. Keep local command
details in the testing and code-style sections above.

---

## Web Push notifications (VAPID)

New chapters are delivered as Web Push notifications to subscribed browsers (installable PWA) —
there is no email path. The backend signs pushes with a VAPID key pair supplied via env:

```dotenv
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...      # secret — never commit; .env is gitignored
VAPID_SUBJECT=mailto:you@example.com
```

Generate a pair with `npx web-push generate-vapid-keys --json`. Copy `.env.example` to `.env`
(auto-loaded by `docker compose`). The frontend fetches the public key from
`GET /api/push/public-key`, subscribes via the service worker, and POSTs the subscription to
`/api/push/subscribe`. Web Push requires HTTPS in production; `localhost` is exempt for dev. To
test on a phone, expose the dev frontend over HTTPS (e.g. a cloudflared quick tunnel to
`http://localhost:4200`).

---

## Authentication (cookie-JWT)

The backend uses stateless, cookie-based JWT auth (see `docs/api.md` and `docs/architecture.md`).
On startup `UserSeeder` creates two accounts from env, BCrypt-hashing the passwords:

```dotenv
JWT_SECRET=...            # secret — HMAC signing key for the auth JWT; never commit
OWNER_PASSWORD=...        # secret — password for the private OWNER account; never commit
DEMO_PASSWORD=...         # secret — password for the public DEMO account; never commit
```

- `JWT_SECRET` should be a long random string; if it changes, existing auth cookies are invalidated.
- `OWNER` is the private library; `DEMO` is a public, passwordless account (login via
  `POST /api/auth/demo-login`) whose library is reset nightly by `DemoResetJob`.
- All three live in `.env` (gitignored) and are documented in `.env.example` with placeholder
  values. Optional knobs: `app.auth.allowed-origins` (enable CORS for a cross-origin frontend),
  `app.ratelimit.add-manga.max` / `.window-seconds` (per-user add limit, default 20 / 60s),
  `app.demo.reset-cron` (default `0 0 4 * * *`, `America/Sao_Paulo`).

---

## Common Gotchas

### `jakarta` vs `javax` imports

Spring Boot 3 uses **Jakarta EE 10**. All JPA annotations must use `jakarta.persistence.*`, not `javax.persistence.*`. Using `javax` will cause runtime failures.

```java
// Correct
import jakarta.persistence.Entity;
import jakarta.persistence.Column;

// Wrong (Spring Boot 2 era)
import javax.persistence.Entity;
```

### `ddl-auto=validate` — schema must match exactly

JPA is configured with `spring.jpa.hibernate.ddl-auto=validate`. Hibernate validates the entity
fields against the existing database schema on startup and **fails fast** if they don't match.

Always create a new Flyway migration (`V{n}__description.sql`) for any schema change instead of
editing existing migrations. Never add JPA fields without a corresponding migration.

### Angular `inject()` vs constructor injection

ESLint (`@angular-eslint/prefer-inject`) enforces `inject()` function over constructor injection.

```typescript
// Correct
private readonly http = inject(HttpClient);

// Will fail ESLint
constructor(private http: HttpClient) {}
```

### `@Output()` is not flagged by `prefer-inject`

`@Output() eventName = new EventEmitter<T>()` is the correct pattern for child-to-parent events in
standalone components. This is not affected by the `prefer-inject` rule.

### Flyway migration ordering

Migrations run in version order (`V1`, `V2`, `V3`, ...). Never change or delete an existing migration
that has already been applied to any environment — Flyway will reject the checksum mismatch. Always
add new migrations with the next version number.

### Testcontainers requires Docker

Repository integration tests (`@DataJpaTest` + Testcontainers) spin up a real PostgreSQL container.
They require Docker to be running locally. These tests are skipped or will fail if Docker is not available.
