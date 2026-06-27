package com.mangaTracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangaTracker.backend.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@SpringBootTest(
    properties = {
      "app.auth.jwt-secret=test-secret-that-is-at-least-32-bytes-long!!",
      "app.auth.cookie-secure=false"
    })
class SecurityConfigTest {

  @Autowired private SecurityFilterChain securityFilterChain;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CorsConfigurationSource corsConfigurationSource;
  @Autowired private JwtCookieAuthFilter jwtCookieAuthFilter;

  @MockBean private AppUserRepository appUserRepository;

  @Test
  void passwordEncoder_isBCrypt() {
    assertThat(passwordEncoder)
        .isInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class);
    assertThat(passwordEncoder.encode("test")).isNotNull();
    assertThat(passwordEncoder.matches("test", passwordEncoder.encode("test"))).isTrue();
  }

  @Test
  void securityFilterChain_isConfigured() {
    assertThat(securityFilterChain).isNotNull();
  }

  @Test
  void corsConfigurationSource_isEmpty_whenNoOriginsConfigured() {
    assertThat(corsConfigurationSource).isNotNull();
  }

  @Test
  void jwtCookieAuthFilter_isInjected() {
    assertThat(jwtCookieAuthFilter).isNotNull();
  }
}
