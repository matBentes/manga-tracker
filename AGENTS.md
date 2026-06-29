# manga-tracker — Agent Briefing (Codex + Claude)

This repository is configured to work with both Codex and Claude Code.
Reusable dual-agent workflow templates and shared review skills live outside this
repo at `https://github.com/matBentes/agent-workflows`.

Read these in order:
1. `docs/agent-workflow.md` (shared rules for both agents)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and contracts)
4. `docs/github-operations.md` (rulesets, required checks, autofix, merge flow)

## Codex Notes

- Use `AGENTS.md` as the entrypoint (this file).
- Reusable workflow commands and shared skills should be installed from `matBentes/agent-workflows`, not committed here.
- Community-installed skills live outside this repo unless explicitly needed for project behavior.
- Recommended split: Claude for PRD/scope and independent second review, Codex for implementation/testing/fixes.

### Agent Workflow Setup

- Keep agent workflow commands, OpenSpec bootstrap files, and shared review skills local/global.
- Use `matBentes/agent-workflows` as the source of truth for `/dual-opus`, `/dual-gpt`, and `thermo-nuclear-code-quality-review`.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, or `openspec/` setup artifacts unless the team explicitly decides to vendor them.

### Codex Operating Profile

Use this default behavior in this repository:

1. Plan-first for non-trivial tasks:
   - Before editing multiple files, changing architecture, or touching DB schema/API contracts, first present a short plan.
   - For medium/large features, use `/prd` to define scope, then use `tasks/plan-template.md` for the implementation handoff and review contract.
   - For small, clear bug fixes, a task plan is enough; do not force a PRD.
2. Implementation checkpoints:
   - After exploration, summarize findings before edits.
   - After edits, report exactly what changed and which checks ran.
3. Review-before-finalize:
   - For feature or refactor work, run a self-review before closing.
   - In the two-agent flow, expect an independent second review before anything is considered ready.
4. Safety defaults:
   - Do not modify existing Flyway migrations.
   - Do not revert unrelated local changes.
   - Do not push directly to `main` (blocked by `.githooks/pre-push` unless explicitly overridden).
   - Call out assumptions and any checks not executed.

### Prompt Shortcuts (Codex)

- `plan first`: forces planning before any edits.
- `implement now`: skip planning and execute directly.
- `review this`: run a findings-first self-review on current changes before the second agent checks them.
- `tech debt scan`: run the project tech debt workflow.

## Claude Notes

- Claude-specific quickstart remains in `CLAUDE.md`.
- `CLAUDE.md` should stay aligned with `docs/agent-workflow.md`.
