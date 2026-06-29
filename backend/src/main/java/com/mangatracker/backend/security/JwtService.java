package com.mangatracker.backend.security;

import com.mangatracker.backend.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies the authentication JWT. Subject = user id, custom {@code role} claim, 7-day
 * expiry. The signing secret comes from the {@code JWT_SECRET} env var and must be at least 256
 * bits (32 bytes); the service fails fast on startup otherwise.
 */
@Service
public class JwtService {

  private static final Duration TOKEN_TTL = Duration.ofDays(7);
  private static final int MIN_SECRET_BYTES = 32; // 256-bit minimum for HS256
  private static final String ROLE_CLAIM = "role";

  private final SecretKey signingKey;

  public JwtService(@Value("${app.auth.jwt-secret}") String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET is not configured. Set the JWT_SECRET environment variable.");
    }
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET must be at least 256 bits (32 bytes); got " + keyBytes.length + " bytes.");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
  }

  /** Returns the token lifetime so callers (e.g. the login cookie) can match the JWT expiry. */
  public Duration getTokenTtl() {
    return TOKEN_TTL;
  }

  public String generateToken(UUID userId, Role role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(ROLE_CLAIM, role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(TOKEN_TTL)))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Verifies the token's signature and expiry and returns its claims.
   *
   * @throws JwtException if the token is malformed, tampered, or expired.
   */
  public AuthenticatedUser parse(String token) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    UUID userId = UUID.fromString(claims.getSubject());
    Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    return new AuthenticatedUser(userId, role);
  }
}
