package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangatracker.backend.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AddMangaRateLimiterTest {

  /** Test clock whose instant can be advanced to simulate the passage of time. */
  private static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;

    MutableClock(Instant start) {
      this.now = new AtomicReference<>(start);
    }

    void advance(Duration by) {
      now.updateAndGet(i -> i.plus(by));
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final Instant start = Instant.parse("2026-06-25T12:00:00Z");

  @Test
  void allowsUpToMax_thenThrowsOnTheNextWithinWindow() {
    MutableClock clock = new MutableClock(start);
    AddMangaRateLimiter limiter = new AddMangaRateLimiter(3, Duration.ofMinutes(1), clock);
    UUID user = UUID.randomUUID();

    // N (=3) adds succeed.
    for (int i = 0; i < 3; i++) {
      final int n = i;
      assertThatCode(() -> limiter.check(user)).as("add #" + n).doesNotThrowAnyException();
    }

    // The N+1-th within the window is rejected with 429-bound exception.
    assertThatThrownBy(() -> limiter.check(user)).isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void tracksEachUserIndependently() {
    MutableClock clock = new MutableClock(start);
    AddMangaRateLimiter limiter = new AddMangaRateLimiter(1, Duration.ofMinutes(1), clock);
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    assertThatCode(() -> limiter.check(userA)).doesNotThrowAnyException();
    // userA is now at the limit, but userB has its own independent budget.
    assertThatCode(() -> limiter.check(userB)).doesNotThrowAnyException();
    assertThatThrownBy(() -> limiter.check(userA)).isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void allowsAgainOnceTheWindowHasElapsed() {
    MutableClock clock = new MutableClock(start);
    AddMangaRateLimiter limiter = new AddMangaRateLimiter(2, Duration.ofMinutes(1), clock);
    UUID user = UUID.randomUUID();

    limiter.check(user);
    limiter.check(user);
    assertThatThrownBy(() -> limiter.check(user)).isInstanceOf(RateLimitExceededException.class);

    // Slide past the window: the earlier hits expire and budget is restored.
    clock.advance(Duration.ofMinutes(1).plusSeconds(1));
    assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();
  }

  @Test
  void rejectionMessageNamesTheLimit() {
    MutableClock clock = new MutableClock(start);
    AddMangaRateLimiter limiter = new AddMangaRateLimiter(1, Duration.ofMinutes(1), clock);
    UUID user = UUID.randomUUID();

    limiter.check(user);

    assertThatThrownBy(() -> limiter.check(user))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage("Too many manga added. Limit is 1 per 60s; try again later.");
  }

  @Test
  void searchLimiterUsesSameSlidingWindowBehavior() {
    MutableClock clock = new MutableClock(start);
    SearchMangaRateLimiter limiter = new SearchMangaRateLimiter(1, Duration.ofMinutes(1), clock);
    UUID user = UUID.randomUUID();

    assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException();

    assertThatThrownBy(() -> limiter.check(user))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage("Too many MangaDex searches. Limit is 1 per 60s; try again later.");
  }

  @Test
  void slidingWindowCountsOnlyHitsInsideTheWindow() {
    MutableClock clock = new MutableClock(start);
    AddMangaRateLimiter limiter = new AddMangaRateLimiter(2, Duration.ofMinutes(1), clock);
    UUID user = UUID.randomUUID();

    limiter.check(user); // t=0
    clock.advance(Duration.ofSeconds(40));
    limiter.check(user); // t=40
    clock.advance(Duration.ofSeconds(25)); // t=65: first hit (t=0) now outside 60s window
    // Only the t=40 hit remains in-window, so one more is allowed.
    assertThatCode(() -> limiter.check(user)).doesNotThrowAnyException(); // t=65
    // Now t=40 and t=65 are in-window -> next is rejected.
    assertThatThrownBy(() -> limiter.check(user)).isInstanceOf(RateLimitExceededException.class);

    assertThat(limiter).isNotNull();
  }
}
