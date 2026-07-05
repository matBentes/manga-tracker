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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

  private final Instant start = Instant.parse("2026-06-25T12:00:00Z");

  @Test
  void slidingWindowCountsOnlyHitsInsideTheWindow() {
    MutableClock clock = new MutableClock(start);
    TestLimiter limiter = new TestLimiter(2, Duration.ofMinutes(1), clock, 10);

    limiter.checkKey("owner"); // t=0
    clock.advance(Duration.ofSeconds(40));
    limiter.checkKey("owner"); // t=40
    clock.advance(Duration.ofSeconds(25)); // t=65: first hit is outside 60s window

    assertThatCode(() -> limiter.checkKey("owner")).doesNotThrowAnyException();
    assertThatThrownBy(() -> limiter.checkKey("owner"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void tracksEachKeyIndependently() {
    MutableClock clock = new MutableClock(start);
    TestLimiter limiter = new TestLimiter(1, Duration.ofMinutes(1), clock, 10);

    assertThatCode(() -> limiter.checkKey("owner")).doesNotThrowAnyException();
    assertThatCode(() -> limiter.checkKey("demo")).doesNotThrowAnyException();

    assertThatThrownBy(() -> limiter.checkKey("owner"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void removesExpiredEntriesWhenCapIsReached() {
    MutableClock clock = new MutableClock(start);
    TestLimiter limiter = new TestLimiter(1, Duration.ofMinutes(1), clock, 2);

    limiter.checkKey("expired");
    clock.advance(Duration.ofSeconds(10));
    limiter.checkKey("active");
    clock.advance(Duration.ofSeconds(51));

    assertThatCode(() -> limiter.checkKey("fresh")).doesNotThrowAnyException();

    assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    assertThatThrownBy(() -> limiter.checkKey("active"))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  void evictsEntryWithOldestNewestHitWhenCapIsStillFull() {
    MutableClock clock = new MutableClock(start);
    TestLimiter limiter = new TestLimiter(1, Duration.ofMinutes(10), clock, 2);

    limiter.checkKey("oldest");
    clock.advance(Duration.ofSeconds(10));
    limiter.checkKey("newer");
    clock.advance(Duration.ofSeconds(10));

    limiter.checkKey("incoming");

    assertThat(limiter.trackedKeyCount()).isEqualTo(2);
    assertThatThrownBy(() -> limiter.checkKey("newer"))
        .isInstanceOf(RateLimitExceededException.class);
    assertThatCode(() -> limiter.checkKey("oldest")).doesNotThrowAnyException();
  }

  @Test
  void keepsTrackedKeysAtTheConfiguredCap() {
    MutableClock clock = new MutableClock(start);
    TestLimiter limiter = new TestLimiter(2, Duration.ofMinutes(10), clock, 2);

    limiter.checkKey("first");
    limiter.checkKey("second");
    limiter.checkKey("third");
    limiter.checkKey("fourth");

    assertThat(limiter.trackedKeyCount()).isEqualTo(2);
  }

  private static final class TestLimiter extends SlidingWindowRateLimiter {

    TestLimiter(int maxPerWindow, Duration window, Clock clock, int maxTrackedKeys) {
      super(maxPerWindow, window, clock, "Too many test attempts.", maxTrackedKeys);
    }

    void checkKey(String key) {
      check(key);
    }
  }

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
}
