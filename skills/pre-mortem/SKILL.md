---
name: pre-mortem
description: "Run a pre-mortem for a planned feature, refactor, CI change, or release in manga-tracker. Use this skill when the user wants to identify what could go wrong before implementation, especially for database, scraper, CI/CD, notifications, or cross-service changes."
user-invocable: true
---

# Pre-Mortem

Identifies likely failure modes before work starts, so risky changes can be scoped and mitigated early.


## The Job

1. Define the initiative being analyzed
2. Assume it failed badly after release or merge
3. List the most plausible causes
4. Convert those causes into mitigations, checks, and sequencing advice
5. Save the result to `tasks/pre-mortem-[topic].md` when useful

Do NOT fix or implement anything unless the user separately asks for that.


## Focus Areas For This Repo

Always consider:

- **Backend startup risk**
  - datasource config
  - Flyway/JPA validation mismatch
  - mail or actuator health checks
- **Frontend/API integration risk**
  - same-origin vs cross-origin assumptions
  - environment config drift
  - Playwright mismatch with real behavior
- **Scraper risk**
  - brittle selectors
  - upstream site changes
  - timeouts and retries
- **Notification risk**
  - invalid email handling
  - SMTP behavior differences between local, CI, and prod
- **CI/CD risk**
  - invalid workflow YAML
  - branch protection mismatch
  - flaky E2E or integration sequencing
- **Data risk**
  - migration safety
  - defaults and nullability
  - existing data compatibility


## Analysis Structure

For each risk, capture:

- **Failure mode**: What goes wrong
- **Cause**: Why it would happen
- **Impact**: User or delivery consequence
- **Early signal**: What would warn us before release
- **Mitigation**: What to change in scope, tests, rollout, or design


## Output Format

```markdown
# Pre-Mortem — [Topic]

## Scenario
Assume this change shipped and failed. What happened?

## Top Failure Modes
### 1. [Failure mode]
- Cause:
- Impact:
- Early signal:
- Mitigation:

## Required Safeguards
- [ ] Test or validation to add
- [ ] Sequencing or rollout adjustment
- [ ] Monitoring or alerting signal

## Recommendation
- Safe to proceed:
- Proceed only if:
- Defer if:
```


## Output

- **Format:** Markdown (`.md`)
- **Location:** `tasks/`
- **Filename:** `pre-mortem-[topic].md`
