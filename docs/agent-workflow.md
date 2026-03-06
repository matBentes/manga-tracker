# Agent Workflow (Codex + Claude)

Shared operating rules for AI agents in this repository.

## Source Of Truth

- Product/docs: `README.md`, `docs/architecture.md`, `docs/api.md`
- Engineering conventions: `docs/developer-guide.md`
- Agent-specific wrappers:
  - Codex: `AGENTS.md`
  - Claude: `CLAUDE.md`

## Practical Split (Recommended)

Use this default split for faster delivery with clearer responsibilities:

1. Claude: idea shaping, PRD creation, and story breakdown.
2. Codex: implementation, test execution, fixes, and commit/push flow.
3. Either agent: final review pass against project conventions before merge.

## Non-Negotiable Conventions

- Use `jakarta.persistence.*` only (never `javax.persistence.*`)
- Keep `spring.jpa.hibernate.ddl-auto=validate` compatible with schema
- Never edit/delete existing Flyway migrations; add new `V{n}__*.sql` files
- Angular DI must use `inject()` (no constructor injection)
- Run formatter/lint/tests before finalizing changes

## Required Verification Commands

```bash
# Backend
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport

# Frontend
cd frontend
npm run format
npm run lint
npm run e2e
```

If the change touches cross-service behavior, also run:

```bash
./run-e2e-integration.sh --down
```

## Skills

- Project skills: `skills/` (`prd`, `ralph`, `review`, `techdebt`)
- Community skills: `.agents/skills/`
- Use the minimum set of skills needed for the task; avoid broad, unfocused runs.

## Collaboration Rules

- Prefer small, reviewable commits.
- Direct pushes to `main` are blocked by `.githooks/pre-push`; use branch + PR by default.
- Do not revert unrelated local changes.
- Call out assumptions and any unexecuted checks.
- For UI changes, validate behavior with Playwright and capture evidence when relevant.

Emergency override (explicit and intentional only):

```bash
ALLOW_MAIN_PUSH=1 git push origin main
```
