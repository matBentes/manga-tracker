package com.mangatracker.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless JWT-cookie security. Authenticated endpoints require a valid auth cookie; login/logout
 * are public but still CSRF-protected. The SPA fetches a same-origin CSRF token and echoes it in
 * the X-XSRF-TOKEN header while both auth and CSRF cookies remain httpOnly.
 */
@Configuration
public class SecurityConfig {

  private final JwtCookieAuthFilter jwtCookieAuthFilter;

  /**
   * Comma-separated browser origins allowed to call the API with credentials. Empty in production
   * (SPA is served same-origin behind nginx); set in dev to the Angular dev server, e.g. {@code
   * http://localhost:4200}.
   */
  private final List<String> allowedOrigins;

  private static final String AUTH_LOGIN = "/api/auth/login";
  private static final String AUTH_LOGOUT = "/api/auth/logout";
  private static final String AUTH_DEMO_LOGIN = "/api/auth/demo-login";
  private static final String AUTH_CSRF = "/api/auth/csrf";

  public SecurityConfig(
      JwtCookieAuthFilter jwtCookieAuthFilter,
      @Value("${app.auth.allowed-origins:}") String allowedOrigins) {
    this.jwtCookieAuthFilter = jwtCookieAuthFilter;
    this.allowedOrigins =
        allowedOrigins == null || allowedOrigins.isBlank()
            ? List.of()
            : Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    // No allowed origins => SPA is served same-origin (prod behind nginx, dev behind the Angular
    // proxy), so register no CORS config and the filter passes requests through untouched.
    // Registering an empty-origins config makes Spring reject any request carrying an Origin
    // header (every POST sends one) with "Invalid CORS request". Only enforce CORS when origins
    // are explicitly configured for a genuine cross-origin browser client.
    if (!allowedOrigins.isEmpty()) {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(allowedOrigins);
      config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
      config.setAllowedHeaders(List.of("*"));
      config.setAllowCredentials(true);
      source.registerCorsConfiguration("/**", config);
    }
    return source;
  }

  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
    csrfTokenRepository.setCookieCustomizer(
        cookie -> cookie.httpOnly(true).sameSite("Strict").path("/"));
    return csrfTokenRepository;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      CsrfTokenRepository csrfTokenRepository)
      throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf -> {
              CsrfTokenRequestAttributeHandler requestHandler =
                  new CsrfTokenRequestAttributeHandler();
              requestHandler.setCsrfRequestAttributeName(null);
              csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(requestHandler);
            })
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(AUTH_LOGIN, AUTH_LOGOUT, AUTH_DEMO_LOGIN, AUTH_CSRF)
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/push/public-key")
                    .permitAll()
                    .requestMatchers("/api/auth/me")
                    .authenticated()
                    .requestMatchers("/api/manga/**")
                    .authenticated()
                    .requestMatchers("/api/push/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .addFilterAfter(new CsrfCookieFilter(csrfTokenRepository), CsrfFilter.class)
        .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /** Forces Spring's deferred CSRF token to materialize so the SPA receives XSRF-TOKEN. */
  private static final class CsrfCookieFilter extends OncePerRequestFilter {

    private final CsrfTokenRepository csrfTokenRepository;

    private CsrfCookieFilter(CsrfTokenRepository csrfTokenRepository) {
      this.csrfTokenRepository = csrfTokenRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
      if (AUTH_CSRF.equals(request.getRequestURI())) {
        filterChain.doFilter(request, response);
        return;
      }

      CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
      if (csrfToken != null) {
        csrfToken.getToken();
      } else {
        csrfToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(csrfToken, request, response);
      }
      filterChain.doFilter(request, response);
    }
  }
}
