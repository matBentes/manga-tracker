package com.mangaTracker.backend.service;

import java.util.UUID;

/**
 * A notification to deliver via Web Push.
 *
 * @param mangaId used to build the "mark as read + open" deep link the service worker follows on
 *     tap.
 * @param sourceUrl the manga page to open after marking it read.
 * @param coverImageUrl optional cover shown in the notification; may be {@code null}.
 */
public record PushMessage(
    String title, String body, UUID mangaId, String sourceUrl, String coverImageUrl) {}
