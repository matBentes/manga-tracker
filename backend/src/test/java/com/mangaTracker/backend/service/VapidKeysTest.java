package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VapidKeysTest {

  @Test
  void exposesConfiguredKeyPairAndSubject() {
    VapidKeys keys = new VapidKeys("pub", "priv", "mailto:admin@example.com");

    assertThat(keys.getPublicKey()).isEqualTo("pub");
    assertThat(keys.getPrivateKey()).isEqualTo("priv");
    assertThat(keys.getSubject()).isEqualTo("mailto:admin@example.com");
  }

  @Test
  void isConfigured_trueWhenBothKeysPresent() {
    assertThat(new VapidKeys("pub", "priv", "mailto:a@b").isConfigured()).isTrue();
  }

  @Test
  void isConfigured_falseWhenPublicKeyBlank() {
    assertThat(new VapidKeys("", "priv", "mailto:a@b").isConfigured()).isFalse();
  }

  @Test
  void isConfigured_falseWhenPrivateKeyBlank() {
    assertThat(new VapidKeys("pub", "   ", "mailto:a@b").isConfigured()).isFalse();
  }
}
