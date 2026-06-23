package com.mangaTracker.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the VAPID key pair used to authenticate Web Push messages. The private key is a secret and
 * must be supplied via environment/config — never committed.
 */
@Component
public class VapidKeys {

  private final String publicKey;
  private final String privateKey;
  private final String subject;

  public VapidKeys(
      @Value("${app.push.vapid.public-key:}") String publicKey,
      @Value("${app.push.vapid.private-key:}") String privateKey,
      @Value("${app.push.vapid.subject:mailto:admin@localhost}") String subject) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
    this.subject = subject;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public String getSubject() {
    return subject;
  }

  public boolean isConfigured() {
    return !publicKey.isBlank() && !privateKey.isBlank();
  }
}
