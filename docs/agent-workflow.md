# Agent Workflow (Codex + Claude)

Shared operating rules for AI agents in this repository.

## Source Of Truth

- Product/docs: `README.md`, `docs/architecture.md`, `docs/api.md`
- Engineering conventions: `docs/developer-guide.md`
- Agent-specific wrappers:
  - Codex: `AGENTS.md`
  - Claude: `CLAUDE.md`
- Reusable dual-agent workflows: `https://github.com/matBentes/agent-workflows`

## Practical Split (Recommended)

Use this default split for faster delivery with clearer responsibilities:

1. Claude: idea shaping, proposal creation, and story breakdown.
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

## Reusable Workflow Setup

- Keep reusable commands, OpenSpec bootstrap files, and shared review skills local/global.
- Use `matBentes/agent-workflows` as the source of truth for `/dual-opus`, `/dual-gpt`, and `thermo-nuclear-code-quality-review`.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, `openspec/`, or `skills/` artifacts unless the team explicitly decides to vendor them.
- Use the minimum workflow or skill needed for the task; avoid broad, unfocused runs.

## Planning Artifacts

- Use `/dual-opus propose` for medium/large features, ambiguous scope, or multi-story work when the external workflow is installed.
- Use `tasks/plan-template.md` only when a local implementation handoff artifact is needed.
- Task plans should reference the approved proposal/plan and the specific story IDs or requirements in scope.
- Task plans should also record manual verification and review evidence when the task needs them.
- By default, keep task plans, fix docs, and review records in a local untracked path such as `.local/agent-artifacts/`; only commit them when explicitly requested.
- Small bug fixes or obvious refactors can skip a durable planning artifact if the scope is already clear.

## Canonical Handoff

For medium/large work, use this explicit handoff:

1. Claude creates or updates the proposal/plan artifact unless the user explicitly wants only chat-based planning.
2. The plan is reviewed and approved before implementation starts.
3. Claude or the user creates a local task artifact from `tasks/plan-template.md` when a durable handoff is needed.
4. OpenCode/Codex implements from the approved plan, not ad hoc chat context.
5. OpenCode/Codex runs the implementer self-review and records relevant evidence.
6. Claude runs `/dual-opus review-impl` against the plan and implementation.
7. Record the final review outcome in the same local artifact (`## Agreement`: implementer verdict, independent verdict, final status).
8. Any fix requires both agents to review again before push and update the agreement record.

## Collaboration Rules

- Prefer small, reviewable commits.
- In the two-agent flow, require both an implementer self-review and an independent second review before push.
- Record both reviews and the final agreement in the local task artifact before push unless the user explicitly wants it committed.
- If the two reviews disagree, stop and reconcile the disagreement before fixing or pushing.
- Direct pushes to `main` are blocked by `.githooks/pre-push`; use branch + PR by default.
- Do not revert unrelated local changes.
- Call out assumptions and any unexecuted checks.
- For UI changes, validate behavior with Playwright and capture evidence when relevant.
