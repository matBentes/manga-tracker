package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.model.PushSubscription;
import org.junit.jupiter.api.Test;

class MartijnDwarsWebPushSenderTest {

  private PushSubscription subscription() {
    return PushSubscription.builder()
        .endpoint("https://push.example.com/abc")
        .p256dh("p256dh-key")
        .auth("auth-key")
        .build();
  }

  @Test
  void send_wrapsFailure_whenVapidKeysNotConfigured() {
    MartijnDwarsWebPushSender sender = new MartijnDwarsWebPushSender(new VapidKeys("", "", ""));

    assertThatThrownBy(() -> sender.send(subscription(), "{\"title\":\"hi\"}"))
        .isInstanceOf(WebPushException.class)
        .hasMessageContaining("https://push.example.com/abc")
        .hasCauseInstanceOf(Exception.class);
  }

  @Test
  void constructor_registersBouncyCastleProvider() {
    new MartijnDwarsWebPushSender(new VapidKeys("", "", ""));

    assertThat(java.security.Security.getProvider("BC")).isNotNull();
  }
}
