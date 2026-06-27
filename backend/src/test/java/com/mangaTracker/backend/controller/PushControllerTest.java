package com.mangaTracker.backend.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangaTracker.backend.service.PushSubscriptionService;
import com.mangaTracker.backend.service.VapidKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PushControllerTest {

  private MockMvc mockMvc;

  @Mock private PushSubscriptionService subscriptionService;
  @Mock private VapidKeys vapidKeys;

  @BeforeEach
  void setUp() {
    PushController controller = new PushController(subscriptionService, vapidKeys);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void publicKey_returnsConfiguredKey() throws Exception {
    when(vapidKeys.getPublicKey()).thenReturn("BPublicKey123");

    mockMvc
        .perform(get("/api/push/public-key").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.publicKey").value("BPublicKey123"));
  }

  @Test
  void subscribe_returns201AndForwardsKeys() throws Exception {
    mockMvc
        .perform(
            post("/api/push/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"endpoint\":\"https://push/a\","
                        + "\"keys\":{\"p256dh\":\"key\",\"auth\":\"secret\"}}"))
        .andExpect(status().isCreated());

    verify(subscriptionService).subscribe(eq("https://push/a"), eq("key"), eq("secret"));
  }

  @Test
  void unsubscribe_returns204() throws Exception {
    mockMvc
        .perform(
            post("/api/push/unsubscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"https://push/a\"}"))
        .andExpect(status().isNoContent());

    verify(subscriptionService).unsubscribe("https://push/a");
  }
}
