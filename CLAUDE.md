# manga-tracker — Claude Code Briefing

> Full details in `docs/developer-guide.md`, `docs/architecture.md`, and `docs/api.md`.
> GitHub operations reference: `docs/github-operations.md`.
> Human+Claude workflow guide: `docs/workflow.md`.
> Shared agent rules for Codex + Claude: `docs/agent-workflow.md`.
> This file is the compact "fast path" — read it first, reference docs for depth.
> Recommended split: use Claude for PRD/planning and independent second review, then Codex for implementation/testing/fixes.

## What This Project Is

A manga reading tracker: users add manga URLs, the backend scrapes for new chapters, and the frontend shows a reading dashboard. Stack: **Spring Boot 3 + Angular 18 + PostgreSQL**, deployed via Docker Compose.

## Directory Map

```
backend/        Spring Boot 3 (Gradle, Java 21, Jakarta EE 10)
frontend/       Angular 18 (standalone components, SCSS, Playwright E2E)
docs/           API reference, architecture, developer guide, workflow, change log
ralph/          Ralph autonomous agent system (PRD-driven iteration)
skills/         Project-specific skills and repo-owned workflows
.agents/skills/ Installed community skills (see Available Skills below)
tasks/          PRD outputs and techdebt reports
```

## Available Skills

### Project-specific (`skills/`)
| Skill | Purpose |
|-------|---------|
| `/prd` | Generate a structured PRD for a new feature |
| `/ralph` | Convert PRD to Ralph JSON for autonomous execution |
| `/techdebt` | Scan for tech debt and inconsistencies |
| `/review` | Pre-push review against **project conventions** (jakarta, Flyway, inject, etc.) |
| `/prioritize-features` | Rank candidate work by value, effort, and delivery risk |
| `/pre-mortem` | Identify likely failure modes before implementation or release |
| `/outcome-roadmap` | Group future work into outcome-based roadmap themes |
| `/supervise` | Independent second review against a plan; verify, agree/disagree, then fix only if requested |

### Installed (`.agents/skills/`)
| Skill | Purpose |
|-------|---------|
| `/angular-component` | Angular component best practices |
| `/java-springboot` | Spring Boot patterns |
| `/playwright-e2e-testing` | Playwright E2E tests |
| `/tdd` | Test-driven development workflow |
| `/code-review` | General code quality (SOLID, best practices) |
| `/security-review` | OWASP-based security scan |
| `/backend-testing` | Backend test patterns |
| `/docker-expert` | Docker and Compose guidance |
| `/github-actions-cicd` | CI/CD pipeline configuration |
| `/find-skills` | Discover and install new skills |
| `/skill-creator` | Create and iterate on custom skills |

> **`/review` vs `/code-review`:** `/review` = project conventions (jakarta, Flyway, inject). `/code-review` = general SOLID/quality.
> **Ownership split:** `skills/` is the project-owned layer; `.agents/skills/` is the imported general toolbox.

## Two-Agent Loop

Default supervised flow:
1. Claude plans
2. Codex implements
3. Codex self-reviews
4. Claude independently reviews with `/supervise`
5. If both agree it is ready, push
6. If they agree it is blocked, Codex fixes and both re-review
7. If they disagree, stop and reconcile before fixing or pushing

## Non-Negotiable Conventions

These cause CI failures or runtime errors if violated:

| Rule | Why |
|------|-----|
| `jakarta.persistence.*` — never `javax.persistence.*` | Spring Boot 3 uses Jakarta EE 10 |
| `ddl-auto=validate` — always add a Flyway migration | Hibernate validates schema on startup; no auto-DDL |
| Never edit or delete existing Flyway migrations | Checksum mismatch will block startup |
| `inject()` function — never constructor injection | ESLint `@angular-eslint/prefer-inject` enforced |
| Run `spotlessApply` before committing Java changes | CI rejects unformatted code |

## Quality Gate Commands

Run these before pushing:

```bash
# Backend
cd backend
./gradlew spotlessApply          # Auto-format Java
./gradlew test jacocoTestReport  # Unit + integration tests + coverage

# Frontend
cd frontend
npm run format                   # Prettier auto-fix
npm test                         # Vitest unit tests
npm run lint                     # ESLint check
npm run e2e                      # Playwright E2E (mocked, no backend needed)
```

## Testing Notes

- Backend integration tests use **Testcontainers** — Docker must be running.
- Frontend E2E tests use **Playwright** with mocked backend (no real server needed for `e2e/manga.spec.ts`).
- Integration E2E (`e2e/integration.spec.ts`) requires full stack via `./run-e2e-integration.sh`.

## Ralph System — Read Before Touching

The `ralph/` directory contains an autonomous agent system. **Do not modify `ralph/CLAUDE.md`, `ralph/prd.json`, or `ralph/progress.txt`** unless you are running a Ralph session or explicitly asked to. These files drive multi-iteration autonomous execution.

## Browser Verification

For any UI change, verify in browser using Playwright MCP tools:
1. Ensure the app is running (`dev.sh` or `docker compose up`)
2. Navigate to the affected page
3. Interact with the feature and take a screenshot
4. Save screenshot to `/tmp/<description>.png`

## Skill Discovery

Before starting an unfamiliar type of task, check for existing skills: `npx skills find "<keywords>"`. Install at project level (`-y`, no `-g`).
