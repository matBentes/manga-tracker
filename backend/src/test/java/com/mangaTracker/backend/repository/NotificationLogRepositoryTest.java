package com.mangaTracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.model.NotificationLog;
import java.util.Optional;
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
class NotificationLogRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private NotificationLogRepository notificationLogRepository;

  @Autowired private MangaRepository mangaRepository;

  @Test
  void save_persistsNotificationLogEntry() {
    UUID mangaId = saveMangaAndGetId();
    NotificationLog log = NotificationLog.builder().mangaId(mangaId).chapterNumber(5).build();

    NotificationLog saved = notificationLogRepository.save(log);

    Optional<NotificationLog> found = notificationLogRepository.findById(saved.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getMangaId()).isEqualTo(mangaId);
    assertThat(found.get().getChapterNumber()).isEqualTo(5);
  }

  @Test
  void existsByMangaIdAndChapterNumber_returnsTrue_whenEntryExists() {
    UUID mangaId = saveMangaAndGetId();
    notificationLogRepository.save(
        NotificationLog.builder().mangaId(mangaId).chapterNumber(5).build());

    assertThat(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 5)).isTrue();
  }

  @Test
  void save_throwsDataIntegrityViolationException_onDuplicateMangaIdAndChapterNumber() {
    UUID mangaId = saveMangaAndGetId();
    notificationLogRepository.saveAndFlush(
        NotificationLog.builder().mangaId(mangaId).chapterNumber(5).build());

    assertThatThrownBy(
            () ->
                notificationLogRepository.saveAndFlush(
                    NotificationLog.builder().mangaId(mangaId).chapterNumber(5).build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID saveMangaAndGetId() {
    Manga manga =
        mangaRepository.save(
            Manga.builder()
                .title("One Piece")
                .sourceUrl("https://sakuramangas.org/manga/one-piece/")
                .currentChapter(0)
                .latestChapter(100)
                .notificationsEnabled(true)
                .build());
    return manga.getId();
  }
}
