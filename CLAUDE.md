# manga-tracker — Claude Code Entrypoint

This is the compact Claude Code entrypoint for this repository.

Read these in order:

1. `docs/agent-workflow.md` (shared agent rules and dual-agent workflow)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and API concepts; use Swagger for endpoint contracts)
4. `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Project Snapshot

- Manga reading tracker: users search MangaDex and add titles, backend tracks reading progress/status and sends best-effort English-only push notifications for new chapters, frontend shows the reading dashboard.
- Stack: Spring Boot 3, Java 21, Angular 22, PostgreSQL, Docker Compose.
- Main directories: `backend/`, `frontend/`, `docs/`, `openspec/`.

## Agent Setup

- Reusable dual-agent workflow templates and shared review skills live at `https://github.com/matBentes/agent-workflows`.
- Install `/dual-opus`, `/dual-gpt`, OpenSpec command bootstrap files, and `thermo-nuclear-code-quality-review` from that repo; do not vendor generated command/skill artifacts here.
- `openspec/` is vendored and canonical. Use `docs/agent-workflow.md` for planning, review criteria, and archive rules.

## Claude Role

- Own exploration, proposal shaping, plan confirmation, implementation review, final approval, sync, and archive.
- Use `/dual-opus explore`, `/dual-opus propose`, `/dual-opus confirm-plan`, `/dual-opus review-impl`, `/dual-opus final-review`, `/dual-opus sync`, and `/dual-opus archive` when the external workflow is installed.
- Let OpenCode/GPT handle implementation, accepted fixes, PR warning fixes, and CI fixes via `/dual-gpt`.

## Fable + Codex Delegation Flow

When acting as Fable/orchestrator, default to planning, delegation, review, re-planning, and synthesis. Do not implement directly unless the user explicitly asks for direct implementation or the change is a tiny local documentation/configuration edit.

- Use Codex through the Claude Code Codex plugin for executor and review work when available.
- If Codex is unavailable (rate/usage limit hit, auth failure, or the plugin errors), fall back
  to a Claude Sonnet 5 subagent (Agent tool, `model: sonnet`) for the same executor/review
  tasks, choosing thinking effort appropriate to the task size. Keep the same delegation
  template, scope discipline, and review gates. Return to Codex when the limit resets.
- For non-trivial work, create a small, verifiable plan first, then ask Codex for adversarial plan review before implementation.
- Delegate implementation to Codex with `/codex:rescue --model gpt-5.5 --effort xhigh --background` when the task is suitable for executor handoff.
- After implementation, review the diff yourself, then run a read-only security/safety gate before final code review.
- For the security/safety gate, ask Codex to check secrets, auth/session/CSRF regressions, authorization bypass, injection, XSS, SSRF, path traversal, dependency risk, sensitive logging, destructive operations, data loss, existing Flyway migration edits, environment/config leakage, and production deploy risk.
- After the security/safety gate, ask Codex for adversarial code review with `/codex:adversarial-review --model gpt-5.5 --effort xhigh --background`.
- Keep plan review and code review tasks read-only; implementation tasks may edit only the approved scope.
- Do not run multiple editing executors against the same files concurrently.
- Incorporate only actionable findings; reject speculative complexity and keep the smallest correct patch.
- If Fable and Codex disagree, stop, reconcile the disagreement, and only then continue or ask the user.
- Summarize final outcome with changed files, checks run, checks skipped with reasons, risks, and any remaining blockers.

Use this delegation shape for Codex tasks:

```text
TASK:
<specific objective>

CONTEXT:
<approved plan, relevant files, docs, constraints>

SCOPE:
<what may change>

OUT OF SCOPE:
<what must not change, including git push, deploy, unrelated refactors, and editing existing Flyway migrations>

SUCCESS CRITERIA:
<verifiable criteria>

REQUIRED OUTPUT:
- Summary
- Files changed
- Commands run
- Checks passed
- Checks skipped with reason
- Risks
- Blockers
```

Use this shape for the security/safety gate:

```text
/codex:rescue --model gpt-5.5 --effort xhigh --background TASK: Perform a security and safety review of the current diff. Do not edit files. CONTEXT: Review only the current uncommitted changes against repository rules. CHECK: secrets, auth/session/CSRF regressions, authorization bypass, unsafe deserialization, SQL injection, XSS, SSRF, path traversal, dependency risk, logging sensitive data, destructive operations, data loss, existing Flyway migration edits, environment/config leakage, and production deploy risk. REQUIRED OUTPUT: findings first, ordered by severity, with file/line references when possible; include explicit PASS if no finding is found.
```

## Non-Negotiables

- Use `jakarta.persistence.*`, never `javax.persistence.*`.
- Keep `spring.jpa.hibernate.ddl-auto=validate` compatible with Flyway schema.
- Never edit/delete existing Flyway migrations; add a new `V{n}__*.sql` migration.
- Angular DI must use `inject()` rather than constructor injection.
- Do not revert unrelated local changes.
- Do not push directly to `main`; use a branch and PR by default.

## Verification

Run relevant checks from `docs/developer-guide.md#running-tests` before final approval and report
anything skipped. Common full-suite commands:

```bash
# Backend
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport jacocoTestCoverageVerification

# Frontend
cd frontend
npm run format:check
npm test
npm run lint
npm run build -- --configuration production
npm run e2e
```

If the change touches cross-service behavior, also run `./run-e2e-integration.sh --down` from the repo root.

## Local Cautions

- For UI changes, verify behavior with Playwright/browser tooling when practical and capture evidence for meaningful visual or flow changes.
