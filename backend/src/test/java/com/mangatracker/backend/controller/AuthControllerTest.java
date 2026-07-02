package com.mangatracker.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.model.Role;
import com.mangatracker.backend.repository.AppUserRepository;
import com.mangatracker.backend.security.AuthenticatedUser;
import com.mangatracker.backend.security.CurrentUser;
import com.mangatracker.backend.security.JwtService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

  private MockMvc mockMvc;

  @Mock private AppUserRepository appUserRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private CurrentUser currentUser;

  @BeforeEach
  void setUp() {
    CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();
    AuthController controller =
        new AuthController(
            appUserRepository,
            passwordEncoder,
            jwtService,
            currentUser,
            csrfTokenRepository,
            false);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    when(passwordEncoder.encode(any())).thenReturn("$2a$10$dummy.timing.attack.hash");
  }

  @Test
  void login_returns200WithCookieAndNoTokenInBody_onValidCredentials() throws Exception {
    UUID userId = UUID.randomUUID();
    AppUser user =
        AppUser.builder()
            .id(userId)
            .username("owner")
            .passwordHash("$2a$hashed")
            .role(Role.OWNER)
            .build();
    when(appUserRepository.findByUsername("owner")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
    when(jwtService.generateToken(userId, Role.OWNER)).thenReturn("signed.jwt.token");
    when(jwtService.getTokenTtl()).thenReturn(Duration.ofDays(7));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("owner"))
        .andExpect(jsonPath("$.role").value("OWNER"))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, containsString("auth_token=signed.jwt.token")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
  }

  @Test
  void login_returns401Generic_onUnknownUsername() throws Exception {
    when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost\",\"password\":\"secret\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid credentials"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void login_runsPasswordComparison_evenWhenUsernameUnknown() throws Exception {
    when(appUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ghost\",\"password\":\"secret\"}"))
        .andExpect(status().isUnauthorized());

    verify(passwordEncoder).matches(eq("secret"), anyString());
  }

  @Test
  void login_returns401Generic_onWrongPassword() throws Exception {
    AppUser user =
        AppUser.builder()
            .id(UUID.randomUUID())
            .username("owner")
            .passwordHash("$2a$hashed")
            .role(Role.OWNER)
            .build();
    when(appUserRepository.findByUsername("owner")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Invalid credentials"));
  }

  @Test
  void demoLogin_returns200WithCookie_whenDemoSeeded() throws Exception {
    UUID demoId = UUID.randomUUID();
    AppUser demo = AppUser.builder().id(demoId).username("demo").role(Role.DEMO).build();
    when(appUserRepository.findByUsername("demo")).thenReturn(Optional.of(demo));
    when(jwtService.generateToken(demoId, Role.DEMO)).thenReturn("demo.jwt.token");
    when(jwtService.getTokenTtl()).thenReturn(Duration.ofDays(7));

    mockMvc
        .perform(post("/api/auth/demo-login"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("demo"))
        .andExpect(jsonPath("$.role").value("DEMO"))
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, containsString("auth_token=demo.jwt.token")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
  }

  @Test
  void demoLogin_returns404_whenDemoNotSeeded() throws Exception {
    when(appUserRepository.findByUsername("demo")).thenReturn(Optional.empty());

    mockMvc
        .perform(post("/api/auth/demo-login"))
        .andExpect(status().isNotFound())
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void logout_returns204AndClearsCookie() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("auth_token=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
        .andExpect(
            header().string(HttpHeaders.SET_COOKIE, not(containsString("auth_token=signed"))));
  }

  @Test
  void me_returns200WithIdentity_whenAuthenticated() throws Exception {
    UUID userId = UUID.randomUUID();
    when(currentUser.require()).thenReturn(new AuthenticatedUser(userId, Role.DEMO));
    when(appUserRepository.findById(userId))
        .thenReturn(
            Optional.of(AppUser.builder().id(userId).username("demo").role(Role.DEMO).build()));

    mockMvc
        .perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("demo"))
        .andExpect(jsonPath("$.role").value("DEMO"));
  }

  @Test
  void me_returns401_whenUserNoLongerExists() throws Exception {
    UUID userId = UUID.randomUUID();
    when(currentUser.require()).thenReturn(new AuthenticatedUser(userId, Role.OWNER));
    when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
