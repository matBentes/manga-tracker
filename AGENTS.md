# manga-tracker — Agent Entrypoint

This is the compact OpenCode/Codex entrypoint for this repository.

Read these in order:
1. `docs/agent-workflow.md` (shared rules for both agents)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and contracts)
4. `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Agent Setup

- Reusable dual-agent workflow templates and shared review skills live at `https://github.com/matBentes/agent-workflows`.
- Install `/dual-opus`, `/dual-gpt`, OpenSpec bootstrap files, and `thermo-nuclear-code-quality-review` from that repo; do not vendor them here.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, `openspec/`, or local `skills/` artifacts unless the team explicitly decides to vendor them.

## Operating Defaults

- Plan before changing multiple files, architecture, database schema, or API contracts.
- For small, clear fixes, keep the plan brief and implement directly.
- Never edit existing Flyway migrations; add a new migration instead.
- Do not revert unrelated local changes.
- Do not push directly to `main`; use a branch and PR by default.
- Report checks run and any checks skipped before finalizing.
