# Task Plan — <Title>

## Goal
- Describe the user-visible outcome in one or two lines.

## Context
- Why this work exists now.
- Current behavior, bug, or gap.
- Any repo-specific background the implementing agent must know first.

## Scope
### In Scope
- List the concrete changes expected in this task.

### Out Of Scope
- List nearby changes that should not be made as part of this task.

## Constraints And Decisions
- Record design decisions that should not be changed silently during implementation.
- Call out version, library, API, schema, or UX choices that reviewers should enforce.
- Note repo conventions that matter for this task.

## Expected Files
- `path/to/file` — what should change and why
- `path/to/other-file` — what should change and why

## Implementation Plan
1. Describe the first implementation step.
2. Describe the second implementation step.
3. Describe the third implementation step.

## Risks
- List the likely failure modes or compatibility traps.
- Call out anything that should trigger extra review attention.

## Verification

**Success commands:**
- `cd <dir> && <command>`
- `cd <dir> && <command>`

**Quality gates:**
- `backend`
- `frontend`

**Max fix attempts:**
- `3`

**Watch targets:**
- `path/to/file` — what must be true after implementation
- `path/to/file` — what reviewers should check carefully

## Review Gate

**Implementer review:**
- Codex runs `review this`

**Independent review:**
- Claude runs `/supervise`

**Agreement rule:**
- `agree-pass` = both reviews say ready
- `agree-fail` = both reviews say blockers remain
- `disagree` = reviewers differ materially; stop and reconcile before fixing or pushing

**Fix owner:**
- original implementer unless the user redirects

**Re-review trigger:**
- Any fix after review requires both agents to review again

## Acceptance Checklist
- [ ] Main functional outcome works
- [ ] Design decisions above were preserved
- [ ] Verification commands pass
- [ ] Both reviews reach `agree-pass`

## Handoff Notes
- Anything the implementer should tell the reviewer explicitly.
- Assumptions, skipped checks, or environment caveats.
