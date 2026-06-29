# Human + AI Agent Workflow

Use this guide for day-to-day collaboration between the human developer, Claude Code, and OpenCode/Codex.

## Start Here

1. Read `docs/agent-workflow.md` for shared agent rules.
2. Use the compact agent entrypoint for the tool in use:
   - OpenCode/Codex: `AGENTS.md`
   - Claude Code: `CLAUDE.md`
3. Use `docs/developer-guide.md` for project structure, test commands, and quality gates.

## Reusable Workflow Source

Reusable dual-agent workflow templates and shared review skills are not vendored in this repo.

- Source of truth: `https://github.com/matBentes/agent-workflows`
- Install from there when needed: `/dual-opus`, `/dual-gpt`, and `thermo-nuclear-code-quality-review`
- During plan review, install/use Matt Pocock's `grill-with-docs` skill with `npx skills add https://github.com/mattpocock/skills --skill grill-with-docs` when available.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, or local `skills/` artifacts.
- `openspec/` is committed canonical project state for durable proposals, specs, tasks, reviews, sync, and archive output.

## Practical Split

1. Claude owns exploration, proposal shaping, plan confirmation, implementation review, final approval, sync, and archive.
2. OpenCode/Codex owns implementation, accepted fixes, PR warning fixes, and CI fixes.
3. The user resolves disagreements and approves scope changes.

## Standard Flow

When the external dual-agent workflow is installed, use this sequence:

1. Claude explores with `/dual-opus explore`.
2. Claude proposes scope with `/dual-opus propose`.
3. OpenCode reviews the plan with `/dual-gpt review-plan`, using `grill-with-docs` to challenge assumptions and missing documentation before approval.
4. Claude confirms the plan with `/dual-opus confirm-plan` after `grill-with-docs` findings are resolved or explicitly accepted.
5. OpenCode implements with `/dual-gpt apply`.
6. Claude reviews implementation with `/dual-opus review-impl` using the Review Criteria in `docs/agent-workflow.md`.
7. OpenCode applies accepted fixes with `/dual-gpt fix`, preserving the same Review Criteria.
8. Claude runs final approval with `/dual-opus final-review` against the same Review Criteria and verification evidence.
9. After PR checks and accepted review comments are handled, Claude runs `/dual-opus sync` and `/dual-opus archive` when appropriate.

For small, obvious fixes, keep the plan brief and implement directly. Still report the checks run and any checks skipped.

## PR And CI Loop

- Use `/dual-gpt pr-fix` for accepted Codex PR review comments, Sonar annotations, or non-failing PR warnings; fixes must still satisfy the Review Criteria in `docs/agent-workflow.md`.
- Use `/dual-gpt ci-fix` for failed required checks or a failed Sonar quality gate; fixes must stay within the approved plan unless the user approves a scope change.
- Do not sync/archive until PR review comments, required checks, and final Claude approval are complete.

## Local Artifacts

Keep scratch notes and temporary fix material local by default. Keep durable planning and review history in `openspec/`.

- Preferred location: `.local/agent-artifacts/`
- Use local artifacts only for private handoff notes or temporary evidence that should not become canonical history.
- If a durable handoff is useful, use the active OpenSpec change's `tasks.md` and reference the relevant requirements.

## OpenSpec Definition Of Done

Before sync/archive, confirm:

1. Proposal, design, tasks, reviews, verification evidence, and final agreement are complete for the scope.
2. `tasks.md` is fully checked or explicitly explains deferred items.
3. Canonical specs under `openspec/specs/` contain no `TBD`, TODO, or unresolved decision placeholders.
4. Required checks and skipped checks are recorded with reasons.
5. PR-only decisions are copied into the archived artifacts so the history is understandable later.

## Verification

Run relevant checks before finalizing work.

### Backend

```bash
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport
./gradlew jacocoTestCoverageVerification
```

### Frontend

```bash
cd frontend
npm run format
npm test
npm run lint
npm run e2e
```

If the change touches cross-service behavior, also run `./run-e2e-integration.sh --down` from the repo root.

## Safety Rules

- Do not edit or delete existing Flyway migrations; add a new migration.
- Do not revert unrelated local changes.
- Do not push directly to `main`; use a branch and PR by default.
- For UI changes, verify behavior with Playwright/browser tooling when practical.
