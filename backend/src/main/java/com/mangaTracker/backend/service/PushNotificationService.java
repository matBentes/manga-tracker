package com.mangaTracker.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

  // Status codes the push service returns for a subscription that no longer exists.
  private static final int STATUS_NOT_FOUND = HttpStatus.NOT_FOUND.value();
  private static final int STATUS_GONE = HttpStatus.GONE.value();

  // Service-worker action that opens (and the route then marks-read) the manga. Relative so the
  // browser resolves it against the PWA origin.
  private static final String OPEN_WINDOW_OPERATION = "openWindow";
  private static final String READ_ROUTE = "/open/";
  private static final String SOURCE_URL_PARAM = "?u=";

  private final PushSubscriptionRepository repository;
  private final WebPushSender sender;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public PushNotificationService(PushSubscriptionRepository repository, WebPushSender sender) {
    this.repository = repository;
    this.sender = sender;
  }

  /** Push a message to every subscription, pruning the ones the push service reports as dead. */
  public void send(PushMessage message) {
    List<PushSubscription> subscriptions = repository.findAll();
    if (subscriptions.isEmpty()) {
      return;
    }
    String payload = toPayload(message);
    for (PushSubscription subscription : subscriptions) {
      try {
        int status = sender.send(subscription, payload);
        if (status == STATUS_NOT_FOUND || status == STATUS_GONE) {
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
   * renders {@code notification.title}/{@code body}/{@code icon} and, on tap, runs the {@code
   * onActionClick} default action which opens the "mark read + redirect" route.
   */
  private String toPayload(PushMessage message) {
    Map<String, Object> data = new HashMap<>();
    data.put("mangaId", message.mangaId().toString());
    data.put("url", message.sourceUrl());
    data.put("onActionClick", Map.of("default", openReadRoute(message)));

    Map<String, Object> notification = new HashMap<>();
    notification.put("title", message.title());
    notification.put("body", message.body());
    notification.put("data", data);
    if (message.coverImageUrl() != null && !message.coverImageUrl().isBlank()) {
      notification.put("icon", message.coverImageUrl());
      notification.put("image", message.coverImageUrl());
    }

    try {
      return objectMapper.writeValueAsString(Map.of("notification", notification));
    } catch (JsonProcessingException e) {
      throw new WebPushException("Failed to serialize push payload", e);
    }
  }

  private static Map<String, String> openReadRoute(PushMessage message) {
    String encodedSource = URLEncoder.encode(message.sourceUrl(), StandardCharsets.UTF_8);
    String url = READ_ROUTE + message.mangaId() + SOURCE_URL_PARAM + encodedSource;
    return Map.of("operation", OPEN_WINDOW_OPERATION, "url", url);
  }
}
