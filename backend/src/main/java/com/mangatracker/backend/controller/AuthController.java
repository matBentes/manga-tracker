package com.mangatracker.backend.controller;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.repository.AppUserRepository;
import com.mangatracker.backend.security.AuthenticatedUser;
import com.mangatracker.backend.security.CurrentUser;
import com.mangatracker.backend.security.JwtCookieAuthFilter;
import com.mangatracker.backend.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Cookie-JWT authentication and CSRF bootstrap")
public class AuthController {

  private static final String KEY_USERNAME = "username";
  private static final String KEY_ROLE = "role";
  private static final String KEY_ERROR = "error";
  private static final String KEY_CSRF_TOKEN = "token";

  private static final String DEMO_USERNAME = "demo";

  private final AppUserRepository appUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final CurrentUser currentUser;
  private final CsrfTokenRepository csrfTokenRepository;
  private final boolean cookieSecure;

  private volatile String dummyHash;

  public AuthController(
      AppUserRepository appUserRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      CurrentUser currentUser,
      CsrfTokenRepository csrfTokenRepository,
      @Value("${app.auth.cookie-secure:true}") boolean cookieSecure) {
    this.appUserRepository = appUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.currentUser = currentUser;
    this.csrfTokenRepository = csrfTokenRepository;
    this.cookieSecure = cookieSecure;
  }

  private String getDummyHash() {
    if (dummyHash == null) {
      dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }
    return dummyHash;
  }

  @PostMapping("/login")
  @Operation(summary = "Log in with username and password")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Authenticated; auth cookie set"),
    @ApiResponse(
        responseCode = "400",
        description = "Missing username or password",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Invalid credentials",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
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
  @Operation(summary = "Log in to the public demo account")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Authenticated as demo; auth cookie set"),
    @ApiResponse(
        responseCode = "404",
        description = "Demo account is not seeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Map<String, String>> demoLogin() {
    Optional<AppUser> demo = appUserRepository.findByUsername(DEMO_USERNAME);
    if (demo.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    return authResponse(demo.get());
  }

  @PostMapping("/logout")
  @Operation(summary = "Clear the auth cookie")
  @ApiResponse(responseCode = "204", description = "Auth cookie cleared")
  public ResponseEntity<Void> logout() {
    ResponseCookie cleared = buildAuthCookie("", Duration.ZERO);
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
  }

  @GetMapping("/me")
  @Operation(summary = "Get the current authenticated identity")
  @SecurityRequirement(name = JwtCookieAuthFilter.COOKIE_NAME)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Current identity"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
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

  @GetMapping("/csrf")
  @Operation(summary = "Issue the CSRF token required for state-changing requests")
  @ApiResponse(responseCode = "200", description = "CSRF token returned and cookie set")
  public ResponseEntity<Map<String, String>> csrf(
      HttpServletRequest request, HttpServletResponse response) {
    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.generateToken(request);
      csrfTokenRepository.saveToken(csrfToken, request, response);
    }
    return ResponseEntity.ok()
        .header(csrfToken.getHeaderName(), csrfToken.getToken())
        .body(Map.of(KEY_CSRF_TOKEN, csrfToken.getToken()));
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

  record LoginRequest(
      @Schema(description = "Seeded account username", requiredMode = Schema.RequiredMode.REQUIRED)
          String username,
      @Schema(description = "Account password", requiredMode = Schema.RequiredMode.REQUIRED)
          String password) {}
}
