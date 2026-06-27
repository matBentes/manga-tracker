package com.mangaTracker.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

  private static final UUID MANGA_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String SOURCE_URL = "https://sakuramangas.org/obras/blue-lock/";

  @Mock private PushSubscriptionRepository repository;
  @Mock private WebPushSender sender;

  @InjectMocks private PushNotificationService service;

  @Test
  void send_deliversToAllSubscriptions() {
    PushSubscription a = buildSubscription("https://push.example/a");
    PushSubscription b = buildSubscription("https://push.example/b");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(a, b));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send(message(null));

    verify(sender).send(eq(a), anyString());
    verify(sender).send(eq(b), anyString());
    verify(repository, never()).delete(any());
  }

  @Test
  void send_payloadCarriesTitleBodyAndMarkReadDeepLinkWithoutSourceRedirect() {
    PushSubscription a = buildSubscription("https://push.example/a");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(a));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send(message(null));

    verify(sender)
        .send(
            eq(a),
            argThat(
                json ->
                    json.contains("Blue Lock")
                        && json.contains("New chapter 169 is out")
                        // deep link opens only the mark-read route; the route uses the owned manga
                        // returned by the API instead of trusting a redirect query parameter.
                        && json.contains("/open/" + MANGA_ID)
                        && !json.contains("sakuramangas.org%2Fobras%2Fblue-lock")));
  }

  @Test
  void send_payloadIncludesCover_whenPresent() {
    PushSubscription a = buildSubscription("https://push.example/a");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(a));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send(message("https://img/cover.jpg"));

    verify(sender).send(eq(a), argThat(json -> json.contains("https://img/cover.jpg")));
  }

  @Test
  void send_usesCoverEndpointUrl_forDataUrlCover_toStayUnderPayloadLimit() {
    PushSubscription a = buildSubscription("https://push.example/a");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(a));
    when(sender.send(any(), anyString())).thenReturn(201);

    service.send(message("data:image/jpeg;base64,QQQQ"));

    // data: URL is tens of KB; reference the cover endpoint instead so we stay under the limit.
    verify(sender)
        .send(
            eq(a),
            argThat(
                json ->
                    !json.contains("data:image")
                        && json.contains("/api/manga/" + MANGA_ID + "/cover")));
  }

  @Test
  void send_prunesSubscription_on410Gone() {
    PushSubscription dead = buildSubscription("https://push.example/dead");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(dead));
    when(sender.send(any(), anyString())).thenReturn(410);

    service.send(message(null));

    verify(repository).delete(dead);
  }

  @Test
  void send_prunesSubscription_on404NotFound() {
    PushSubscription dead = buildSubscription("https://push.example/dead");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(dead));
    when(sender.send(any(), anyString())).thenReturn(404);

    service.send(message(null));

    verify(repository).delete(dead);
  }

  @Test
  void send_continuesToNextSubscription_whenSenderThrows() {
    PushSubscription a = buildSubscription("https://push.example/a");
    PushSubscription b = buildSubscription("https://push.example/b");
    when(repository.findAllByOwnerId(OWNER_ID)).thenReturn(List.of(a, b));
    when(sender.send(eq(a), anyString()))
        .thenThrow(new WebPushException("boom", new RuntimeException()));
    when(sender.send(eq(b), anyString())).thenReturn(201);

    service.send(message(null));

    verify(sender).send(eq(b), anyString());
    verify(repository, never()).delete(any());
  }

  private static PushMessage message(String coverImageUrl) {
    return new PushMessage(
        "Blue Lock", "New chapter 169 is out", MANGA_ID, OWNER_ID, SOURCE_URL, coverImageUrl);
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
