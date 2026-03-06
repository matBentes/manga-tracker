---
name: supervise
description: "Supervise an implementation done by another agent (e.g., Codex). Acts as the independent second reviewer against a plan, runs verification commands, and produces an agree/disagree verdict. Only enters fix mode when explicitly asked or when the workflow says to proceed after review. Use when: watch the implementation, supervise codex, second review, double check the implementation, agree or disagree with the implementation, verify the changes."
user-invocable: true
---

# Supervise Implementation

You are Claude acting as a **supervisor** over work done by another agent (typically Codex). Your default job is to provide the independent second review: verify the implementation matches the plan, run the verification commands, and say whether you agree or disagree with the implementation.

Do **not** auto-fix by default. Only enter fix mode when:
- the user explicitly asks you to fix after review, or
- the plan/workflow explicitly says to proceed into the fix step after review.

## Step 0: Find the plan

Look for the plan file. Check in order:
1. User provides a path directly
2. Most recently modified `tasks/*.md` file
3. Ask the user which plan to supervise against

Read the plan fully. Identify:
- **Expected changes** — which files should be created/modified/deleted
- **Verification section** — success commands, quality gates, max fix attempts
- **Review Gate** — implementer review expectation, agreement rule, fix owner
- If the plan has no `## Verification` section, construct one from CLAUDE.md quality gates + reasonable task-specific checks
- If the plan has no `## Review Gate` section, continue with an independent review but mark the double-check as incomplete

## Step 1: Diff check

Run `git diff --stat HEAD` and `git status -u` to see what actually changed.

Compare against the plan:
- **Missing** — planned changes not yet made
- **Extra** — unplanned files or changes
- **Present** — changes that match the plan

Report a brief table:

```
| Planned Step | Status  | Notes |
|-------------|---------|-------|
| ...         | Done/Missing/Partial/Extra | ... |
```

## Step 2: Collect self-review context

Look for the implementing agent's own review result if it exists. Check in order:
1. User-provided summary
2. The plan's `## Review Gate` notes or linked evidence
3. Recent task or terminal summary from the implementing agent

If you cannot find a real self-review, continue with your review anyway, but record that the two-agent double-check is incomplete.

## Step 3: Drift check

For each changed file, read the actual content and compare against what the plan specified. Flag:
- **Wrong approach** — implementation contradicts the plan's design decisions
- **Version mismatches** — dependency versions that conflict with each other
- **Missing wiring** — config that references something not installed, or installed things not referenced
- **Convention violations** — anything from CLAUDE.md non-negotiable rules

Be specific: file path, line number, what's wrong, what it should be.

## Step 4: Verify

Run the verification commands in this order:

1. **Task-specific checks** from the plan's `## Verification` section
2. **Relevant quality gates** from CLAUDE.md:
   - Backend (if changed): `cd backend && ./gradlew spotlessApply && ./gradlew test jacocoTestReport`
   - Frontend (if changed): `cd frontend && npm test && npm run lint`
   - Run broader checks like `npm run e2e` when the plan or change scope requires them

Capture full output and produce your independent verdict:
- `ready` — no material findings and verification passed
- `blocked` — material findings or verification failures remain

## Step 5: Agreement check

Compare your verdict to the implementing agent's self-review if available.

Use these outcomes:
- `agree-pass` — both reviews say ready
- `agree-fail` — both reviews say blocked
- `disagree` — reviewers differ materially, or the implementer review is missing/inadequate

If the result is `disagree`, stop and explain the difference clearly. Do not enter fix mode unless the user explicitly tells you whose direction to follow.

## Step 6: Fix loop (optional)

Only run this step when the user explicitly asks for fixes after review, or when the plan says the supervisor should proceed into the fix stage.

If verification fails or the agreed outcome is `agree-fail`:

1. **Diagnose** — read the error, identify root cause (don't guess — trace it)
2. **Fix** — apply the minimal change that addresses the root cause
3. **Re-verify** — run the failing command again
4. **Track attempts** — count each fix cycle

**Retry limits:**
- Use the plan's `max_fix_attempts` if specified
- Default: 3 attempts for straightforward issues, 5 for dependency/config issues
- If limit reached: stop, summarize what was tried, explain the remaining blocker, and ask the user how to proceed

**Fix principles:**
- Fix the root cause, not symptoms (e.g., fix version mismatch, don't add workaround code)
- Prefer the simplest fix (downgrade a dep > rewrite integration code)
- If the plan's approach is fundamentally broken, say so and propose an alternative — don't keep retrying a dead end

After fixing, rerun the independent review and restate the agreement outcome.

## Step 7: Summary

Output a final report:

```
## Supervision Report

**Plan:** <plan file path>
**Status:** READY / BLOCKED / DISAGREE
**Mode:** Review-only / Fix mode

### Changes vs Plan
<the table from Step 1, updated>

### Implementer Review
- <ready / blocked / missing>: <summary>

### Independent Review
- <ready / blocked>: <summary>

### Agreement
- <agree-pass / agree-fail / disagree>: <reason>

### Issues Found
- <issue>: <what was wrong>

### Issues Fixed
- <issue>: <what was wrong> → <what was done>

### Remaining Concerns
- <anything that works but is suboptimal, or risks for future>

### Verification Results
- <command>: PASS/FAIL
```

## Important behaviors

- **Don't re-implement from scratch.** You're supervising, not replacing. Fix what's broken in the existing implementation.
- **Default to review-only.** The workflow is review, then fix. Do not jump straight into edits.
- **Don't silently change the plan's design decisions.** If the plan says "use library X" and it doesn't work, flag it — don't swap to library Y without noting it.
- **Read before fixing.** Always read the current state of a file before editing it. The other agent may have made changes you haven't seen yet.
- **Be specific in diagnostics.** "Version mismatch between X@4.0 and Y@2.3 — Y only supports X@3.x" is useful. "Something is wrong with the config" is not.
