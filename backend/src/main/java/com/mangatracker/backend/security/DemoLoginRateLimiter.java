package com.mangatracker.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Generous per-IP limiter for the passwordless public demo login. This dampens resource abuse; it
 * is not a credential-protection boundary.
 */
@Component
public class DemoLoginRateLimiter extends SlidingWindowRateLimiter {

  @Autowired
  public DemoLoginRateLimiter(
      @Value("${app.ratelimit.demo-login.max:60}") int maxPerWindow,
      @Value("${app.ratelimit.demo-login.window-seconds:900}") long windowSeconds) {
    this(maxPerWindow, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
  }

  DemoLoginRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    super(maxPerWindow, window, clock, "Too many demo login attempts.");
  }

  public void check(HttpServletRequest request) {
    String remoteAddr = request == null ? null : request.getRemoteAddr();
    check(remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr);
  }
}
