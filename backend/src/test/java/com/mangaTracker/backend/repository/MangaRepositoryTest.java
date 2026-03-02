package com.mangaTracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mangaTracker.backend.model.Manga;
import java.util.List;
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
class MangaRepositoryTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MangaRepository mangaRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void save_persistsAndRetrievesEntity() {
    Manga saved = mangaRepository.save(buildManga("https://sakuramangas.org/manga/one-piece/"));

    Optional<Manga> found = mangaRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getTitle()).isEqualTo("One Piece");
    assertThat(found.get().getSourceUrl()).isEqualTo("https://sakuramangas.org/manga/one-piece/");
    assertThat(found.get().getCurrentChapter()).isEqualTo(0);
    assertThat(found.get().getLatestChapter()).isEqualTo(100);
    assertThat(found.get().isNotificationsEnabled()).isTrue();
  }

  @Test
  void findAllByOrderByUpdatedAtDesc_returnsMangaSortedByUpdatedAtDesc()
      throws InterruptedException {
    Manga first =
        entityManager.persistAndFlush(
            buildManga("https://sakuramangas.org/manga/first/"));
    Thread.sleep(10);
    Manga second =
        entityManager.persistAndFlush(
            buildManga("https://sakuramangas.org/manga/second/"));

    List<Manga> result = mangaRepository.findAllByOrderByUpdatedAtDesc();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(second.getId());
    assertThat(result.get(1).getId()).isEqualTo(first.getId());
  }

  @Test
  void existsBySourceUrl_returnsTrue_whenUrlExists() {
    mangaRepository.save(buildManga("https://sakuramangas.org/manga/one-piece/"));

    assertThat(
            mangaRepository.existsBySourceUrl("https://sakuramangas.org/manga/one-piece/"))
        .isTrue();
  }

  @Test
  void existsBySourceUrl_returnsFalse_whenUrlDoesNotExist() {
    assertThat(
            mangaRepository.existsBySourceUrl("https://sakuramangas.org/manga/not-found/"))
        .isFalse();
  }

  @Test
  void deleteById_removesEntity() {
    Manga saved = mangaRepository.save(buildManga("https://sakuramangas.org/manga/one-piece/"));

    mangaRepository.deleteById(saved.getId());

    assertThat(mangaRepository.findById(saved.getId())).isEmpty();
  }

  private static Manga buildManga(String sourceUrl) {
    return Manga.builder()
        .title("One Piece")
        .sourceUrl(sourceUrl)
        .currentChapter(0)
        .latestChapter(100)
        .notificationsEnabled(true)
        .build();
  }
}
