package com.mangatracker.backend.controller;

import com.mangatracker.backend.service.PushSubscriptionService;
import com.mangatracker.backend.service.VapidKeys;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushController {

  private final PushSubscriptionService subscriptionService;
  private final VapidKeys vapidKeys;

  public PushController(PushSubscriptionService subscriptionService, VapidKeys vapidKeys) {
    this.subscriptionService = subscriptionService;
    this.vapidKeys = vapidKeys;
  }

  @GetMapping("/public-key")
  public Map<String, String> publicKey() {
    return Map.of("publicKey", vapidKeys.getPublicKey());
  }

  @PostMapping("/subscribe")
  public ResponseEntity<Void> subscribe(@RequestBody SubscribeRequest request) {
    subscriptionService.subscribe(request.endpoint(), p256dh(request), auth(request));
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @PostMapping("/unsubscribe")
  public ResponseEntity<Void> unsubscribe(@RequestBody UnsubscribeRequest request) {
    subscriptionService.unsubscribe(request.endpoint());
    return ResponseEntity.noContent().build();
  }

  private static String p256dh(SubscribeRequest request) {
    return request.keys() == null ? null : request.keys().p256dh();
  }

  private static String auth(SubscribeRequest request) {
    return request.keys() == null ? null : request.keys().auth();
  }

  /** Matches the browser {@code PushSubscription.toJSON()} shape. */
  record SubscribeRequest(String endpoint, Keys keys) {}

  record Keys(String p256dh, String auth) {}

  record UnsubscribeRequest(String endpoint) {}
}
