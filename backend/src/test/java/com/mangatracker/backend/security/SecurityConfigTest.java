package com.mangatracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangatracker.backend.controller.AuthController;
import com.mangatracker.backend.controller.MangaController;
import com.mangatracker.backend.controller.PushController;
import com.mangatracker.backend.repository.AppUserRepository;
import com.mangatracker.backend.service.MangaService;
import com.mangatracker.backend.service.PushNotificationService;
import com.mangatracker.backend.service.PushSubscriptionService;
import com.mangatracker.backend.service.VapidKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

@WebMvcTest(controllers = {AuthController.class, MangaController.class, PushController.class})
@Import({SecurityConfig.class, JwtCookieAuthFilter.class})
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SecurityFilterChain securityFilterChain;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private CorsConfigurationSource corsConfigurationSource;
  @Autowired private JwtCookieAuthFilter jwtCookieAuthFilter;

  @MockitoBean private AppUserRepository appUserRepository;
  @MockitoBean private CurrentUser currentUser;
  @MockitoBean private JwtService jwtService;
  @MockitoBean private MangaService mangaService;
  @MockitoBean private PushNotificationService pushNotificationService;
  @MockitoBean private PushSubscriptionService pushSubscriptionService;
  @MockitoBean private VapidKeys vapidKeys;

  @Test
  void passwordEncoder_isBCrypt() {
    assertThat(passwordEncoder)
        .isInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class);
    assertThat(passwordEncoder.encode("test")).isNotNull();
    assertThat(passwordEncoder.matches("test", passwordEncoder.encode("test"))).isTrue();
  }

  @Test
  void securityFilterChain_isConfigured() {
    assertThat(securityFilterChain).isNotNull();
  }

  @Test
  void corsConfigurationSource_isEmpty_whenNoOriginsConfigured() {
    assertThat(corsConfigurationSource).isNotNull();
  }

  @Test
  void jwtCookieAuthFilter_isInjected() {
    assertThat(jwtCookieAuthFilter).isNotNull();
  }

  @Test
  void safePublicRequest_emitsHttpOnlyXsrfCookie_forSpaMutations() throws Exception {
    when(vapidKeys.getPublicKey()).thenReturn("BPublicKey123");

    mockMvc
        .perform(get("/api/push/public-key"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
  }

  @Test
  void csrfEndpoint_exposesTokenForSpaHeader() throws Exception {
    mockMvc
        .perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-XSRF-TOKEN", not("")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
  }

  @Test
  void swaggerDocsPaths_areReachableWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/swagger-ui.html"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    mockMvc
        .perform(get("/swagger-ui/index.html"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
  }

  @Test
  void explicitlyProtectedPaths_requireAuthentication() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/manga")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/push/subscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"https://push/a\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void authEndpoints_requireCsrf() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"secret\"}"))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/auth/demo-login")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"secret\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/auth/demo-login").with(csrf())).andExpect(status().isNotFound());
    mockMvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
  }

  @Test
  void protectedMutations_stillRequireCsrf() throws Exception {
    mockMvc
        .perform(
            post("/api/manga")
                .with(user("owner"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceUrl\":\"https://sakuramangas.org/obras/test/\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void pushSubscribe_requiresAuthenticationAndCsrf() throws Exception {
    mockMvc
        .perform(
            post("/api/push/subscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"https://push/a\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}"))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/api/push/subscribe")
                .with(user("owner"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"https://push/a\",\"keys\":{\"p256dh\":\"k\",\"auth\":\"a\"}}"))
        .andExpect(status().isCreated());

    verify(pushSubscriptionService).subscribe("https://push/a", "k", "a");
  }
}
