# Human + AI Agent Workflow

Use this guide for day-to-day collaboration between the human developer, Claude Code, and OpenCode/Codex.

## Start Here

1. Read `docs/agent-workflow.md` for shared agent rules.
2. Use the compact agent entrypoint for the tool in use:
   - OpenCode/Codex: `AGENTS.md`
   - Claude Code: `CLAUDE.md`
3. Use `docs/developer-guide.md` for project structure, test commands, and quality gates.

## Source Of Truth

- Detailed agent rules: `docs/agent-workflow.md`
- Reusable commands/skills: `https://github.com/matBentes/agent-workflows`
- Durable planning/review history: `openspec/`

## Practical Split

1. Claude owns exploration, proposal shaping, plan confirmation, implementation review, final approval, sync, and archive.
2. OpenCode/Codex owns implementation, accepted fixes, PR warning fixes, and CI fixes.
3. The user resolves disagreements and approves scope changes.

## Standard Flow

When the external dual-agent workflow is installed:

1. Claude explores with `/dual-opus explore`.
2. Claude proposes scope with `/dual-opus propose`.
3. OpenCode reviews the plan with `/dual-gpt review-plan` using `grill-with-docs`.
4. Claude confirms the plan with `/dual-opus confirm-plan`.
5. OpenCode implements with `/dual-gpt apply`.
6. Claude reviews implementation with `/dual-opus review-impl` using `docs/agent-workflow.md#review-criteria`.
7. OpenCode applies accepted fixes with `/dual-gpt fix`.
8. Claude runs final approval with `/dual-opus final-review`.
9. After PR checks and accepted review comments are handled, Claude runs `/dual-opus sync` and `/dual-opus archive`.

For small, obvious fixes, keep the plan brief and implement directly. Still report the checks run and any checks skipped.

## PR And CI Loop

- Use `/dual-gpt pr-fix` for accepted Codex PR review comments, Sonar annotations, or non-failing PR warnings.
- Use `/dual-gpt ci-fix` for failed required checks or a failed Sonar quality gate.
- Do not sync/archive until PR review comments, required checks, and final Claude approval are complete.

## Local Artifacts

Keep scratch notes under `.local/agent-artifacts/`. Keep durable planning, review evidence, and archive history in `openspec/`; see `docs/agent-workflow.md#openspec-definition-of-done`.

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
