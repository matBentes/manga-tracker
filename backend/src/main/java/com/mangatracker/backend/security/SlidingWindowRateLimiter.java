package com.mangatracker.backend.security;

import com.mangatracker.backend.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

abstract class SlidingWindowRateLimiter {

  static final int DEFAULT_MAX_TRACKED_KEYS = 10_000;

  private final int maxPerWindow;
  private final Duration window;
  private final Clock clock;
  private final String rejectionPrefix;
  private final int maxTrackedKeys;
  private final Object capacityLock = new Object();
  private final ConcurrentHashMap<String, Deque<Instant>> hitsByKey = new ConcurrentHashMap<>();

  SlidingWindowRateLimiter(int maxPerWindow, Duration window, Clock clock, String rejectionPrefix) {
    this(maxPerWindow, window, clock, rejectionPrefix, DEFAULT_MAX_TRACKED_KEYS);
  }

  SlidingWindowRateLimiter(
      int maxPerWindow, Duration window, Clock clock, String rejectionPrefix, int maxTrackedKeys) {
    if (maxPerWindow <= 0) {
      throw new IllegalArgumentException("maxPerWindow must be positive");
    }
    if (window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("window must be positive");
    }
    if (maxTrackedKeys <= 0) {
      throw new IllegalArgumentException("maxTrackedKeys must be positive");
    }
    this.maxPerWindow = maxPerWindow;
    this.window = window;
    this.clock = Objects.requireNonNull(clock, "clock");
    this.rejectionPrefix = rejectionPrefix;
    this.maxTrackedKeys = maxTrackedKeys;
  }

  /**
   * Records an attempt for the key and enforces the limit. Call once per request, before doing the
   * work. Keys may be authenticated user ids or bounded unauthenticated composites such as
   * ip|username.
   *
   * @throws RateLimitExceededException if the key is already at the limit for the current window
   */
  protected final void check(String key) {
    Objects.requireNonNull(key, "key");
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    while (true) {
      // Not computeIfAbsent: a new key must run capacity eviction under capacityLock before
      // insert, and ConcurrentHashMap forbids mutating the map inside a mapping function.
      Deque<Instant> hits = hitsByKey.get(key);
      if (hits == null) {
        synchronized (capacityLock) {
          hits = hitsByKey.get(key);
          if (hits == null) {
            ensureCapacityForNewKey(cutoff);
            hits = new ArrayDeque<>();
            hitsByKey.put(key, hits);
            synchronized (hits) {
              hits.addLast(now);
            }
            return;
          }
        }
      }

      synchronized (hits) {
        pruneExpired(hits, cutoff);
        if (hits.isEmpty()) {
          hitsByKey.remove(key, hits);
          continue;
        }
        enforceLimit(hits);
        hits.addLast(now);
        return;
      }
    }
  }

  int trackedKeyCount() {
    return hitsByKey.size();
  }

  private void ensureCapacityForNewKey(Instant cutoff) {
    if (hitsByKey.size() < maxTrackedKeys) {
      return;
    }
    sweepExpiredEntries(cutoff);
    while (hitsByKey.size() >= maxTrackedKeys && evictOldestNewestHit()) {
      // Continue until one slot is available.
    }
  }

  private void sweepExpiredEntries(Instant cutoff) {
    for (var entry : hitsByKey.entrySet()) {
      Deque<Instant> hits = entry.getValue();
      synchronized (hits) {
        pruneExpired(hits, cutoff);
        if (hits.isEmpty()) {
          hitsByKey.remove(entry.getKey(), hits);
        }
      }
    }
  }

  private boolean evictOldestNewestHit() {
    String oldestKey = null;
    Deque<Instant> oldestHits = null;
    Instant oldestNewestHit = null;
    for (var entry : hitsByKey.entrySet()) {
      Deque<Instant> hits = entry.getValue();
      Instant newestHit;
      synchronized (hits) {
        newestHit = hits.peekLast();
      }
      if (newestHit == null) {
        if (hitsByKey.remove(entry.getKey(), hits)) {
          return true;
        }
        continue;
      }
      if (oldestNewestHit == null || newestHit.isBefore(oldestNewestHit)) {
        oldestKey = entry.getKey();
        oldestHits = hits;
        oldestNewestHit = newestHit;
      }
    }
    return oldestKey != null && hitsByKey.remove(oldestKey, oldestHits);
  }

  private void pruneExpired(Deque<Instant> hits, Instant cutoff) {
    while (!hits.isEmpty() && !hits.peekFirst().isAfter(cutoff)) {
      hits.pollFirst();
    }
  }

  private void enforceLimit(Deque<Instant> hits) {
    if (hits.size() >= maxPerWindow) {
      throw new RateLimitExceededException(
          rejectionPrefix
              + " Limit is "
              + maxPerWindow
              + " per "
              + window.toSeconds()
              + "s; try again later.");
    }
  }
}
