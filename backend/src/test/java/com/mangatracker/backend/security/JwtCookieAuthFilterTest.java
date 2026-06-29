package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangatracker.backend.model.Role;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtCookieAuthFilterTest {

  private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";

  private JwtCookieAuthFilter filter;
  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService(SECRET);
    filter = new JwtCookieAuthFilter(jwtService);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void filter_setsAuthentication_whenValidCookiePresent() throws Exception {
    UUID userId = UUID.randomUUID();
    String token = jwtService.generateToken(userId, Role.OWNER);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, token));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
    AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
    assertThat(principal.userId()).isEqualTo(userId);
    assertThat(principal.role()).isEqualTo(Role.OWNER);
  }

  @Test
  void filter_leavesContextEmpty_whenNoCookiePresentOrNoCookies() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void filter_leavesContextEmpty_whenInvalidTokenInCookie() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, "invalid.token.value"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void filter_leavesContextEmpty_whenBlankTokenInCookie() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, "  "));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void filter_skipsAuthentication_whenAlreadyAuthenticated() throws Exception {
    UUID firstUserId = UUID.randomUUID();
    String token = jwtService.generateToken(firstUserId, Role.OWNER);

    var existingAuth =
        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            "existing", null, java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(existingAuth);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(JwtCookieAuthFilter.COOKIE_NAME, token));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth.getPrincipal()).isEqualTo("existing");
  }
}
