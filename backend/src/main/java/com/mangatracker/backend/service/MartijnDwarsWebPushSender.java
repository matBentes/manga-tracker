package com.mangatracker.backend.service;

import com.mangatracker.backend.model.PushSubscription;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link WebPushSender} backed by the {@code nl.martijndwars:web-push} library. Encrypts the
 * payload with the subscription's keys and signs the request with the VAPID key pair.
 */
@Component
public class MartijnDwarsWebPushSender implements WebPushSender {

  private static final Logger LOG = LoggerFactory.getLogger(MartijnDwarsWebPushSender.class);

  private final VapidKeys vapidKeys;
  private PushService pushService;

  public MartijnDwarsWebPushSender(VapidKeys vapidKeys) {
    this.vapidKeys = vapidKeys;
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  @Override
  public int send(PushSubscription subscription, String payloadJson) {
    try {
      Notification notification =
          new Notification(
              subscription.getEndpoint(),
              subscription.getP256dh(),
              subscription.getAuth(),
              payloadJson.getBytes(StandardCharsets.UTF_8));
      return pushService().send(notification).getStatusLine().getStatusCode();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebPushException("Failed to deliver web push to " + subscription.getEndpoint(), e);
    } catch (Exception e) {
      throw new WebPushException("Failed to deliver web push to " + subscription.getEndpoint(), e);
    }
  }

  private PushService pushService() throws java.security.GeneralSecurityException {
    if (pushService == null) {
      if (!vapidKeys.isConfigured()) {
        throw new IllegalStateException(
            "VAPID keys not configured; set app.push.vapid.public-key/private-key");
      }
      pushService =
          new PushService(
              vapidKeys.getPublicKey(), vapidKeys.getPrivateKey(), vapidKeys.getSubject());
      LOG.info("Web Push service initialized");
    }
    return pushService;
  }
}
