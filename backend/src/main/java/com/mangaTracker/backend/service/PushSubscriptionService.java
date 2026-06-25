package com.mangaTracker.backend.service;

import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Manages the lifecycle of browser push subscriptions (idempotent subscribe / unsubscribe). */
@Service
@Transactional
public class PushSubscriptionService {

  private final PushSubscriptionRepository repository;

  public PushSubscriptionService(PushSubscriptionRepository repository) {
    this.repository = repository;
  }

  /**
   * Register a subscription. Idempotent: re-subscribing the same endpoint refreshes its keys rather
   * than creating a duplicate (endpoint is unique).
   */
  public PushSubscription subscribe(String endpoint, String p256dh, String auth) {
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
    subscription.setP256dh(p256dh);
    subscription.setAuth(auth);
    return repository.save(subscription);
  }

  public void unsubscribe(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("endpoint must not be blank");
    }
    repository.deleteByEndpoint(endpoint);
  }
}
