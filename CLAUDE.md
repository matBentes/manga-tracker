# manga-tracker — Claude Code Entrypoint

This is the compact Claude Code entrypoint for this repository.

Read these in order:

1. `docs/agent-workflow.md` (shared agent rules and dual-agent workflow)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and API concepts; use Swagger for endpoint contracts)
4. `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Project Snapshot

- Manga reading tracker: users add manga URLs, backend scrapes for new chapters, frontend shows the reading dashboard.
- Stack: Spring Boot 3, Java 21, Angular 22, PostgreSQL, Docker Compose.
- Main directories: `backend/`, `frontend/`, `docs/`, `openspec/`.

## Agent Setup

- Reusable dual-agent workflow templates and shared review skills live at `https://github.com/matBentes/agent-workflows`.
- Install `/dual-opus`, `/dual-gpt`, OpenSpec bootstrap files, and `thermo-nuclear-code-quality-review` from that repo; do not vendor them here.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, or local `skills/` artifacts unless the team explicitly decides to vendor them.
- `openspec/` is vendored and canonical: it is the main planning system (proposals, specs, tasks). Retired pre-OpenSpec plans live under `openspec/deprecated-tasks/` for reference only.

## Claude Role

- Own exploration, proposal shaping, plan confirmation, implementation review, final approval, sync, and archive.
- Use `/dual-opus explore`, `/dual-opus propose`, `/dual-opus confirm-plan`, `/dual-opus review-impl`, `/dual-opus final-review`, `/dual-opus sync`, and `/dual-opus archive` when the external workflow is installed.
- Let OpenCode/GPT handle implementation, accepted fixes, PR warning fixes, and CI fixes via `/dual-gpt`.

## Non-Negotiables

- Use `jakarta.persistence.*`, never `javax.persistence.*`.
- Keep `spring.jpa.hibernate.ddl-auto=validate` compatible with Flyway schema.
- Never edit/delete existing Flyway migrations; add a new `V{n}__*.sql` migration.
- Angular DI must use `inject()` rather than constructor injection.
- Do not revert unrelated local changes.
- Do not push directly to `main`; use a branch and PR by default.

## Verification

Run relevant checks before final approval and report anything skipped:

```bash
# Backend
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport

# Frontend
cd frontend
npm run format
npm test
npm run lint
npm run e2e
```

If the change touches cross-service behavior, also run `./run-e2e-integration.sh --down` from the repo root.

## Local Cautions

- For UI changes, verify behavior with Playwright/browser tooling when practical and capture evidence for meaningful visual or flow changes.

## Core Principles

- Keep it simple (KISS)
- Avoid duplication (DRY)
- Don't implement speculative features (YAGNI)
- Follow SOLID where it improves maintainability
- Prefer readability over cleverness
- Prefer composition over inheritance
- High cohesion, low coupling
- Fail fast on invalid input
- Small, focused functions
- Minimize dependencies
- Refactor instead of accumulating technical debt
