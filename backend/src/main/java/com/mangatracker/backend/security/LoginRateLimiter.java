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
 * HttpServletRequest#getRemoteAddr()}, after Tomcat's native RemoteIpValve resolves X-Forwarded-For
 * right-to-left past trusted private-range proxies.
 */
@Component
public class LoginRateLimiter extends SlidingWindowRateLimiter {

  private static final int MAX_USERNAME_KEY_CHARS = 100;

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
    String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    // LoginRequest has no length validation, so cap per-key memory before building the limiter key.
    return normalized.length() <= MAX_USERNAME_KEY_CHARS
        ? normalized
        : normalized.substring(0, MAX_USERNAME_KEY_CHARS);
  }
}
