package com.mangaTracker.backend.service;

import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import com.mangaTracker.backend.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the lifecycle of browser push subscriptions (idempotent subscribe / unsubscribe). */
@Service
@Transactional
public class PushSubscriptionService {

  private final PushSubscriptionRepository repository;
  private final CurrentUser currentUser;

  public PushSubscriptionService(PushSubscriptionRepository repository, CurrentUser currentUser) {
    this.repository = repository;
    this.currentUser = currentUser;
  }

  /**
   * Register a subscription. Idempotent: re-subscribing the same endpoint refreshes its keys rather
   * than creating a duplicate (endpoint is unique).
   */
  public PushSubscription subscribe(String endpoint, String p256dh, String auth) {
    UUID ownerId = currentUser.requireId();
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("endpoint must not be blank");
    }
    if (p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank()) {
      throw new IllegalArgumentException("p256dh and auth keys are required");
    }
    PushSubscription subscription =
        repository
            .findByEndpoint(endpoint)
            .orElseGet(() -> PushSubscription.builder().endpoint(endpoint).build());
    subscription.setOwnerId(ownerId);
    subscription.setP256dh(p256dh);
    subscription.setAuth(auth);
    return repository.save(subscription);
  }

  public void unsubscribe(String endpoint) {
    UUID ownerId = currentUser.requireId();
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("endpoint must not be blank");
    }
    repository.deleteByEndpointAndOwnerId(endpoint, ownerId);
  }
}
