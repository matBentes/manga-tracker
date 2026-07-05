package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangatracker.backend.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LoginRateLimiterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-06-25T12:00:00Z"), ZoneOffset.UTC);

  @Test
  void longUsernamesSharingFirst100CharactersShareBudget() {
    LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(15), FIXED_CLOCK);
    MockHttpServletRequest request = requestFrom("203.0.113.40");
    String sharedPrefix = "a".repeat(100);

    limiter.check(request, sharedPrefix + "x");

    assertThatThrownBy(() -> limiter.check(request, sharedPrefix + "y"))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessage("Too many login attempts. Limit is 1 per 900s; try again later.");
  }

  @Test
  void oversizedUsernamesWithDistinctSuffixesDoNotCreateDistinctKeys() {
    LoginRateLimiter limiter = new LoginRateLimiter(300, Duration.ofMinutes(15), FIXED_CLOCK);
    MockHttpServletRequest request = requestFrom("203.0.113.41");
    String sharedPrefix = "b".repeat(100);

    for (int i = 0; i < 250; i++) {
      limiter.check(request, sharedPrefix + i);
    }

    assertThat(limiter.trackedKeyCount()).isEqualTo(1);
  }

  private static MockHttpServletRequest requestFrom(String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    return request;
  }
}
