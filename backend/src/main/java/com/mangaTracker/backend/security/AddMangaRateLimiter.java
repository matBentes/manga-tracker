package com.mangaTracker.backend.security;

import com.mangaTracker.backend.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window rate limiter for adding manga. Each user may perform at most {@code
 * maxPerWindow} adds within a rolling {@code window}; the next attempt is rejected with {@link
 * RateLimitExceededException} (mapped to HTTP 429). State is kept in memory per JVM instance, which
 * is sufficient for the single-node deployment; it resets on restart and is not shared across
 * replicas.
 *
 * <p>Time is read from an injected {@link Clock} so the window can be exercised deterministically
 * in tests.
 */
@Component
public class AddMangaRateLimiter {

  private final int maxPerWindow;
  private final Duration window;
  private final Clock clock;
  private final ConcurrentHashMap<UUID, Deque<Instant>> hitsByUser = new ConcurrentHashMap<>();

  @Autowired
  public AddMangaRateLimiter(
      @Value("${app.ratelimit.add-manga.max:20}") int maxPerWindow,
      @Value("${app.ratelimit.add-manga.window-seconds:60}") long windowSeconds) {
    this(maxPerWindow, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
  }

  AddMangaRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    this.maxPerWindow = maxPerWindow;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Records an add attempt for the user and enforces the limit. Call once per request, before doing
   * the work.
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
            "Too many manga added. Limit is "
                + maxPerWindow
                + " per "
                + window.toSeconds()
                + "s; try again later.");
      }
      hits.addLast(now);
    }
  }
}
