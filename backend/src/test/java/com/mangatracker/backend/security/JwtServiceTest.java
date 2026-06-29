package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangatracker.backend.model.Role;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";

  @Test
  void generateAndParse_roundTripsUserIdAndRole() {
    JwtService service = new JwtService(SECRET);
    UUID userId = UUID.randomUUID();

    String token = service.generateToken(userId, Role.OWNER);
    AuthenticatedUser parsed = service.parse(token);

    assertThat(parsed.userId()).isEqualTo(userId);
    assertThat(parsed.role()).isEqualTo(Role.OWNER);
  }

  @Test
  void tokenTtl_isSevenDays() {
    JwtService service = new JwtService(SECRET);

    assertThat(service.getTokenTtl()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void parse_throwsJwtException_onTamperedToken() {
    JwtService service = new JwtService(SECRET);
    String token = service.generateToken(UUID.randomUUID(), Role.DEMO);
    String tampered = token.substring(0, token.length() - 2) + "xy";

    assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
  }

  @Test
  void parse_throwsJwtException_whenSignedWithDifferentSecret() {
    JwtService signer = new JwtService(SECRET);
    JwtService verifier = new JwtService("a-totally-different-secret-32-bytes-long!!");
    String token = signer.generateToken(UUID.randomUUID(), Role.OWNER);

    assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void constructor_throwsIllegalState_onBlankSecret() {
    assertThatThrownBy(() -> new JwtService("  ")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructor_throwsIllegalState_onNullSecret() {
    assertThatThrownBy(() -> new JwtService(null)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructor_throwsIllegalState_onShortSecret() {
    assertThatThrownBy(() -> new JwtService("too-short")).isInstanceOf(IllegalStateException.class);
  }
}
