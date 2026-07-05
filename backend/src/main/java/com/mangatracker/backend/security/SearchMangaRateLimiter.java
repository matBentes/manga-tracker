package com.mangatracker.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-user sliding-window rate limiter for MangaDex search proxy requests. */
@Component
public class SearchMangaRateLimiter extends SlidingWindowRateLimiter {

  @Autowired
  public SearchMangaRateLimiter(
      @Value("${app.ratelimit.search-manga.max:30}") int maxPerWindow,
      @Value("${app.ratelimit.search-manga.window-seconds:60}") long windowSeconds) {
    this(maxPerWindow, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
  }

  SearchMangaRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    super(maxPerWindow, window, clock, "Too many MangaDex searches.");
  }

  public void check(UUID userId) {
    check(userId.toString());
  }
}
