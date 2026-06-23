package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.model.PushSubscription;
import com.mangaTracker.backend.repository.PushSubscriptionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

  @Mock private PushSubscriptionRepository repository;

  @InjectMocks private PushSubscriptionService service;

  @Test
  void subscribe_createsNewSubscription_whenEndpointUnknown() {
    when(repository.findByEndpoint("https://push/a")).thenReturn(Optional.empty());
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.subscribe("https://push/a", "key", "secret");

    ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push/a");
    assertThat(captor.getValue().getP256dh()).isEqualTo("key");
    assertThat(captor.getValue().getAuth()).isEqualTo("secret");
  }

  @Test
  void subscribe_refreshesKeys_whenEndpointAlreadyExists() {
    PushSubscription existing =
        PushSubscription.builder().endpoint("https://push/a").p256dh("old").auth("old").build();
    when(repository.findByEndpoint("https://push/a")).thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

    service.subscribe("https://push/a", "newKey", "newSecret");

    assertThat(existing.getP256dh()).isEqualTo("newKey");
    assertThat(existing.getAuth()).isEqualTo("newSecret");
    verify(repository).save(existing);
  }

  @Test
  void subscribe_rejectsBlankEndpoint() {
    assertThatThrownBy(() -> service.subscribe("  ", "k", "a"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void subscribe_rejectsMissingKeys() {
    assertThatThrownBy(() -> service.subscribe("https://push/a", "", "a"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unsubscribe_deletesByEndpoint() {
    service.unsubscribe("https://push/a");
    verify(repository).deleteByEndpoint("https://push/a");
  }

  @Test
  void unsubscribe_rejectsBlankEndpoint() {
    assertThatThrownBy(() -> service.unsubscribe("")).isInstanceOf(IllegalArgumentException.class);
  }
}
