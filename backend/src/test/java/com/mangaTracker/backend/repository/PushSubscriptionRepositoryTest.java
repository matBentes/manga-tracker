package com.mangaTracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.model.PushSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PushSubscriptionRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private PushSubscriptionRepository repository;

  @Test
  void save_persistsSubscription() {
    PushSubscription saved =
        repository.save(
            PushSubscription.builder().endpoint("https://push/a").p256dh("k").auth("s").build());

    assertThat(repository.findById(saved.getId())).isPresent();
    assertThat(repository.findByEndpoint("https://push/a")).isPresent();
  }

  @Test
  void existsByEndpoint_returnsTrue_whenPresent() {
    repository.save(
        PushSubscription.builder().endpoint("https://push/b").p256dh("k").auth("s").build());

    assertThat(repository.existsByEndpoint("https://push/b")).isTrue();
  }

  @Test
  void deleteByEndpoint_removesSubscription() {
    repository.saveAndFlush(
        PushSubscription.builder().endpoint("https://push/c").p256dh("k").auth("s").build());

    repository.deleteByEndpoint("https://push/c");

    assertThat(repository.existsByEndpoint("https://push/c")).isFalse();
  }

  @Test
  void save_throwsOnDuplicateEndpoint() {
    repository.saveAndFlush(
        PushSubscription.builder().endpoint("https://push/d").p256dh("k").auth("s").build());

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    PushSubscription.builder()
                        .endpoint("https://push/d")
                        .p256dh("k2")
                        .auth("s2")
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
