package com.mangatracker.backend.security;

import com.mangatracker.backend.model.Role;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the authenticated principal from the Spring Security context. Service-layer code uses
 * this to scope data to the current user without depending on the web layer.
 */
@Component
public class CurrentUser {

  /**
   * @return the authenticated user.
   * @throws IllegalStateException if no authenticated {@link AuthenticatedUser} is present. The
   *     security filter chain rejects unauthenticated requests with 401 before service code runs,
   *     so this only fires on misconfiguration.
   */
  public AuthenticatedUser require() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
      return principal;
    }
    throw new IllegalStateException("No authenticated user in security context");
  }

  public UUID requireId() {
    return require().userId();
  }

  public Role requireRole() {
    return require().role();
  }
}
