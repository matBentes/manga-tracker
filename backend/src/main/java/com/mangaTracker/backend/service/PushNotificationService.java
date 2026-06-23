package com.mangaTracker.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends Web Push notifications to every registered browser subscription. Dead subscriptions (HTTP
 * 404/410) are pruned so the table stays clean.
 */
@Service
@Transactional
public class PushNotificationService {

  private static final Logger LOG = LoggerFactory.getLogger(PushNotificationService.class);

  private final PushSubscriptionRepository repository;
  private final WebPushSender sender;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public PushNotificationService(PushSubscriptionRepository repository, WebPushSender sender) {
    this.repository = repository;
    this.sender = sender;
  }

  /**
   * Push a notification to all subscriptions. {@code url} is carried in the payload so the service
   * worker can open the manga page when the user taps the notification.
   */
  public void send(String title, String body, String url) {
    List<PushSubscription> subscriptions = repository.findAll();
    if (subscriptions.isEmpty()) {
      return;
    }
    String payload = toPayload(title, body, url);
    for (PushSubscription subscription : subscriptions) {
      try {
        int status = sender.send(subscription, payload);
        if (status == 404 || status == 410) {
          LOG.info("Pruning dead push subscription (status {}): {}", status, subscription.getId());
          repository.delete(subscription);
        }
      } catch (WebPushException e) {
        LOG.error("Failed to send push to {}: {}", subscription.getId(), e.getMessage());
      }
    }
  }

  /**
   * Build the payload in the shape Angular's {@code @angular/service-worker} expects. The worker
   * renders {@code notification.title}/{@code body} and, on tap, runs the {@code onActionClick}
   * default action to open the manga URL.
   */
  private String toPayload(String title, String body, String url) {
    Map<String, Object> notification =
        Map.of(
            "title",
            title,
            "body",
            body,
            "data",
            Map.of("url", url, "onActionClick", Map.of("default", openWindow(url))));
    try {
      return objectMapper.writeValueAsString(Map.of("notification", notification));
    } catch (JsonProcessingException e) {
      throw new WebPushException("Failed to serialize push payload", e);
    }
  }

  private static Map<String, String> openWindow(String url) {
    return Map.of("operation", "openWindow", "url", url);
  }
}
