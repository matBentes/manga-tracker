package com.mangatracker.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-user sliding-window rate limiter for adding manga. Each user may perform at most {@code
 * maxPerWindow} adds within a rolling {@code window}; the next attempt is rejected with {@link
 * com.mangatracker.backend.exception.RateLimitExceededException} (mapped to HTTP 429). State is
 * kept in memory per JVM instance, which is sufficient for the single-node deployment; it resets on
 * restart and is not shared across replicas.
 *
 * <p>Time is read from an injected {@link Clock} so the window can be exercised deterministically
 * in tests.
 */
@Component
public class AddMangaRateLimiter extends SlidingWindowRateLimiter {

  @Autowired
  public AddMangaRateLimiter(
      @Value("${app.ratelimit.add-manga.max:20}") int maxPerWindow,
      @Value("${app.ratelimit.add-manga.window-seconds:60}") long windowSeconds) {
    this(maxPerWindow, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
  }

  AddMangaRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    super(maxPerWindow, window, clock, "Too many manga added.");
  }

  public void check(UUID userId) {
    check(userId.toString());
  }
}
