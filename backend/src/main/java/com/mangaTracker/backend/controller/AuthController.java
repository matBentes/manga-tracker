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
import java.util.UUID;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final String KEY_USERNAME = "username";
  private static final String KEY_ROLE = "role";
  private static final String KEY_ERROR = "error";

  private static final String DEMO_USERNAME = "demo";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CurrentUser currentUser;
  private final boolean cookieSecure;

  private volatile String dummyHash;

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

  private String getDummyHash() {
    if (dummyHash == null) {
      dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }
    return dummyHash;
  }

  @PostMapping("/login")
  public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
    String username = request == null ? null : request.username();
    String password = request == null ? null : request.password();
    Optional<AppUser> found =
        username == null ? Optional.empty() : appUserRepository.findByUsername(username);

    String hashToCheck = found.map(AppUser::getPasswordHash).orElse(getDummyHash());
    boolean passwordMatches = password != null && passwordEncoder.matches(password, hashToCheck);

    if (found.isPresent() && passwordMatches) {
      AppUser user = found.get();
      return authResponse(user);
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of(KEY_ERROR, "Invalid credentials"));
  }

  @PostMapping("/demo-login")
  public ResponseEntity<Map<String, String>> demoLogin() {
    Optional<AppUser> demo = appUserRepository.findByUsername(DEMO_USERNAME);
    if (demo.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return authResponse(demo.get());
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie cleared = buildAuthCookie("", Duration.ZERO);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  @GetMapping("/me")
  public ResponseEntity<Map<String, String>> me() {
    AuthenticatedUser principal = currentUser.require();
    return appUserRepository
        .findById(principal.userId())
        .<ResponseEntity<Map<String, String>>>map(
            u ->
                ResponseEntity.ok()
                    .body(Map.of(KEY_USERNAME, u.getUsername(), KEY_ROLE, u.getRole().name())))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  private ResponseEntity<Map<String, String>> authResponse(AppUser user) {
    String token = jwtService.generateToken(user.getId(), user.getRole());
    ResponseCookie cookie = buildAuthCookie(token, jwtService.getTokenTtl());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(Map.of(KEY_USERNAME, user.getUsername(), KEY_ROLE, user.getRole().name()));
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
