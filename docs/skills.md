# Skills History

## 2026-03-05 — Install community skills (project-level)

Installed 11 skills at `.agents/skills/` (project-level, no `-g`):

| Skill | Source | Risk |
|-------|--------|------|
| angular-component | analogjs/angular-skills | Safe |
| java-springboot | github/awesome-copilot | Safe |
| docker-expert | sickn33/antigravity-awesome-skills | Safe (1 socket alert) |
| playwright-e2e-testing | bobmatnyc/claude-mpm-skills | Safe (false positive — markdown only) |
| code-review | supercent-io/skills-template | Safe |
| backend-testing | supercent-io/skills-template | Safe |
| security-review | getsentry/skills | Safe (docs discuss vulns, doesn't introduce them) |
| github-actions-cicd | hack23/homepage | Med Risk |
| tdd | mattpocock/skills | Safe |
| find-skills | (auto-installed) | Safe |
| skill-creator | anthropics/skills | Safe (false positive — markdown + python utils) |

**Why project-level:** User prefers skills scoped to this project, not polluting other projects.

**Security review:** Reviewed `playwright-e2e-testing` and `security-review` contents manually. Both are documentation-only (markdown), no executable code, no network calls. Scanner flags are false positives.

## 2026-03-05 — Improve project-specific skills using skill-creator patterns

Applied skill-creator best practices to all 4 project skills + CLAUDE.md. Total reduction: 822 → 548 lines (33%).

### Changes to `skills/prd/SKILL.md` (235 → 150 lines)

| Change | Why |
|--------|-----|
| Pushier description with more trigger phrases | Claude undertriggers skills — broader phrases help |
| `dev-browser skill` → `Playwright MCP` | dev-browser doesn't exist; Playwright MCP is the actual tool |
| Trimmed example PRD from 4 stories to 2 | Two stories (one backend, one UI) convey the pattern; 4 was redundant |
| Removed end-of-file checklist | Every item restated what the body already said |
| Condensed clarifying questions (3 → 1 example) | LLM understands the pattern from one example |
| Removed "Writing for Clarity" section | Generic writing advice an LLM already follows |

### Changes to `skills/ralph/SKILL.md` (258 → 153 lines)

| Change | Why |
|--------|-----|
| Pushier description | Same undertrigger reason |
| `Amp instance` → `Claude Code instance` | Stale terminology |
| `dev-browser skill` → `Playwright MCP` | Same fix as prd |
| `your ralph directory` → `ralph/prd.json` | Ambiguous path could confuse agents |
| Trimmed example from 4 stories to 2 | Same reason as prd |
| Merged "Splitting Large PRDs" into story size section | Repeated the same concept |
| Consolidated acceptance criteria rules (3 code blocks → 1 paragraph) | Said "Typecheck passes" 3 separate times |
| Condensed archiving section (12 → 3 lines) | ralph.sh handles it automatically anyway |
| Removed end-of-file checklist | Same reason as prd |

### Changes to `skills/review/SKILL.md` (110 → 58 lines)

| Change | Why |
|--------|-----|
| Pushier description with `/code-review` disambiguation | Helps Claude pick the right skill |
| Merged git commands into "The Job" section | "How to Review" section duplicated The Job |
| Replaced 30-line output example with compact format description | The example was mostly `...` placeholders |
| Explained why rubber-stamping is bad | skill-creator says explain "why" not just "what" |

### Changes to `skills/techdebt/SKILL.md` (107 → 88 lines)

| Change | Why |
|--------|-----|
| Pushier description | Same undertrigger reason |
| `npx ng lint` → `npm run lint` | Inconsistent with documented quality gate command in CLAUDE.md |
| Fixed component-to-E2E comparison | Flawed methodology — no 1:1 mapping exists for Playwright E2E |
| Condensed report template (removed duplicate structure) | Warning/Info sections repeated Critical's format |
| Explained why not to auto-fix | skill-creator: explain reasoning, not just rules |

### Changes to `CLAUDE.md` (112 → 99 lines)

| Change | Why |
|--------|-----|
| Added `find-skills` and `skill-creator` to installed skills table | Were installed but missing from the table |
| Tightened skill descriptions in table | Shorter = less context window usage (loads every conversation) |
| Condensed `/review` vs `/code-review` note | Was wordy for a one-liner distinction |
| Removed "Recommended Session Pattern" | Generic workflow advice (orient, plan, implement, verify, commit) an LLM already follows |
| Condensed "Skill Discovery" section (8 → 2 lines) | Kept the actionable command, removed filler explanation |

### Cross-cutting changes

| Change | Why |
|--------|-----|
| Removed `---` separators between sections (all 4 skills) | Decorative when `##` headings already provide structure — saves ~20 lines total |
