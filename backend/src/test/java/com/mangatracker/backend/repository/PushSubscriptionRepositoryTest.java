package com.mangatracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.model.PushSubscription;
import com.mangatracker.backend.model.Role;
import java.util.UUID;
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

  @Autowired private AppUserRepository appUserRepository;

  @Test
  void save_persistsSubscription() {
    PushSubscription saved =
        repository.save(buildSubscription("https://push/a", createOwner("owner-a")));

    assertThat(repository.findById(saved.getId())).isPresent();
    assertThat(repository.findByEndpoint("https://push/a")).isPresent();
  }

  @Test
  void existsByEndpoint_returnsTrue_whenPresent() {
    repository.save(buildSubscription("https://push/b", createOwner("owner-b")));

    assertThat(repository.existsByEndpoint("https://push/b")).isTrue();
  }

  @Test
  void deleteByEndpointAndOwnerId_removesOnlyOwnedSubscription() {
    UUID ownerId = createOwner("owner-c");
    repository.saveAndFlush(buildSubscription("https://push/c", ownerId));

    repository.deleteByEndpointAndOwnerId("https://push/c", ownerId);

    assertThat(repository.existsByEndpoint("https://push/c")).isFalse();
  }

  @Test
  void findAllByOwnerId_returnsOnlyOwnedSubscriptions() {
    UUID ownerId = createOwner("owner-d");
    UUID otherOwnerId = createOwner("owner-e");
    repository.save(buildSubscription("https://push/d", ownerId));
    repository.save(buildSubscription("https://push/e", otherOwnerId));

    assertThat(repository.findAllByOwnerId(ownerId))
        .extracting(PushSubscription::getEndpoint)
        .containsExactly("https://push/d");
  }

  @Test
  void save_throwsOnDuplicateEndpoint() {
    UUID ownerId = createOwner("owner-f");
    repository.saveAndFlush(buildSubscription("https://push/f", ownerId));

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    PushSubscription.builder()
                        .endpoint("https://push/f")
                        .p256dh("k2")
                        .auth("s2")
                        .ownerId(ownerId)
                        .build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID createOwner(String username) {
    return appUserRepository
        .save(AppUser.builder().username(username).passwordHash("hash").role(Role.OWNER).build())
        .getId();
  }

  private static PushSubscription buildSubscription(String endpoint, UUID ownerId) {
    return PushSubscription.builder()
        .endpoint(endpoint)
        .p256dh("k")
        .auth("s")
        .ownerId(ownerId)
        .build();
  }
}
