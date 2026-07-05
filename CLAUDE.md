# manga-tracker — Claude Code Entrypoint

This is the compact Claude Code entrypoint for this repository.

Read first:

1. `docs/agent-workflow.md` (shared agent rules, review criteria, verification commands)
2. `docs/developer-guide.md` (project structure, tests, quality gates)

Read when the task touches the relevant area:

- `docs/architecture.md` and `docs/api.md` (system behavior and API concepts; use Swagger for endpoint contracts)
- `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Project Snapshot

- Manga reading tracker: users search MangaDex and add titles, backend tracks reading progress/status and sends best-effort English-only push notifications for new chapters, frontend shows the reading dashboard.
- Stack: Spring Boot 3, Java 21, Angular 22, PostgreSQL, Docker Compose.
- Main directories: `backend/`, `frontend/`, `docs/`, `openspec/`.

## Agent Setup

- Reusable dual-agent workflow templates and shared review skills live at `https://github.com/matBentes/agent-workflows`. The current default is `docs/fable-codex-workflow.md` there (Fable orchestrates, the Codex plugin executes); the OpenCode `/dual-gpt` flow is the legacy variant.
- Install `/dual-opus`, OpenSpec command bootstrap files, and `thermo-nuclear-code-quality-review` from that repo; do not vendor generated command/skill artifacts here.
- `openspec/` is vendored and canonical. Use `docs/agent-workflow.md` for planning, review criteria, and archive rules.

## Fable Role

- Default to planning, delegation, review, re-planning, and synthesis. Do not implement directly unless the user explicitly asks or the change is a tiny local documentation/configuration edit.
- Claude-side OpenSpec phases still use `/dual-opus` (`explore`, `propose`, `confirm-plan`, `review-impl`, `final-review`, `sync`, `archive`) when installed; delegate the executor side to Codex per the flow below.
- If Fable is unavailable (usage limit, capacity, or model errors), run the same orchestrator role on the strongest available Claude model — Opus 4.8 first, then Sonnet 5. Keep the same tiers, templates, and gates; a weaker orchestrator must not skip gates and should go one tier up when in doubt. Return to Fable when it is available again.

## Review Tiers

Pick the lightest tier that fits; when in doubt, go one tier up.

- Small (docs, config, or a single-file fix with no API/schema/auth surface): implement, then Claude diff review. Skip the security gate and adversarial review only when the diff touches no security-sensitive code or configuration — auth/session/CSRF, CORS, cookies, security headers, secrets, environment/deploy config, or dependency changes stay gated.
- Medium (multi-file feature or fix, no API contract or schema change): small verifiable plan + Codex adversarial plan review, delegate implementation, Claude diff review, security/safety gate, Codex adversarial code review.
- Large (API contract, database schema, architecture, or multi-story work): full OpenSpec pipeline from `docs/agent-workflow.md` plus all medium-tier gates, then sync/archive.
- Every PR: pull and triage the SonarCloud and CodeQL findings (e.g. the SonarCloud issues API for the PR), not just the check's pass/fail status — a passing Quality Gate can still carry new below-threshold issues. Decide fix-now vs. defer-with-reason for each and record it. See `docs/agent-workflow.md#review-criteria` item 11.

## Codex Delegation Flow

- Use Codex through the Claude Code Codex plugin for executor and review work.
- If Codex is unavailable (rate/usage limit hit, auth failure, or the plugin errors), fall back to a Claude Sonnet 5 subagent (Agent tool, `model: sonnet`) for the same executor/review tasks, choosing thinking effort appropriate to the task size. Keep the same delegation template, scope discipline, and review gates. Return to Codex when the limit resets.
- Delegate implementation with `/codex:rescue --model gpt-5.5 --effort xhigh --background`; run adversarial code review with `/codex:adversarial-review --model gpt-5.5 --effort xhigh --background`.
- Keep plan review, the security gate, and code review read-only; implementation tasks may edit only the approved scope.
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

Run relevant checks from `docs/developer-guide.md#running-tests` before final approval and report anything skipped. The common full-suite commands are listed in `docs/agent-workflow.md#required-verification-commands`.

If the change touches cross-service behavior, also run `./run-e2e-integration.sh --down` from the repo root.

## Local Cautions

- For UI changes, verify behavior with Playwright/browser tooling when practical and capture evidence for meaningful visual or flow changes.
