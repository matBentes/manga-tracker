# Developer Guide

## Project Structure

```
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
│       │   ├── java/com/mangaTracker/backend/
│       │   │   ├── BackendApplication.java
│       │   │   ├── controller/  HTTP layer (MangaController, SettingsController, GlobalExceptionHandler)
│       │   │   ├── service/     Business logic (MangaService, SettingsService, NotificationService)
│       │   │   ├── repository/  JPA repositories
│       │   │   ├── model/       JPA entities (Manga, AppSettings, NotificationLog)
│       │   │   ├── scraper/     MangaScraper interface, ScraperRegistry, SakuraMangasScraper
│       │   │   ├── job/         ScrapingJob (@Scheduled)
│       │   │   └── exception/   Domain exceptions
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/ Flyway migrations (V1, V2, V3)
│       └── test/                JUnit 5 unit and integration tests
│
└── frontend/                    Angular 18 application
    ├── Dockerfile
    ├── nginx.conf
    ├── angular.json
    ├── eslint.config.js
    ├── playwright.config.ts
    ├── src/
    │   ├── app/
    │   │   ├── app.config.ts    Angular bootstrapping
    │   │   ├── app.routes.ts    Router config
    │   │   ├── dashboard/       Reading list page
    │   │   ├── settings/        Settings page
    │   │   ├── add-manga/       Add manga form
    │   │   └── services/        MangaService, SettingsService (HttpClient)
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
./gradlew test --tests "com.mangaTracker.backend.service.MangaServiceTest"
```

Backend integration tests (repository layer) use **Testcontainers** to spin up a real PostgreSQL
container automatically. Docker must be available when running these tests.

Test reports: `backend/build/reports/tests/test/index.html`
Coverage reports: `backend/build/reports/jacoco/test/html/index.html`

### Frontend

```bash
cd frontend

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
3. Run `npx playwright test e2e/integration.spec.ts`

You can also run them manually if services are already up:

```bash
cd frontend
npx playwright test e2e/integration.spec.ts
```

---

## Adding a New Scraper

To support a new manga site:

1. **Create the scraper class** in `backend/src/main/java/com/mangaTracker/backend/scraper/`:

   ```java
   @Component
   public class MyNewSiteScraper implements MangaScraper {

     @Override
     public boolean supports(String url) {
       return url != null && url.contains("mynewsite.com");
     }

     @Override
     public ScrapedManga scrape(String url) throws ScrapingException {
       try {
         Document doc = Jsoup.connect(url).timeout(10_000).get();
         String title = /* extract title from doc */;
         int latestChapter = /* extract chapter number from doc */;
         if (title == null || title.isBlank()) throw new ScrapingException("Title not found");
         return new ScrapedManga(title, latestChapter);
       } catch (IOException e) {
         throw new ScrapingException("Failed to fetch page: " + e.getMessage());
       }
     }
   }
   ```

2. **No registration needed.** Spring auto-wires all `MangaScraper` beans into `ScraperRegistry` via `List<MangaScraper>`. The new scraper is picked up automatically.

3. **Write tests** in `src/test/java/com/mangaTracker/backend/scraper/`:
   - `supports()` returns `true` for your domain URLs and `false` for others.
   - `scrape()` correctly extracts title and chapter from a sample HTML string.
   - `scrape()` throws `ScrapingException` when title or chapter cannot be extracted.

   See `SakuraMangasScraperTest` for a reference pattern.

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
npm run lint          # ESLint check
```

---

## CI Pipeline Explained

See `.github/workflows/ci.yml`. The pipeline has four stages:

| Stage            | Trigger            | What runs                                                    |
|------------------|--------------------|--------------------------------------------------------------|
| 1. Format & Lint | Every push/PR      | Spotless, Checkstyle, ESLint, Prettier, `ng build --prod`    |
| 2. Tests         | After stage 1      | JUnit 5, Jacoco coverage, SonarCloud, Playwright E2E         |
| 3. AI Code Review| PRs only           | Claude posts review comments on the PR                       |
| 4. Build & Deploy| Push to `main` only| Builds Docker images, pushes to GHCR (`ghcr.io`)             |

Branch protection on `main` requires stages 1 and 2 to pass plus at least one human approval.

### CI Auto-fix Setup

The repository includes `.github/workflows/ci-autofix.yml`, which can attempt a minimal Codex-based
fix after a failed `CI` run on a pull request. It only runs when:

- the failed workflow is `CI`
- the event is `pull_request`
- the PR comes from the same repository
- the PR has the `autofix` label

The workflow also requires a repository secret named `OPENAI_API_KEY`. Without that secret, the job
will run but skip the actual Codex fix step.

Configure it in one of these ways:

```bash
# From a shell that already has OPENAI_API_KEY set locally
gh secret set OPENAI_API_KEY --body "$OPENAI_API_KEY"
```

Or via GitHub UI:

- Repository -> `Settings` -> `Secrets and variables` -> `Actions`
- Add a new repository secret named `OPENAI_API_KEY`

`fix/*` pull requests from this repository are labeled automatically by
`.github/workflows/pr-autofix-label.yml`, so once the secret exists the next failed `CI` run on a
`fix/*` PR is eligible for auto-fix.

## Branch Policy

To reduce accidental bypass of failing CI, this repository enforces a local Git hook:

- `.githooks/pre-push` blocks direct pushes to `main` by default.
- Expected flow: feature branch -> pull request -> CI green -> merge.
- Emergency override (intentional only):

```bash
ALLOW_MAIN_PUSH=1 git push origin main
```

---

## Mailhog — Viewing Test Emails

In Docker mode, email is routed to [Mailhog](https://github.com/mailhog/MailHog) instead of a real SMTP server.

- **SMTP (for backend):** `localhost:1025`
- **Web UI (to read emails):** `http://localhost:8025`

Open `http://localhost:8025` in your browser after `docker compose up` to see all outgoing notifications.

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
