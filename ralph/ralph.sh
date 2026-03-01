#!/usr/bin/env bash
set -euo pipefail

# Ralph — Autonomous Agent Orchestrator
# Usage: ./ralph.sh [--tool <tool>] [max_iterations]
#   --tool <tool>   AI tool to use (default: claude)
#   max_iterations  Maximum number of iterations (default: 10)

TOOL="claude"
MAX_ITERATIONS=10

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

# Count remaining stories
count_remaining() {
  grep -c '"passes": false' "$PRD_FILE" 2>/dev/null || echo 0
}

# Get current story ID from prd.json
current_story() {
  grep -B5 '"passes": false' "$PRD_FILE" 2>/dev/null \
    | grep '"id"' | tail -1 \
    | sed 's/.*"id": *"\([^"]*\)".*/\1/' || echo "unknown"
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

  # Run the AI tool
  case $TOOL in
    claude)
      OUTPUT_FILE=$(mktemp)
      claude --dangerously-skip-permissions --print \
        "Read CLAUDE.md, prd.json, and progress.txt. Then execute your task as described in CLAUDE.md. Work on the highest priority story where passes is false." \
        > "$OUTPUT_FILE" 2>&1 &
      CLAUDE_PID=$!

      spinner "$CLAUDE_PID" "Claude is working..."
      wait "$CLAUDE_PID"
      EXIT_CODE=$?

      # Print claude's output indented
      if [[ -s "$OUTPUT_FILE" ]]; then
        printf "│\n"
        while IFS= read -r line; do
          printf "│  ${DIM}%s${RESET}\n" "$line"
        done < "$OUTPUT_FILE"
      fi
      rm -f "$OUTPUT_FILE"

      if [[ $EXIT_CODE -ne 0 ]]; then
        printf "│\n${RED}└─ Claude exited with code %d${RESET}\n" "$EXIT_CODE"
        exit "$EXIT_CODE"
      fi

      # Push after each story
      printf "│\n"
      printf "│  ${DIM}Pushing to origin...${RESET}\n"
      BRANCH=$(git -C "$PROJECT_DIR/.." rev-parse --abbrev-ref HEAD 2>/dev/null || echo "main")
      if git -C "$PROJECT_DIR/.." push origin "$BRANCH" 2>&1 | while IFS= read -r line; do printf "│  ${DIM}%s${RESET}\n" "$line"; done; then
        printf "│  ${GREEN}✔ Pushed to origin/%s${RESET}\n" "$BRANCH"
      else
        printf "│  ${RED}✘ Push failed — stopping${RESET}\n"
        exit 1
      fi

      # Wait for CI to pass
      printf "│\n"
      printf "│  ${DIM}Waiting for CI...${RESET}\n"
      sleep 5  # give GitHub a moment to register the run
      RUN_ID=$(gh run list --branch "$BRANCH" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || echo "")
      if [[ -z "$RUN_ID" ]]; then
        printf "│  ${YELLOW}⚠ Could not find CI run — skipping CI check${RESET}\n"
      else
        printf "│  ${DIM}CI run #%s — watching...${RESET}\n" "$RUN_ID"
        if gh run watch "$RUN_ID" --exit-status 2>&1 | while IFS= read -r line; do printf "│  ${DIM}%s${RESET}\n" "$line"; done; then
          printf "│  ${GREEN}✔ CI passed${RESET}\n"
        else
          printf "│  ${RED}✘ CI failed — stopping to avoid stacking broken stories${RESET}\n"
          printf "│  ${DIM}View details: gh run view %s${RESET}\n" "$RUN_ID"
          exit 1
        fi
      fi
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
