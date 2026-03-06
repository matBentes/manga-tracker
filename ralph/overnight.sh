#!/usr/bin/env bash
# overnight.sh — restarts ralph until all stories complete or max attempts reached

LOG=/tmp/ralph.log
MAX_RESTARTS=20
RESTART_DELAY=60  # seconds between restarts

cd "$(dirname "$0")/.."

echo ""                                              >> "$LOG"
echo "============================================" >> "$LOG"
echo " Overnight run started: $(date)"              >> "$LOG"
echo "============================================" >> "$LOG"

remaining() {
  jq '[.userStories[] | select(.passes == false)] | length' ralph/prd.json 2>/dev/null || echo 1
}

for attempt in $(seq 1 $MAX_RESTARTS); do
  REMAINING=$(remaining)

  if [[ "$REMAINING" -eq 0 ]]; then
    echo "[$(date)] All stories complete!" >> "$LOG"
    exit 0
  fi

  echo "[$(date)] Attempt $attempt/$MAX_RESTARTS — $REMAINING stories remaining" >> "$LOG"

  bash ralph/ralph.sh >> "$LOG" 2>&1
  EXIT_CODE=$?

  REMAINING=$(remaining)

  if [[ "$REMAINING" -eq 0 ]]; then
    echo "[$(date)] All stories complete after attempt $attempt!" >> "$LOG"
    exit 0
  fi

  if [[ $attempt -lt $MAX_RESTARTS ]]; then
    echo "[$(date)] Ralph exited (code $EXIT_CODE) — restarting in ${RESTART_DELAY}s..." >> "$LOG"
    sleep "$RESTART_DELAY"
  fi
done

REMAINING=$(remaining)
echo "[$(date)] Max restarts reached. $REMAINING stories still remaining." >> "$LOG"
exit 1
