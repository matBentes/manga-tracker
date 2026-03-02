#!/usr/bin/env bash
set -euo pipefail

# Ralph — Autonomous Agent Orchestrator
# Usage: ./ralph.sh [--tool <tool>] [max_iterations]
#   --tool <tool>   AI tool to use (default: claude)
#   max_iterations  Maximum number of iterations (default: 10)

TOOL="claude"
MAX_ITERATIONS=10
MAX_FIX_ATTEMPTS=3  # max CI self-heal attempts per story

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --tool)
      TOOL="$2"
      shift 2
      ;;
    *)
      MAX_ITERATIONS="$1"
      shift
      ;;
  esac
done

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PRD_FILE="$PROJECT_DIR/prd.json"
PROGRESS_FILE="$PROJECT_DIR/progress.txt"

# Colors
BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
DIM='\033[2m'
RESET='\033[0m'

# Spinner
spinner() {
  local pid=$1
  local label=$2
  local frames=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
  local i=0
  local start=$SECONDS
  while kill -0 "$pid" 2>/dev/null; do
    local elapsed=$(( SECONDS - start ))
    printf "\r  ${CYAN}${frames[$i]}${RESET} %s ${DIM}(${elapsed}s)${RESET}" "$label"
    i=$(( (i + 1) % ${#frames[@]} ))
    sleep 0.1
  done
  printf "\r\033[K"  # clear spinner line
}

# Count remaining stories (jq-based, safe)
count_remaining() {
  jq '[.userStories[] | select(.passes == false)] | length' "$PRD_FILE" 2>/dev/null || echo 0
}

# Get current story ID — lowest priority number among passing:false (jq-based, safe)
current_story() {
  jq -r '[.userStories[] | select(.passes == false)] | sort_by(.priority) | .[0].id' "$PRD_FILE" 2>/dev/null || echo "unknown"
}

# Run claude and stream output indented under the pipe
run_claude() {
  local prompt="$1"
  local OUTPUT_FILE
  OUTPUT_FILE=$(mktemp)

  claude --dangerously-skip-permissions --print "$prompt" \
    > "$OUTPUT_FILE" 2>&1 &
  local CLAUDE_PID=$!

  spinner "$CLAUDE_PID" "Claude is working..."
  wait "$CLAUDE_PID"
  local EXIT_CODE=$?

  if [[ -s "$OUTPUT_FILE" ]]; then
    printf "│\n"
    while IFS= read -r line; do
      printf "│  ${DIM}%s${RESET}\n" "$line"
    done < "$OUTPUT_FILE"
  fi
  rm -f "$OUTPUT_FILE"
  return $EXIT_CODE
}

printf "\n${BOLD}╔══════════════════════════════════╗${RESET}\n"
printf "${BOLD}║        Ralph Orchestrator        ║${RESET}\n"
printf "${BOLD}╚══════════════════════════════════╝${RESET}\n"
printf "  Tool:    ${CYAN}%s${RESET}\n" "$TOOL"
printf "  Project: ${DIM}%s${RESET}\n" "$PROJECT_DIR"
printf "  Max iterations: ${YELLOW}%s${RESET}\n\n" "$MAX_ITERATIONS"

# Check prd.json exists
if [[ ! -f "$PRD_FILE" ]]; then
  printf "${RED}ERROR: prd.json not found at %s${RESET}\n" "$PRD_FILE"
  exit 1
fi

# Check jq is available
if ! command -v jq &>/dev/null; then
  printf "${RED}ERROR: jq is required but not installed. Run: apt-get install jq${RESET}\n"
  exit 1
fi

TOTAL_START=$SECONDS

for i in $(seq 1 "$MAX_ITERATIONS"); do
  REMAINING=$(count_remaining)

  if [[ "$REMAINING" -eq 0 ]]; then
    printf "\n${GREEN}${BOLD}✔ ALL STORIES COMPLETE${RESET}\n"
    printf "  Finished after %d iteration(s) in %ds.\n" "$((i - 1))" "$((SECONDS - TOTAL_START))"
    exit 0
  fi

  STORY=$(current_story)
  ITER_START=$SECONDS

  printf "${BOLD}┌─ Iteration %d/%d${RESET}  ${DIM}(%d stories remaining)${RESET}\n" "$i" "$MAX_ITERATIONS" "$REMAINING"
  printf "│  Story: ${YELLOW}%s${RESET}\n" "$STORY"
  printf "│\n"

  case $TOOL in
    claude)
      # ── Implement the story ─────────────────────────────────────────────
      if ! run_claude "Read CLAUDE.md, prd.json, and progress.txt. Then execute your task as described in CLAUDE.md. Work on the highest priority story where passes is false."; then
        printf "│\n${RED}└─ Claude exited with non-zero code — stopping${RESET}\n"
        exit 1
      fi

      # ── Push ────────────────────────────────────────────────────────────
      printf "│\n"
      printf "│  ${DIM}Pushing to origin...${RESET}\n"
      BRANCH=$(git -C "$PROJECT_DIR/.." rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
      if git -C "$PROJECT_DIR/.." push origin "$BRANCH" 2>&1 | while IFS= read -r line; do printf "│  ${DIM}%s${RESET}\n" "$line"; done; then
        printf "│  ${GREEN}✔ Pushed to origin/%s${RESET}\n" "$BRANCH"
      else
        printf "│  ${RED}✘ Push failed — stopping${RESET}\n"
        exit 1
      fi

      # ── CI loop with self-healing ────────────────────────────────────────
      CI_FIX_ATTEMPT=0
      while true; do
        printf "│\n"
        printf "│  ${DIM}Waiting for CI...${RESET}\n"
        sleep 5  # give GitHub a moment to register the run
        RUN_ID=$(gh run list --branch "$BRANCH" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || echo "")

        if [[ -z "$RUN_ID" ]]; then
          printf "│  ${YELLOW}⚠ Could not find CI run — skipping CI check${RESET}\n"
          break
        fi

        printf "│  ${DIM}CI run #%s — watching...${RESET}\n" "$RUN_ID"

        if gh run watch "$RUN_ID" --exit-status 2>&1 | while IFS= read -r line; do printf "│  ${DIM}%s${RESET}\n" "$line"; done; then
          printf "│  ${GREEN}✔ CI passed${RESET}\n"
          break
        fi

        # ── CI failed — try to self-heal ──────────────────────────────────
        CI_FIX_ATTEMPT=$(( CI_FIX_ATTEMPT + 1 ))
        if [[ $CI_FIX_ATTEMPT -gt $MAX_FIX_ATTEMPTS ]]; then
          printf "│  ${RED}✘ CI still failing after %d fix attempt(s) — stopping${RESET}\n" "$MAX_FIX_ATTEMPTS"
          printf "│  ${DIM}View details: gh run view %s${RESET}\n" "$RUN_ID"
          exit 1
        fi

        printf "│  ${YELLOW}⚠ CI failed — self-heal attempt %d/%d${RESET}\n" "$CI_FIX_ATTEMPT" "$MAX_FIX_ATTEMPTS"

        # Fetch the failed logs (cap at 200 lines to stay within context)
        CI_LOGS=$(gh run view "$RUN_ID" --log-failed 2>/dev/null | head -200)

        # Ask Claude to fix
        FIX_PROMPT="CI failed for story ${STORY} (fix attempt ${CI_FIX_ATTEMPT}/${MAX_FIX_ATTEMPTS}).

Here are the failing CI logs:
---
${CI_LOGS}
---

Diagnose and fix the CI failure above. Do NOT start a new story — only fix this failure. Commit your fix with message: 'fix: ci failure for ${STORY}'."

        printf "│\n"
        if ! run_claude "$FIX_PROMPT"; then
          printf "│  ${RED}✘ Claude fix attempt failed — stopping${RESET}\n"
          exit 1
        fi

        # Push the fix and loop back to watch CI
        printf "│\n"
        printf "│  ${DIM}Pushing fix attempt %d...${RESET}\n" "$CI_FIX_ATTEMPT"
        if git -C "$PROJECT_DIR/.." push origin "$BRANCH" 2>&1 | while IFS= read -r line; do printf "│  ${DIM}%s${RESET}\n" "$line"; done; then
          printf "│  ${GREEN}✔ Fix pushed — re-watching CI${RESET}\n"
        else
          printf "│  ${RED}✘ Push failed — stopping${RESET}\n"
          exit 1
        fi
      done
      ;;

    *)
      printf "${RED}ERROR: Unknown tool '%s'. Supported: claude${RESET}\n" "$TOOL"
      exit 1
      ;;
  esac

  ELAPSED=$(( SECONDS - ITER_START ))
  printf "│\n${GREEN}└─ Done${RESET}  ${DIM}(%ds)${RESET}\n\n" "$ELAPSED"
done

REMAINING=$(count_remaining)
if [[ "$REMAINING" -gt 0 ]]; then
  printf "\n${YELLOW}${BOLD}⚠ MAX ITERATIONS REACHED${RESET}\n"
  printf "  %d stories still remaining.\n" "$REMAINING"
  exit 1
fi

printf "\n${GREEN}${BOLD}✔ ALL STORIES COMPLETE${RESET}\n"
printf "  Total time: %ds\n" "$((SECONDS - TOTAL_START))"
