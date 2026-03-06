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
2. Codex: implementation, test execution, and the first self-review.
3. Claude: independent second review against the plan and verification commands.
4. Implementing agent: fixes agreed findings, then hands back for re-review.

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
npm test                         # Vitest unit tests
npm run lint
npm run e2e
```

If the change touches cross-service behavior, also run:

```bash
./run-e2e-integration.sh --down
```

## Skills

- Project skills: `skills/` (`prd`, `ralph`, `review`, `supervise`, `techdebt`)
- Community skills: `.agents/skills/`
- Use the minimum set of skills needed for the task; avoid broad, unfocused runs.

## Collaboration Rules

- Prefer small, reviewable commits.
- In the two-agent flow, require both an implementer self-review and an independent second review before push.
- If the two reviews disagree, stop and reconcile the disagreement before fixing or pushing.
- Direct pushes to `main` are blocked by `.githooks/pre-push`; use branch + PR by default.
- Do not revert unrelated local changes.
- Call out assumptions and any unexecuted checks.
- For UI changes, validate behavior with Playwright and capture evidence when relevant.

Emergency override (explicit and intentional only):

```bash
ALLOW_MAIN_PUSH=1 git push origin main
```
