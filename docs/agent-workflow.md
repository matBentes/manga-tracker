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

Run the relevant checks from `docs/developer-guide.md#running-tests` and report anything skipped.
Common full-suite commands:

```bash
# Backend
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport jacocoTestCoverageVerification

# Frontend
cd frontend
npm run format:check
npm test                         # Vitest unit tests
npm run lint
npm run build -- --configuration production
npm run e2e
```

If the change touches cross-service behavior, also run:

```bash
./run-e2e-integration.sh --down
```

## Reusable Workflow Setup

- Keep reusable commands and shared review skills local/global.
- Use `matBentes/agent-workflows` as the source of truth for `/dual-opus`, `/dual-gpt`, and `thermo-nuclear-code-quality-review`. Its `docs/fable-codex-workflow.md` is the current default executor flow (Codex plugin); `/dual-gpt` is the OpenCode variant.
- During plan review, install/use Matt Pocock's `grill-with-docs` skill with `npx skills add https://github.com/mattpocock/skills --skill grill-with-docs` when available. Use it to challenge assumptions, missing docs, unclear requirements, and plan/spec gaps before implementation starts.
- Do not commit generated `.claude/commands/`, `.claude/skills/`, `.opencode/`, or `skills/` artifacts.
- `openspec/` is committed canonical project state. Use it for durable proposals, specs, tasks, review records, sync, and archive output.
- Use the minimum workflow or skill needed for the task; avoid broad, unfocused runs.

## Planning Artifacts

- Use OpenSpec proposals/specs/tasks for medium/large features, ambiguous scope, API contracts, architecture changes, database changes, or multi-story work.
- Use `/dual-opus propose` when the external workflow is installed; otherwise create the OpenSpec change manually and keep the same structure.
- Use `.local/agent-artifacts/` only for scratch notes, temporary fix docs, or private handoff material that should not become canonical history.
- Task plans should reference the approved OpenSpec change and the specific story IDs or requirements in scope.
- Task plans should record manual verification and review evidence when the task needs them.
- Small bug fixes or obvious refactors can skip a durable planning artifact if the scope is already clear.

## OpenSpec Definition Of Done

Before an OpenSpec-backed change is considered complete:

1. `proposal.md` states why the change exists, what changes, non-goals, impact, risks, and any human decisions.
2. `design.md` records architecture decisions, constraints, trade-offs, migration/rollback notes, and resolved open questions when the change is non-trivial.
3. `tasks.md` is fully checked or explicitly explains any unchecked item and why it remains deferred.
4. Capability specs under `openspec/specs/` contain no placeholders such as `TBD` or unresolved TODOs after sync/archive.
5. Implementer and independent review findings are recorded, including the final agreement status.
6. Required verification commands and any skipped checks are recorded with the reason.
7. PR-only decisions or sign-off notes are captured before archive, so archived artifacts remain understandable without the PR page.
8. Archive/sync happens only after accepted review comments, required checks, and final approval are complete.

## Canonical Handoff

For medium/large work, use this explicit handoff:

1. Claude creates or updates the OpenSpec proposal/plan artifact unless the user explicitly wants only chat-based planning.
2. The plan is reviewed with `grill-with-docs` and approved before implementation starts.
3. Claude or the user ensures the OpenSpec `tasks.md` is specific enough for handoff; use `.local/agent-artifacts/` only for scratch implementation notes.
4. OpenCode/Codex implements from the approved OpenSpec change, not ad hoc chat context.
5. OpenCode/Codex runs the implementer self-review and records relevant evidence.
6. Claude runs `/dual-opus review-impl` against the plan, implementation, and Review Criteria below.
7. Record the final review outcome in the OpenSpec review artifacts (`## Agreement`: implementer verdict, independent verdict, final status).
8. Any fix requires both agents to review again before push and update the agreement record.

## Review Criteria

Use these criteria for implementer self-review, independent implementation review, final review, PR warning fixes, and CI fixes. They are the canonical form of this repo's core principles: KISS, DRY, YAGNI, useful SOLID, readable code, high cohesion, low coupling, fail-fast input handling, minimal dependencies, and active technical-debt control.

1. Plan/spec alignment: the implementation satisfies the approved OpenSpec change, task scope, non-goals, and accepted review decisions without adding speculative behavior.
2. Behavioral correctness: user-visible behavior, API contracts, persistence behavior, and error handling match the documented requirements and existing architecture.
3. Simplicity: the solution is as small as practical, avoids unnecessary abstractions, and keeps the implementation understandable.
4. DRY and cohesion: duplicated logic is avoided where it would create maintenance risk, and each changed unit has a clear responsibility with low coupling.
5. SOLID where useful: composition is preferred over inheritance, boundaries are clear, and abstractions exist only when they improve maintainability.
6. Readability: code favors clear names and straightforward control flow over cleverness; comments explain non-obvious decisions only.
7. Safety: invalid input fails fast, security-sensitive behavior is preserved or explicitly approved, data/schema changes are safe, and existing Flyway migrations are not edited.
8. Minimal dependencies: new dependencies are justified by the plan and do not replace simple local code without clear benefit.
9. Tests and verification: relevant automated tests, formatters, linters, smoke checks, and skipped-check reasons are recorded with evidence.
10. Technical debt: the change leaves touched code at least as maintainable as before and does not hide known follow-up work.
11. Static-analysis triage: as part of review, pull and read the SonarCloud (and CodeQL) findings for the PR — not just the pass/fail check status. A **passing** Quality Gate can still carry new below-threshold issues, so a green check is not the same as zero findings. Open the findings, and for each decide fix-now vs. defer-with-reason; record that decision.

## Collaboration Rules

- Prefer small, reviewable commits.
- In the two-agent flow, require both an implementer self-review and an independent second review before push.
- Record both reviews and the final agreement in the OpenSpec review artifacts before push unless the user explicitly chooses a local-only review record.
- If the two reviews disagree, stop and reconcile the disagreement before fixing or pushing.
- Direct pushes to `main` are blocked by `.githooks/pre-push`; use branch + PR by default.
- Do not revert unrelated local changes.
- Call out assumptions and any unexecuted checks.
- For UI changes, validate behavior with Playwright and capture evidence when relevant.
