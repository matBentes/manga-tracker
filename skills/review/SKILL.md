---
name: review
description: "Review current branch changes for correctness, project conventions, and test coverage before pushing. Use this skill whenever the user is about to push, wants a pre-merge check, asks to review changes, or says anything like: review my changes, check before push, pre-push review, review this branch, look at my diff, anything ready to merge?, what did I miss. This is the project-specific review — it checks manga-tracker conventions (jakarta imports, inject(), Flyway, Spotless). For general SOLID/quality review use /code-review instead."
user-invocable: true
---

# Pre-Push Code Review

Reviews all changes on the current branch against `main`, checking for correctness, project conventions, and test coverage.


## The Job

1. Run `git diff main...HEAD` and `git log main..HEAD --oneline`
2. Read every changed file in full (not just the diff — understand context)
3. Check against the review criteria below
4. For each failure, explain what's wrong, where (file:line), and how to fix it
5. Output an actionable checklist with pass/fail for each item

Be critical — catch issues BEFORE CI and human reviewers see them.

## Review Criteria

### Correctness
- [ ] Logic is correct — no off-by-one errors, null pointer risks, or race conditions
- [ ] Edge cases are handled (empty lists, null values, missing data)
- [ ] Error handling is appropriate (not swallowing exceptions, meaningful error messages)

### Project Conventions
- [ ] Java imports use `jakarta.persistence.*`, not `javax.persistence.*`
- [ ] Angular services use `inject()`, not constructor injection
- [ ] New database fields have a corresponding Flyway migration
- [ ] No existing Flyway migrations were modified or deleted
- [ ] Java code is formatted with Spotless (`./gradlew spotlessApply`)
- [ ] Frontend code is formatted with Prettier (`npm run format`)

### Test Coverage
- [ ] New backend logic has unit tests
- [ ] New API endpoints have integration tests
- [ ] UI changes have Playwright E2E coverage or were browser-verified
- [ ] Existing tests still pass with the changes

### Security
- [ ] No hardcoded credentials, API keys, or secrets
- [ ] No SQL injection vectors (parameterized queries used)
- [ ] No XSS vectors in frontend (Angular sanitization not bypassed)
- [ ] No sensitive data logged or exposed in error messages

### Code Quality
- [ ] No unnecessary complexity — simplest solution that works
- [ ] No dead code or commented-out code left behind
- [ ] Names are clear and consistent with existing codebase
- [ ] No duplicate logic that should be extracted


## Output Format

Use the checklist categories above as sections. Mark each item `[x]` pass, `[ ] **FAIL**` with `file:line` and explanation, or skip if N/A. End with a numbered **Action Items** list split into `[Must fix]` and `[Suggestion]`.
