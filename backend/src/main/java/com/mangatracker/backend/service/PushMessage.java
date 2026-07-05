package com.mangatracker.backend.service;

import java.util.UUID;

/**
 * A notification to deliver via Web Push.
 *
 * @param mangaId used to build the "mark as read + open" deep link the service worker follows on
 *     tap.
 * @param ownerId user whose browser subscriptions should receive the push.
 * @param sourceUrl optional manga page retained for clients that can open an external read target.
 * @param coverImageUrl optional cover shown in the notification; may be {@code null}.
 */
public record PushMessage(
    String title,
    String body,
    UUID mangaId,
    UUID ownerId,
    String sourceUrl,
    String coverImageUrl) {}
