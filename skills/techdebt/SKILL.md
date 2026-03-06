---
name: techdebt
description: "Scan the manga-tracker codebase for tech debt, inconsistencies, and quality issues. Use this skill after Ralph completes autonomous runs (to catch drift), before starting a refactoring effort, or on a periodic basis. Also use when the user asks things like: tech debt scan, find tech debt, code quality check, what needs cleaning up, any issues in the codebase, health check, what's broken, audit the code."
user-invocable: true
---

# Tech Debt Scanner

Scans the manga-tracker codebase for common tech debt patterns and outputs a prioritized report.


## The Job

1. Run all checks below against the current codebase
2. Categorize findings by severity (Critical / Warning / Info)
3. Save report to `tasks/techdebt-YYYY-MM-DD.md`
4. Print a summary to the conversation

Do NOT fix anything — just report findings. The value of this skill is giving a clear picture of what needs attention, so the user can prioritize. Fixing things without asking risks breaking something or wasting effort on low-priority items.


## Checks to Run

### Critical (breaks CI or runtime)

1. **`javax` imports** — Search for `javax.persistence` in all Java files. Must be `jakarta.persistence`.
   ```
   Grep for: javax\.persistence in backend/src/
   ```

2. **Migration integrity** — Check that Flyway migrations are sequential (`V1`, `V2`, `V3`, ...) with no gaps or duplicates.
   ```
   List files in: backend/src/main/resources/db/migration/
   Verify: version numbers are sequential, no gaps
   ```

3. **Constructor injection in Angular** — Search for constructor-based DI instead of `inject()`.
   ```
   Grep for: constructor\(.*private in frontend/src/ (*.ts files)
   ```

### Warning (quality / consistency issues)

4. **TODO/FIXME comments** — Find all TODO, FIXME, HACK, XXX comments across the codebase.
   ```
   Grep for: TODO|FIXME|HACK|XXX in backend/src/ and frontend/src/
   ```

5. **Test coverage gaps** — Check for backend source files without corresponding test files.
   ```
   Compare: backend/src/main/java/**/*.java against backend/src/test/java/**/*.java
   ```
   Frontend coverage is checked via Playwright E2E — no 1:1 component-to-test mapping expected.

6. **Unused imports** — Look for obvious unused imports in TypeScript files.
   ```
   Run: cd frontend && npm run lint (check output for unused imports)
   ```

### Info (housekeeping)

7. **Large files** — Flag any source files over 300 lines (potential candidates for splitting).

8. **Inconsistent naming** — Check for mixed naming conventions (camelCase vs snake_case in the same layer).


## Report Format

```markdown
# Tech Debt Report — YYYY-MM-DD

## Summary
- Critical: X | Warning: Y | Info: Z

## Critical
### [Finding title]
- **File(s):** path/to/file.java:42
- **Issue:** Description
- **Fix:** What to do
```
Use the same format for Warning (with "Suggested action" instead of "Fix") and Info sections.


## Output

- **Format:** Markdown (`.md`)
- **Location:** `tasks/`
- **Filename:** `techdebt-YYYY-MM-DD.md`
