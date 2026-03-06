# Good Practices: Human + Claude Workflow

How we use Claude Code effectively on this project.

---

## Session Structure

Every Claude Code session should follow this pattern:

1. **Orient** — Claude auto-reads `CLAUDE.md` on session start. Skim it to confirm project context is loaded. For deeper context, ask Claude to read `docs/developer-guide.md`.
2. **Plan** — Use plan mode (`/plan` or default) for any non-trivial change. Claude explores the codebase, proposes an approach, and waits for approval before writing code.
3. **Implement** — Work interactively or delegate to Ralph for multi-story features.
4. **Challenge** — Before pushing, run `/review` to have Claude critically review its own changes against project conventions.
5. **Remember** — If Claude discovered a reusable pattern or made a recurring mistake, ask it to save the insight to its memory files.

---

## Feature Task Flow

For new features:

1. **`/prd`** — Generate a Product Requirements Document with clarifying questions
2. **Review the PRD** — Refine scope, acceptance criteria, and story ordering
3. **Choose execution mode:**
   - **`/ralph`** — Convert to `prd.json` and run autonomously (best for multi-story features)
   - **Interactive** — Implement stories one at a time with human review between each
4. **`/review`** — Pre-push review of all changes
5. **`/techdebt`** — Periodic scan for accumulated inconsistencies

---

## Available Skills

### Project-specific (`skills/`)

| Skill | Purpose | When to use |
|-------|---------|-------------|
| `/prd` | Generate a structured PRD | Starting a new feature |
| `/ralph` | Convert PRD to Ralph JSON format | Preparing for autonomous execution |
| `/techdebt` | Scan for tech debt and inconsistencies | After a Ralph run, or periodically |
| `/review` | Pre-push review against project conventions | Before pushing any branch |

### Installed community skills (`.agents/skills/`)

| Skill | Purpose | When to use |
|-------|---------|-------------|
| `/angular-component` | Generate Angular components | Creating new standalone components |
| `/java-springboot` | Spring Boot patterns | Adding controllers, services, entities |
| `/playwright-e2e-testing` | Playwright E2E testing | Writing or debugging E2E tests |
| `/tdd` | Test-driven development | Implementing with red-green-refactor |
| `/code-review` | General code quality review | Deep review for SOLID, patterns, bugs |
| `/security-review` | OWASP security vulnerability scan | Pre-merge security audit |
| `/backend-testing` | Backend test strategies | Writing unit/integration tests for Java |
| `/docker-expert` | Docker/Compose guidance | Dockerfile or compose changes |
| `/github-actions-cicd` | CI/CD pipeline help | Modifying GitHub Actions workflows |

> **`/review` vs `/code-review`:** `/review` checks project-specific conventions (jakarta, Flyway, inject, Spotless). `/code-review` does general quality analysis (SOLID, best practices, bugs). Use both for thorough pre-push review.

---

## Parallel Work with Worktrees

When you need to work on multiple things at once (e.g., a bug fix while a feature is in progress), use **git worktrees** instead of stashing or juggling branches:

```
/worktree
```

This creates an isolated copy of the repo in `.claude/worktrees/` with its own branch. You can run a separate Claude session in the worktree without interfering with your main work.

**When to use worktrees:**
- Bug fix while a Ralph run is in progress on the main checkout
- Exploring a different approach without losing current state
- Running two Claude sessions on independent tasks

**Note:** Ralph already handles multi-story parallelism within a feature. Worktrees are for *cross-feature* parallelism.

---

## Subagents

Claude can spawn subagents — lightweight background workers that research or explore without polluting the main conversation context. This is useful for:

- **Parallel research** — "Search for how the scraper handles timeouts" while continuing to work on something else
- **Deep exploration** — Let a subagent thoroughly explore a part of the codebase while you focus on implementation
- **Independent tasks** — Run tests, check build output, or search documentation in the background

**How to trigger:** Ask Claude to do something "in the background" or "in parallel", or it will use subagents automatically when exploring the codebase.

**Note:** Ralph's iteration model already covers autonomous multi-step execution. Subagents are most useful in interactive sessions for research and exploration.

---

## Advanced Prompting Tips

Get better results from Claude by structuring your prompts:

- **Challenge Claude's work** — After implementation, explicitly ask: "Now review what you just wrote. What did you miss? What could break?" This catches issues Claude wouldn't flag unprompted.
- **Teach Claude to remember** — When Claude discovers a codebase pattern or makes a mistake, say "Remember this for future sessions." It saves to `.claude/` memory files.
- **Be specific about scope** — "Fix the bug" is vague. "The scraping job fails when the URL returns a 403 — handle that in ScrapingJob.java" gives Claude the right context immediately.
- **Use plan mode for anything non-trivial** — Even if you think you know the approach, plan mode forces Claude to read the codebase first, which prevents it from writing code based on assumptions.
- **Reference files by path** — "Update the manga service" is ambiguous. "Update `backend/src/main/java/.../MangaService.java`" is precise.

---

## Watch Claude For These

Common mistakes Claude makes on this codebase — catch them early:

- **`javax` imports** — Claude's training data is full of `javax.persistence`. Always needs to be `jakarta.persistence` here.
- **Editing existing migrations** — Claude may try to "fix" a migration instead of creating a new one. Flyway will reject checksum mismatches.
- **Constructor injection** — Claude defaults to `constructor(private service: MyService)` instead of `inject()`. ESLint will catch it, but it wastes a cycle.
- **Missing `spotlessApply`** — Claude forgets to format Java code. Pre-commit hook catches it, but running it proactively avoids noisy diffs.
- **Overly large stories** — When using Ralph, Claude tends to create stories that are too big for one iteration. Each story should be completable in a single context window.

---

## Pre-Push Checklist

### Backend
```bash
cd backend
./gradlew spotlessApply
./gradlew test jacocoTestReport
./gradlew jacocoTestCoverageVerification  # 70% minimum
```

### Frontend
```bash
cd frontend
npm run format
npm run lint
npm run e2e
```

### Both
- [ ] No `javax.persistence` imports in Java files
- [ ] No edited/deleted Flyway migrations (only new ones)
- [ ] All `inject()` — no constructor injection in Angular
- [ ] UI changes verified in browser
