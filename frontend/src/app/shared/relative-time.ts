const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const WEEK = 7 * DAY;

/** Human-readable "time ago" for an ISO timestamp, e.g. "2h ago". Returns 'never' for null. */
export function relativeTime(iso: string | null, now: Date = new Date()): string {
  if (!iso) {
    return 'never';
  }
  const seconds = Math.floor((now.getTime() - new Date(iso).getTime()) / 1000);
  if (seconds < MINUTE) {
    return 'just now';
  }
  if (seconds < HOUR) {
    return `${Math.floor(seconds / MINUTE)}m ago`;
  }
  if (seconds < DAY) {
    return `${Math.floor(seconds / HOUR)}h ago`;
  }
  if (seconds < WEEK) {
    return `${Math.floor(seconds / DAY)}d ago`;
  }
  return `${Math.floor(seconds / WEEK)}w ago`;
}
