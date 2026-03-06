# manga-tracker — Agent Briefing (Codex + Claude)

This repository is configured to work with both Codex and Claude Code.

Read these in order:
1. `docs/agent-workflow.md` (shared rules for both agents)
2. `docs/developer-guide.md` (project structure, tests, quality gates)
3. `docs/architecture.md` and `docs/api.md` (system behavior and contracts)

## Codex Notes

- Use `AGENTS.md` as the entrypoint (this file).
- Project-specific reusable workflows live in `skills/`.
- Community-installed skills live in `.agents/skills/`.

### Codex Operating Profile

Use this default behavior in this repository:

1. Plan-first for non-trivial tasks:
   - Before editing multiple files, changing architecture, or touching DB schema/API contracts, first present a short plan.
2. Implementation checkpoints:
   - After exploration, summarize findings before edits.
   - After edits, report exactly what changed and which checks ran.
3. Review-before-finalize:
   - For feature or refactor work, run a project-convention review pass before closing.
4. Safety defaults:
   - Do not modify existing Flyway migrations.
   - Do not revert unrelated local changes.
   - Call out assumptions and any checks not executed.

### Prompt Shortcuts (Codex)

- `plan first`: forces planning before any edits.
- `implement now`: skip planning and execute directly.
- `review this`: run a findings-first review on current changes.
- `tech debt scan`: run the project tech debt workflow.

## Claude Notes

- Claude-specific quickstart remains in `CLAUDE.md`.
- `CLAUDE.md` should stay aligned with `docs/agent-workflow.md`.
