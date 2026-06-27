package com.mangaTracker.backend.controller;

import com.mangaTracker.backend.model.AppUser;
import com.mangaTracker.backend.repository.AppUserRepository;
import com.mangaTracker.backend.security.AuthenticatedUser;
import com.mangaTracker.backend.security.CurrentUser;
import com.mangaTracker.backend.security.JwtCookieAuthFilter;
import com.mangaTracker.backend.security.JwtService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Login issues an httpOnly, SameSite=Strict JWT cookie (no token in the
 * body); logout clears it; {@code /me} reports the current identity. Bad credentials always return
 * a generic 401 to avoid revealing whether a username exists.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  /**
   * A valid BCrypt hash used as a decoy when the username does not exist, so a password comparison
   * still runs and login timing does not reveal whether an account exists.
   */
  private static final String DUMMY_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

  /** Username of the shared, public read-mostly demo account used as the landing experience. */
  private static final String DEMO_USERNAME = "demo";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CurrentUser currentUser;
  private final boolean cookieSecure;

  public AuthController(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CurrentUser currentUser,
      @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.currentUser = currentUser;
    this.cookieSecure = cookieSecure;
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    String username = request == null ? null : request.username();
    String password = request == null ? null : request.password();
    Optional<AppUser> found =
        username == null ? Optional.empty() : appUserRepository.findByUsername(username);

    // Always run exactly one BCrypt comparison — against the real hash when the account exists,
    // otherwise a fixed dummy hash — so login timing does not reveal whether the username exists.
    String hashToCheck = found.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
    boolean passwordMatches = password != null && passwordEncoder.matches(password, hashToCheck);

    if (found.isPresent() && passwordMatches) {
      AppUser user = found.get();
      String token = jwtService.generateToken(user.getId(), user.getRole());
      ResponseCookie cookie = buildAuthCookie(token, jwtService.getTokenTtl());
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, cookie.toString())
          .body(Map.of("username", user.getUsername(), "role", user.getRole().name()));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Invalid credentials"));
  }

  /**
   * Passwordless entry to the shared demo account, which is the public landing experience. Issues
   * the same httpOnly JWT cookie as a normal login but for the seeded {@code demo} user, so
   * visitors never receive (or need) the demo password. Returns 404 when the demo account is not
   * seeded.
   */
  @PostMapping("/demo-login")
  public ResponseEntity<?> demoLogin() {
    return appUserRepository
        .findByUsername(DEMO_USERNAME)
        .<ResponseEntity<?>>map(
            demo -> {
              String token = jwtService.generateToken(demo.getId(), demo.getRole());
              ResponseCookie cookie = buildAuthCookie(token, jwtService.getTokenTtl());
              return ResponseEntity.ok()
                  .header(HttpHeaders.SET_COOKIE, cookie.toString())
                  .body(Map.of("username", demo.getUsername(), "role", demo.getRole().name()));
            })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie cleared = buildAuthCookie("", Duration.ZERO);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  @GetMapping("/me")
  public ResponseEntity<?> me() {
    AuthenticatedUser principal = currentUser.require();
    return appUserRepository
        .findById(principal.userId())
        .<ResponseEntity<?>>map(
            u -> ResponseEntity.ok(Map.of("username", u.getUsername(), "role", u.getRole().name())))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  private ResponseCookie buildAuthCookie(String value, Duration maxAge) {
    return ResponseCookie.from(JwtCookieAuthFilter.COOKIE_NAME, value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Strict")
        .path("/")
        .maxAge(maxAge)
        .build();
  }

  record LoginRequest(String username, String password) {}
}
