package com.mangaTracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.model.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTest {

  private final CurrentUser currentUser = new CurrentUser();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void require_returnsAuthenticatedUser_whenPrincipalSet() {
    UUID userId = UUID.randomUUID();
    AuthenticatedUser principal = new AuthenticatedUser(userId, Role.OWNER);
    setPrincipal(principal);

    AuthenticatedUser result = currentUser.require();

    assertThat(result).isEqualTo(principal);
  }

  @Test
  void requireId_returnsUserId_whenPrincipalSet() {
    UUID userId = UUID.randomUUID();
    setPrincipal(new AuthenticatedUser(userId, Role.DEMO));

    assertThat(currentUser.requireId()).isEqualTo(userId);
  }

  @Test
  void requireRole_returnsRole_whenPrincipalSet() {
    setPrincipal(new AuthenticatedUser(UUID.randomUUID(), Role.OWNER));

    assertThat(currentUser.requireRole()).isEqualTo(Role.OWNER);
  }

  @Test
  void require_throwsIllegalState_whenNoAuthentication() {
    SecurityContextHolder.clearContext();

    assertThatThrownBy(currentUser::require).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void require_throwsIllegalState_whenPrincipalIsNotAuthenticatedUser() {
    var auth =
        new UsernamePasswordAuthenticationToken(
            "not-authenticated-user",
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);

    assertThatThrownBy(currentUser::require).isInstanceOf(IllegalStateException.class);
  }

  private void setPrincipal(AuthenticatedUser principal) {
    var auth =
        new UsernamePasswordAuthenticationToken(
            principal,
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
  }
}
