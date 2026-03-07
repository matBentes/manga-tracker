# Change Log

## 2026-03-07 — Dev Container: Add Codex CLI and mount Codex home

**Files:** `.devcontainer/.devcontainer/devcontainer.json`,
`.devcontainer/.devcontainer/Dockerfile`,
`.devcontainer/.devcontainer/init-firewall.sh`

**What changed:**

- Renamed the dev container to `Claude + Codex Sandbox`
- Added a `CODEX_VERSION` build arg and installed
  `@openai/codex` globally in the image
- Mounted the host `~/.codex` directory into
  `/home/node/.codex` and exposed `CODEX_HOME`
- Allowed `chatgpt.com`, `api.openai.com`, and
  `auth.openai.com` through the dev container firewall

**Why:**
The repository already uses Codex in CI
(`.github/workflows/ci-autofix.yml`), but the dev
container only provisioned Claude Code. Adding Codex
to the image keeps the local container workflow aligned
with CI and reuses the developer's existing Codex auth,
config, and skills inside the container.

## 2026-03-05 — Skills: Install community skills and simplify all project skills

**Files:** `CLAUDE.md`, `skills/*/SKILL.md`,
`.agents/skills/`

**What changed:**

- Installed 11 community skills at project level
  (not global)
- Simplified all 4 project skills using skill-creator
  patterns (822 to 548 lines, -33%)
- Fixed stale references (`dev-browser skill` to
  `Playwright MCP`, `Amp` to `Claude Code`)
- Fixed wrong commands (`npx ng lint` to
  `npm run lint`)
- Added skill discovery instruction to CLAUDE.md

**Why:**
Skills were verbose with duplicated content (checklists
restating the body, 4-story examples where 2 suffice,
repeated convention rules across files). The
skill-creator's guidance says: explain "why" not just
rules, keep SKILL.md lean, and make descriptions
"pushy" so Claude triggers them more reliably. Detailed
breakdown in [skills.md](skills.md).

## 2026-03-04 — Dockerfile: Replace openjdk-21-jdk with Adoptium Temurin 21

**File:** `.devcontainer/.devcontainer/Dockerfile`

**What changed:**

- Added a new `RUN` layer to import the Adoptium
  (Eclipse Temurin) APT repository and GPG key
- Replaced `openjdk-21-jdk` with `temurin-21-jdk`
  in the package install list

**Why:**
The dev container was failing to build with
`exit code: 100`. The `node:20` base image uses Debian
Bookworm, which only has OpenJDK 17 in its default
repos. Since the project's `build.gradle` requires
Java 21 (`JavaLanguageVersion.of(21)`), we added the
Adoptium third-party repository which provides
Temurin 21 builds for Bookworm.
