package com.mangatracker.backend.security;

import com.mangatracker.backend.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

abstract class SlidingWindowRateLimiter {

  private final int maxPerWindow;
  private final Duration window;
  private final Clock clock;
  private final String rejectionPrefix;
  private final ConcurrentHashMap<UUID, Deque<Instant>> hitsByUser = new ConcurrentHashMap<>();

  SlidingWindowRateLimiter(int maxPerWindow, Duration window, Clock clock, String rejectionPrefix) {
    this.maxPerWindow = maxPerWindow;
    this.window = window;
    this.clock = clock;
    this.rejectionPrefix = rejectionPrefix;
  }

  /**
   * Records an attempt for the user and enforces the limit. Call once per request, before doing the
   * work.
   *
   * @throws RateLimitExceededException if the user is already at the limit for the current window
   */
  public void check(UUID userId) {
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    Deque<Instant> hits = hitsByUser.computeIfAbsent(userId, k -> new ArrayDeque<>());
    synchronized (hits) {
      while (!hits.isEmpty() && !hits.peekFirst().isAfter(cutoff)) {
        hits.pollFirst();
      }
      if (hits.size() >= maxPerWindow) {
        throw new RateLimitExceededException(
            rejectionPrefix
                + " Limit is "
                + maxPerWindow
                + " per "
                + window.toSeconds()
                + "s; try again later.");
      }
      hits.addLast(now);
    }
  }
}
