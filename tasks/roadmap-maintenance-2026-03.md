# Outcome Roadmap — Maintenance (March 2026)

## Goal For The Period
- Make manga-tracker reliable to operate as a solo-maintained project with low-friction CI, stable required checks, and predictable automation.

## Now
### Outcome 1: Solo Delivery Without Process Friction
- Why it matters:
  - The current repo process still assumes a second reviewer even when the project is effectively solo-maintained.
  - This creates repeated merge friction without increasing quality.
- Success signal:
  - PRs are blocked only by real quality gates, not by artificial review requirements.
- Supporting initiatives:
  - Revisit the `Protect main` ruleset and decide whether required approval should remain.
  - Document when bypass is acceptable for solo maintenance.
  - Keep required checks limited to real quality signals.

### Outcome 2: CI And Security Checks Are Trustworthy
- Why it matters:
  - Recent failures came from workflow/ruleset drift, not product code.
  - CI only adds value if check names, workflows, and ruleset requirements stay aligned.
- Success signal:
  - Required checks consistently match real workflow outputs on PRs and `main`.
  - `Security Review` remains green and visible through CodeQL job checks.
- Supporting initiatives:
  - Freeze the current required check set:
    - `Format & Lint`
    - `Tests`
    - `E2E Integration Tests`
    - `Markdown Lint`
    - `SonarCloud Code Analysis`
    - `CodeQL (java)`
    - `CodeQL (javascript-typescript)`
  - Add a lightweight maintenance habit to verify workflows still match rulesets after workflow changes.
  - Avoid adding new required checks unless they address a real failure mode.

## Next
### Outcome 3: Automation Can Be Trusted To Help, Not Surprise
- Why it matters:
  - `ci-autofix` and the `autofix` label flow were repaired, but they still need to prove themselves on a real future failure.
  - Automation should reduce toil, not create hidden repo behavior.
- Success signal:
  - A future failed `fix/*` PR gets `autofix` automatically and the repaired auto-fix workflow creates real jobs.
- Supporting initiatives:
  - Observe the next `fix/*` PR lifecycle.
  - Verify `pr-autofix-label.yml` auto-labels the PR.
  - If CI fails, verify `ci-autofix.yml` runs as a real workflow instead of failing at scheduling.
  - Decide whether `autofix` should remain opt-in via label or become broader.

### Outcome 4: Repo Workflow Ownership Is Clear
- Why it matters:
  - Skills, agent docs, and workflow ownership were previously scattered.
  - Clear boundaries reduce drift and future cleanup work.
- Success signal:
  - `skills/`, `.agents/skills/`, and GitHub workflow responsibilities remain easy to explain.
- Supporting initiatives:
  - Keep `skills/` limited to repo-owned workflows.
  - Keep `.agents/skills/` as the imported general toolbox.
  - Update docs only when repo behavior actually changes.

## Later
### Outcome 5: Product Reliability Improves After Tooling Stabilizes
- Why it matters:
  - Once governance and CI are stable, the next highest-value improvements are product reliability and scraper resilience.
- Success signal:
  - Fewer scraper-related failures and stronger confidence in core manga-tracking flows.
- Supporting initiatives:
  - Audit scraper brittleness and retry behavior.
  - Review notification reliability and settings validation paths.
  - Revisit release/deploy complexity only after CI and repo operations remain stable for multiple PR cycles.

## Dependencies
- Ruleset updates depend on your chosen solo-maintainer operating model.
- Auto-fix validation depends on a future `fix/*` PR with a real failed CI run.
- Product reliability work should follow workflow stabilization, not compete with it.

## Not In This Period
- Major architecture changes
- Broad CI expansion beyond the current stable required checks
- Complex deployment automation changes unless release frequency increases
