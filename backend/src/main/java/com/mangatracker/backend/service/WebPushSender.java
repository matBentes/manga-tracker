package com.mangatracker.backend.service;

import com.mangatracker.backend.model.PushSubscription;

/**
 * Thin abstraction over the Web Push transport so {@link PushNotificationService} stays unit
 * testable. Implementations deliver an encrypted payload to a single browser endpoint and return
 * the HTTP status code (e.g. {@code 201} created, {@code 404}/{@code 410} for a dead subscription).
 */
public interface WebPushSender {

  /**
   * Deliver {@code payloadJson} to the given subscription.
   *
   * @return the HTTP status code returned by the push service.
   * @throws WebPushException if the payload could not be encrypted or delivered.
   */
  int send(PushSubscription subscription, String payloadJson);
}
