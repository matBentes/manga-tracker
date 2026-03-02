package com.mangaTracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangaTracker.backend.model.AppSettings;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AppSettingsRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private AppSettingsRepository appSettingsRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void findById_returnsSeedRow_fromV3Migration() {
    Optional<AppSettings> settings = appSettingsRepository.findById(1);

    assertThat(settings).isPresent();
    assertThat(settings.get().getNotificationEmail()).isEqualTo("user@localhost");
    assertThat(settings.get().getPollIntervalMinutes()).isEqualTo(30);
    assertThat(settings.get().isEmailNotificationsEnabled()).isTrue();
  }

  @Test
  void save_updatesAndPersistsSingleSettingsRow() {
    AppSettings updated =
        AppSettings.builder()
            .id(1)
            .emailNotificationsEnabled(false)
            .notificationEmail("admin@example.com")
            .pollIntervalMinutes(60)
            .build();

    appSettingsRepository.saveAndFlush(updated);
    entityManager.clear();

    AppSettings saved = appSettingsRepository.findById(1).orElseThrow();
    assertThat(saved.getNotificationEmail()).isEqualTo("admin@example.com");
    assertThat(saved.getPollIntervalMinutes()).isEqualTo(60);
    assertThat(saved.isEmailNotificationsEnabled()).isFalse();
  }
}
