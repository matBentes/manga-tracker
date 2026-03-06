# GitHub Operations

Operational reference for pull requests, required checks, and repo automation.

## Main Ruleset

`main` is protected by a GitHub ruleset. The exact settings live in GitHub, but the intended
operating model for this repository is:

- require the configured status checks before merge
- allow `Required approvals = 0` for solo-maintainer flow
- keep direct pushes to `main` blocked locally by `.githooks/pre-push` unless intentionally
  overridden

Repository documentation should match the active ruleset. If the ruleset changes, update this file
and any linked references in `docs/developer-guide.md`, `AGENTS.md`, and `CLAUDE.md`.

## Required Checks

The checks currently expected on pull requests are:

- `Format & Lint`
- `Tests`
- `E2E Integration Tests`
- `Markdown Lint`
- `SonarCloud Code Analysis`
- `CodeQL (java)`
- `CodeQL (javascript-typescript)`

`Build & Deploy` runs on pushes to `main` and is not part of normal PR merge gating.

## Merge Flow

Expected default flow:

1. create a feature branch
2. open a pull request
3. let required checks pass
4. merge after checks are green

For solo-maintainer work, approvals may be set to `0`, so green checks are the primary merge gate.

## Bypass Guidance

Bypass is acceptable only when:

- the required checks are already green
- the remaining blocker is a ruleset artifact that does not reflect the actual team model
- the bypass is intentional and understood

Bypass is not a substitute for failing checks.

## Auto-fix

Two workflows support PR automation:

- `.github/workflows/pr-autofix-label.yml`
  - auto-adds the `autofix` label to same-repo, non-draft `fix/*` pull requests
- `.github/workflows/ci-autofix.yml`
  - runs after a failed `CI` workflow on a pull request
  - only proceeds when the PR already has the `autofix` label
  - attempts a minimal Codex-based fix and pushes it to the PR branch if changes are produced

`CI Auto Fix` also requires the repository secret `OPENAI_API_KEY`. Without that secret, the
workflow still runs but skips the actual Codex edit step and comments that the secret is missing.

## Operating Checks

When GitHub behavior looks wrong, verify these in order:

1. workflow file exists on `main`
2. workflow YAML is valid
3. required check names in the ruleset match the actual emitted check contexts
4. required secrets exist
5. PR labels or branch naming conditions match the workflow logic

This repository has already hit all five failure modes. Use this list before assuming the code is
the problem.
