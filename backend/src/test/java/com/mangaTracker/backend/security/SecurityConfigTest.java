package com.mangaTracker.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangaTracker.backend.controller.AuthController;
import com.mangaTracker.backend.controller.MangaController;
import com.mangaTracker.backend.controller.PushController;
import com.mangaTracker.backend.repository.AppUserRepository;
import com.mangaTracker.backend.service.MangaService;
import com.mangaTracker.backend.service.PushNotificationService;
import com.mangaTracker.backend.service.PushSubscriptionService;
import com.mangaTracker.backend.service.VapidKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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

  @MockBean private AppUserRepository appUserRepository;
  @MockBean private CurrentUser currentUser;
  @MockBean private JwtService jwtService;
  @MockBean private MangaService mangaService;
  @MockBean private PushNotificationService pushNotificationService;
  @MockBean private PushSubscriptionService pushSubscriptionService;
  @MockBean private VapidKeys vapidKeys;

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
  void safePublicRequest_emitsXsrfCookie_forSpaMutations() throws Exception {
    when(vapidKeys.getPublicKey()).thenReturn("BPublicKey123");

    mockMvc
        .perform(get("/api/push/public-key"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("XSRF-TOKEN=")));
  }

  @Test
  void authEndpoints_doNotRequireCsrf() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"secret\"}"))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/auth/demo-login")).andExpect(status().isNotFound());
    mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
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
        .andExpect(status().isForbidden());

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
