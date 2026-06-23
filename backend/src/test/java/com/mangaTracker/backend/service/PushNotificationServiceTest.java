package com.mangaTracker.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

  @Mock private PushSubscriptionRepository repository;
  @Mock private WebPushSender sender;

  @InjectMocks private PushNotificationService service;

  @Test
  void send_deliversToAllSubscriptions() {
    PushSubscription a = buildSubscription("https://push.example/a");
    PushSubscription b = buildSubscription("https://push.example/b");
    when(repository.findAll()).thenReturn(List.of(a, b));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send("New chapter", "Chapter 11", "https://sakuramangas.org/manga/test/");

    verify(sender).send(eq(a), anyString());
    verify(sender).send(eq(b), anyString());
    verify(repository, never()).delete(any());
  }

  @Test
  void send_includesTitleBodyAndUrlInPayload() {
    PushSubscription a = buildSubscription("https://push.example/a");
    when(repository.findAll()).thenReturn(List.of(a));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send("New chapter", "Chapter 11", "https://sakuramangas.org/manga/test/");

    verify(sender)
        .send(
            eq(a),
            org.mockito.ArgumentMatchers.argThat(
                json ->
                    json.contains("New chapter")
                        && json.contains("Chapter 11")
                        && json.contains("https://sakuramangas.org/manga/test/")));
  }

  @Test
  void send_prunesSubscription_on410Gone() {
    PushSubscription dead = buildSubscription("https://push.example/dead");
    when(repository.findAll()).thenReturn(List.of(dead));
    when(sender.send(any(), anyString())).thenReturn(410);

    service.send("t", "b", "u");

    verify(repository).delete(dead);
  }

  @Test
  void send_prunesSubscription_on404NotFound() {
    PushSubscription dead = buildSubscription("https://push.example/dead");
    when(repository.findAll()).thenReturn(List.of(dead));
    when(sender.send(any(), anyString())).thenReturn(404);

    service.send("t", "b", "u");

    verify(repository).delete(dead);
  }

  @Test
  void send_continuesToNextSubscription_whenSenderThrows() {
    PushSubscription a = buildSubscription("https://push.example/a");
    PushSubscription b = buildSubscription("https://push.example/b");
    when(repository.findAll()).thenReturn(List.of(a, b));
    when(sender.send(eq(a), anyString()))
        .thenThrow(new WebPushException("boom", new RuntimeException()));
    when(sender.send(eq(b), anyString())).thenReturn(201);

    service.send("t", "b", "u");

    verify(sender).send(eq(b), anyString());
    verify(repository, never()).delete(any());
  }

  private static PushSubscription buildSubscription(String endpoint) {
    return PushSubscription.builder()
        .id(UUID.randomUUID())
        .endpoint(endpoint)
        .p256dh("p256dh-key")
        .auth("auth-secret")
        .build();
  }
}
