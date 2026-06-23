import { describe, expect, it } from 'vitest';
import { relativeTime } from './relative-time';

describe('relativeTime', () => {
  const now = new Date('2026-06-23T12:00:00Z');

  it('returns "never" for null', () => {
    expect(relativeTime(null, now)).toBe('never');
  });

  it('returns "just now" under a minute', () => {
    expect(relativeTime('2026-06-23T11:59:30Z', now)).toBe('just now');
  });

  it('formats minutes', () => {
    expect(relativeTime('2026-06-23T11:45:00Z', now)).toBe('15m ago');
  });

  it('formats hours', () => {
    expect(relativeTime('2026-06-23T09:00:00Z', now)).toBe('3h ago');
  });

  it('formats days', () => {
    expect(relativeTime('2026-06-20T12:00:00Z', now)).toBe('3d ago');
  });

  it('formats weeks', () => {
    expect(relativeTime('2026-06-02T12:00:00Z', now)).toBe('3w ago');
  });
});
