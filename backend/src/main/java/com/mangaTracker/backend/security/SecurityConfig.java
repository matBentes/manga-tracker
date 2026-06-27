package com.mangaTracker.backend.security;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless JWT-cookie security. Authenticated endpoints require a valid auth cookie; login/logout
 * are public. CSRF protection uses a double-submit cookie (readable by JS) for state-changing
 * requests, matching the SPA's needs while keeping the auth token httpOnly.
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
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    // Pre-authentication endpoints: the CSRF cookie is not yet available when the
                    // browser calls these for the first time, so the double-submit pattern cannot
                    // apply. Login is safe without CSRF because an attacker cannot read the
                    // response
                    // (which sets the httpOnly auth cookie) or forge valid credentials.
                    .ignoringRequestMatchers("/api/auth/login", "/api/auth/demo-login"))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/logout", "/api/auth/demo-login")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/api/auth/me")
                    .authenticated()
                    .requestMatchers("/api/manga/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
