package com.mangatracker.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the auth JWT from the httpOnly cookie, verifies it, and populates the security context. A
 * missing or invalid token leaves the context unauthenticated, so downstream authorization rejects
 * the request (401). Tampered/expired tokens are silently ignored — never logged with their value.
 */
@Component
public class JwtCookieAuthFilter extends OncePerRequestFilter {

  /** Name of the httpOnly cookie carrying the auth JWT. */
  public static final String COOKIE_NAME = "auth_token";

  private final JwtService jwtService;

  public JwtCookieAuthFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = extractToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      try {
        AuthenticatedUser user = jwtService.parse(token);
        var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        var authentication =
            new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (Exception ignored) {
        // Invalid/expired/tampered token: leave context unauthenticated. Do not log the value.
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        String value = cookie.getValue();
        return (value == null || value.isBlank()) ? null : value;
      }
    }
    return null;
  }
}
