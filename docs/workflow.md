# Good Practices: Human + AI Agent Workflow

How to run this project effectively with either Claude Code or Codex.

## Start Here

1. Read the shared rules: `docs/agent-workflow.md`
2. Use your agent entrypoint:
   - Codex: `AGENTS.md`
   - Claude: `CLAUDE.md`
3. Use `docs/developer-guide.md` for detailed project conventions

## Practical Split That Works Well

1. Use Claude for feature discovery:
   - scope clarification
   - PRD generation
   - story slicing and acceptance criteria
2. Use Codex for delivery:
   - implement stories
   - run tests and linting
   - do the first findings-first self-review
3. Use Claude for the independent second review:
   - compare implementation against the plan
   - run verification commands
   - agree or disagree with the implementer review
4. Use the implementing agent for fixes:
   - address agreed findings
   - hand back for re-review before push

## Session Structure

Every non-trivial session should follow this pattern:

1. **Orient**: Load shared conventions from `docs/agent-workflow.md`
2. **Plan**: Explore code and agree an approach before broad edits
3. **Implement**: Make targeted changes with tests
4. **Self-Review**: Implementing agent runs a findings-first review
5. **Second Review**: The other agent independently reviews and verifies
6. **Fix**: The implementing agent fixes agreed issues
7. **Re-review**: Both agents confirm the fix set or explicitly disagree
8. **Capture**: Record notable process updates in docs when needed

If both reviews pass on the first check, skip directly from Step 5 to Step 8.

### Codex Rule Mapping (Claude-like behavior)

To emulate a Claude-style disciplined loop in Codex, use:

- `plan first`: Codex explores and proposes a plan before editing
- `implement now`: Codex executes immediately without a separate planning pause
- `review this`: Codex performs the first findings-first self-review before the second agent checks
- `tech debt scan`: Codex runs the project tech debt workflow

Recommended default for non-trivial tasks: start with `plan first`.

## Supervised Implementation Flow

When Codex (or another agent) implements a plan that Claude created:

```
Claude: /prd or write plan → tasks/*.md (include ## Verification and ## Review Gate)
                ↓
User: hand plan to Codex → Codex implements
                ↓
Codex: run self-review (`review this`) → record findings / ready verdict
                ↓
User: ask Claude to /supervise → Claude independently reviews and verifies
                ↓
Agreement?
  - agree-pass → push
  - agree-fail → Codex fixes → both re-review
  - disagree   → reconcile review differences before fixing or pushing
```

### Plan Verification And Review Sections

Every plan in `tasks/` should end with a `## Verification` block and a `## Review Gate` block so the two-agent loop is explicit:

Start from [tasks/plan-template.md](/home/maetsu/Desktop/projects/manga-tracker/tasks/plan-template.md) when writing a new task plan.

```markdown
## Verification

**Success commands:**
- `cd frontend && npm test`
- `cd frontend && npm run test:coverage`

**Quality gates:** backend, frontend (references CLAUDE.md commands)

**Max fix attempts:** 3

**Watch targets:**
- `frontend/package.json` — dependency versions must be compatible
- `frontend/vitest.config.*` — must reference correct setup file

## Review Gate

**Implementer review:**
- Codex runs `review this`

**Independent review:**
- Claude runs `/supervise`

**Agreement rule:**
- `agree-pass` = both reviews say ready
- `agree-fail` = both reviews say blockers remain
- `disagree` = reviewers differ materially; stop and reconcile before push

**Fix owner:**
- original implementer unless the user redirects
```

If a plan lacks `## Verification`, `/supervise` constructs one from CLAUDE.md quality gates + reasonable defaults.
If a plan lacks `## Review Gate`, the second review can still run, but the double-check is incomplete and should be called out explicitly.

### When to use `/supervise`

- After the implementing agent has finished and posted a self-review
- When you want the independent second-agent verdict before fixing or pushing
- After each fix round when you want both agents to re-check the result
- When the task involves config/dependency changes where version mismatches are likely
- Anytime you'd say "watch this" or "check what Codex did"

## Feature Task Flow

For new features:

1. Generate a PRD
   - Claude style: `/prd`
   - Codex style: ask to use the `prd` skill
2. Refine scope and acceptance criteria
3. Choose execution mode
   - Ralph/autonomous flow for multi-story features
   - Interactive story-by-story implementation
4. Implement with the chosen agent
5. Run implementer self-review
   - Codex style: `review this`
6. Run the independent second review
   - Claude style: `/supervise`
7. Fix agreed issues and re-review until the verdict is `agree-pass`
8. Run tech debt scan periodically
   - Claude style: `/techdebt`
   - Codex style: ask to use the `techdebt` skill

## Available Skills

### Project-specific (`skills/`)

| Skill | Purpose | When to use |
|-------|---------|-------------|
| `prd` | Generate a structured PRD | Starting a new feature |
| `ralph` | Convert PRD to Ralph JSON format | Preparing autonomous execution |
| `techdebt` | Scan for tech debt and inconsistencies | After a Ralph run, or periodically |
| `review` | Pre-push review against project conventions | Before pushing any branch |
| `supervise` | Independent second review against a plan | After the implementing agent finishes and self-reviews |

### Installed community skills (`.agents/skills/`)

| Skill | Purpose | When to use |
|-------|---------|-------------|
| `angular-component` | Generate Angular components | Creating new standalone components |
| `java-springboot` | Spring Boot patterns | Adding controllers, services, entities |
| `playwright-e2e-testing` | Playwright E2E testing | Writing or debugging E2E tests |
| `tdd` | Test-driven development | Implementing with red-green-refactor |
| `code-review` | General code quality review | Deep review for SOLID, patterns, bugs |
| `security-review` | OWASP security vulnerability scan | Pre-merge security audit |
| `backend-testing` | Backend test strategies | Writing unit/integration tests for Java |
| `docker-expert` | Docker/Compose guidance | Dockerfile or compose changes |
| `github-actions-cicd` | CI/CD pipeline help | Modifying GitHub Actions workflows |
| `find-skills` | Discover installable skills | When you are unsure which skill to use |
| `skill-creator` | Create or improve skills | Building or refining project skills |

## Claude-only Capabilities

The items below are Claude-specific workflow features and should not be treated as shared agent rules:

- `/worktree` helper flow in `.claude/worktrees/`
- Claude subagents for parallel background exploration
- Claude memory-specific prompts in `.claude/`

## Pre-Push Checklist

Direct pushes to `main` are blocked by `.githooks/pre-push` by default.
Use branch + PR flow; only use `ALLOW_MAIN_PUSH=1 git push origin main` intentionally.

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
npm test              # Vitest unit tests
npm run lint
npm run e2e
```

### Both

- [ ] No `javax.persistence` imports in Java files
- [ ] No edited/deleted Flyway migrations (only new ones)
- [ ] All `inject()` usage, no constructor injection in Angular
- [ ] UI changes verified in browser/Playwright
