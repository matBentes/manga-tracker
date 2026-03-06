# Good Practices: Human + AI Agent Workflow

How to run this project effectively with either Claude Code or Codex.

## Start Here

1. Read the shared rules: `docs/agent-workflow.md`
2. Use your agent entrypoint:
   - Codex: `AGENTS.md`
   - Claude: `CLAUDE.md`
3. Use `docs/developer-guide.md` for detailed project conventions

## Session Structure

Every non-trivial session should follow this pattern:

1. **Orient**: Load shared conventions from `docs/agent-workflow.md`
2. **Plan**: Explore code and agree an approach before broad edits
3. **Implement**: Make targeted changes with tests
4. **Review**: Run project review checks and quality gates
5. **Capture**: Record notable process updates in docs when needed

### Codex Rule Mapping (Claude-like behavior)

To emulate a Claude-style disciplined loop in Codex, use:

- `plan first`: Codex explores and proposes a plan before editing
- `implement now`: Codex executes immediately without a separate planning pause
- `review this`: Codex performs a findings-first review before you push
- `tech debt scan`: Codex runs the project tech debt workflow

Recommended default for non-trivial tasks: start with `plan first`.

## Feature Task Flow

For new features:

1. Generate a PRD
   - Claude style: `/prd`
   - Codex style: ask to use the `prd` skill
2. Refine scope and acceptance criteria
3. Choose execution mode
   - Ralph/autonomous flow for multi-story features
   - Interactive story-by-story implementation
4. Run review
   - Claude style: `/review`
   - Codex style: ask for a project-convention review
5. Run tech debt scan periodically
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
- [ ] All `inject()` usage, no constructor injection in Angular
- [ ] UI changes verified in browser/Playwright
