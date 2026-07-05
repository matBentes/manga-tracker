package com.mangatracker.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Per-IP + username limiter for password login attempts.
 *
 * <p>The app deliberately avoids a username-only global budget: that would let an attacker lock out
 * the real owner account from many source IPs. BCrypt plus this per-IP composite key is the
 * accepted posture for this single-node deployment. Client IP comes from {@link
 * HttpServletRequest#getRemoteAddr()}; with {@code server.forward-headers-strategy=framework},
 * upstream nginx/ALB must sanitize X-Forwarded-For before it reaches the app.
 */
@Component
public class LoginRateLimiter extends SlidingWindowRateLimiter {

  @Autowired
  public LoginRateLimiter(
      @Value("${app.ratelimit.login.max:10}") int maxPerWindow,
      @Value("${app.ratelimit.login.window-seconds:900}") long windowSeconds) {
    this(maxPerWindow, Duration.ofSeconds(windowSeconds), Clock.systemUTC());
  }

  LoginRateLimiter(int maxPerWindow, Duration window, Clock clock) {
    super(maxPerWindow, window, clock, "Too many login attempts.");
  }

  LoginRateLimiter(int maxPerWindow, Duration window, Clock clock, int maxTrackedKeys) {
    super(maxPerWindow, window, clock, "Too many login attempts.", maxTrackedKeys);
  }

  public void check(HttpServletRequest request, String username) {
    check(clientIp(request) + "|" + normalizedUsername(username));
  }

  private static String clientIp(HttpServletRequest request) {
    String remoteAddr = request == null ? null : request.getRemoteAddr();
    return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
  }

  private static String normalizedUsername(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }
}
