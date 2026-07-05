# manga-tracker — Agent Entrypoint

This is the compact OpenCode/Codex entrypoint for this repository.

Read these in order:

1. `docs/agent-workflow.md` (shared rules for both agents)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and API concepts; use Swagger for endpoint contracts)
4. `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Agent Setup

- Reusable dual-agent workflow templates and shared review skills live at `https://github.com/matBentes/agent-workflows`.
- Install `/dual-opus`, `/dual-gpt`, OpenSpec command bootstrap files, and `thermo-nuclear-code-quality-review` from that repo; do not vendor generated command/skill artifacts here.
- `openspec/` is vendored and canonical. Use `docs/agent-workflow.md` for planning, review criteria, and archive rules.

## Operating Defaults

- Plan before changing multiple files, architecture, database schema, or API contracts.
- For small, clear fixes, keep the plan brief and implement directly.
- Never edit existing Flyway migrations; add a new migration instead.
- Do not revert unrelated local changes.
- Do not push directly to `main`; use a branch and PR by default.
- Report checks run and any checks skipped before finalizing.

## Codex Executor And Reviewer Role

When work is delegated from Claude/Fable through the Codex plugin or a similar handoff, treat the delegation prompt as the task contract.

- Execute only the delegated task and keep changes within the stated scope.
- Prefer the smallest safe patch; avoid opportunistic refactors, new dependencies, or architecture changes unless explicitly requested.
- Preserve unrelated local changes and never revert work you did not make.
- Do not run `git push`, deploy, or perform destructive repository operations.
- Never edit/delete existing Flyway migrations; add a new migration if schema changes are explicitly in scope.
- Run relevant checks when feasible and report any skipped checks with the reason.
- Return summary, files changed, commands run, checks passed, checks skipped, risks, and blockers.
- For review tasks, stay read-only and report findings first, ordered by severity, with file/line references when possible.
- For security/safety review tasks, explicitly check secrets, auth/session/CSRF regressions, authorization bypass, injection, XSS, SSRF, path traversal, dependency risk, sensitive logging, destructive operations, data loss, existing Flyway migration edits, environment/config leakage, and production deploy risk.
- If the task contract conflicts with repository rules, stop and report the conflict instead of guessing.
