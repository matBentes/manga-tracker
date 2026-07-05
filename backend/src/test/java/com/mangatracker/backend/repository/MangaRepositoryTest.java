package com.mangatracker.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangatracker.backend.model.AppUser;
import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.model.ReadingStatus;
import com.mangatracker.backend.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MangaRepositoryTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MangaRepository mangaRepository;

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void save_persistsAndRetrievesEntity_withReadingTrackerFields() {
    UUID mangadexId = UUID.randomUUID();
    Manga saved = mangaRepository.save(buildManga(null, mangadexId, null));

    Optional<Manga> found = mangaRepository.findById(saved.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getTitle()).isEqualTo("One Piece");
    assertThat(found.get().getSourceUrl()).isNull();
    assertThat(found.get().getMangadexId()).isEqualTo(mangadexId);
    assertThat(found.get().getReadingStatus()).isEqualTo(ReadingStatus.READING);
    assertThat(found.get().getCurrentChapter()).isEqualTo(0);
    assertThat(found.get().getLatestChapter()).isEqualTo(100);
    assertThat(found.get().isNotificationsEnabled()).isTrue();
  }

  @Test
  void findAllByOrderByUpdatedAtDesc_returnsMangaSortedByUpdatedAtDesc()
      throws InterruptedException {
    Manga first = entityManager.persistAndFlush(buildManga("https://read/first", null, null));
    Thread.sleep(10);
    Manga second = entityManager.persistAndFlush(buildManga("https://read/second", null, null));

    List<Manga> result = mangaRepository.findAllByOrderByUpdatedAtDesc();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getId()).isEqualTo(second.getId());
    assertThat(result.get(1).getId()).isEqualTo(first.getId());
  }

  @Test
  void save_rejectsSameMangaDexIdForSameOwner() {
    UUID ownerId = createOwner("owner-a");
    UUID mangadexId = UUID.randomUUID();

    mangaRepository.saveAndFlush(buildManga("https://read/one", mangadexId, ownerId));
    Manga duplicate = buildManga("https://read/two", mangadexId, ownerId);

    assertThatThrownBy(() -> mangaRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_allowsSameMangaDexIdForDifferentOwners() {
    UUID mangadexId = UUID.randomUUID();
    UUID ownerA = createOwner("owner-a");
    UUID ownerB = createOwner("owner-b");

    mangaRepository.save(buildManga("https://read/a", mangadexId, ownerA));
    mangaRepository.saveAndFlush(buildManga("https://read/b", mangadexId, ownerB));

    assertThat(mangaRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerA)).hasSize(1);
    assertThat(mangaRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerB)).hasSize(1);
  }

  @Test
  void save_allowsManualEntriesWithoutMangaDexIdForSameOwner() {
    UUID ownerId = createOwner("owner-a");

    mangaRepository.save(buildManga(null, null, ownerId));
    mangaRepository.saveAndFlush(buildManga(null, null, ownerId));

    assertThat(mangaRepository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).hasSize(2);
  }

  @Test
  void existsByMangadexIdAndOwnerId_isOwnerScoped() {
    UUID mangadexId = UUID.randomUUID();
    UUID ownerA = createOwner("owner-a");
    UUID ownerB = createOwner("owner-b");
    mangaRepository.saveAndFlush(buildManga("https://read/a", mangadexId, ownerA));

    assertThat(mangaRepository.existsByMangadexIdAndOwnerId(mangadexId, ownerA)).isTrue();
    assertThat(mangaRepository.existsByMangadexIdAndOwnerId(mangadexId, ownerB)).isFalse();
  }

  @Test
  void findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue_filtersNotificationCandidates() {
    UUID ownerId = createOwner("owner-a");
    Manga candidate = buildManga("https://read/candidate", UUID.randomUUID(), ownerId);
    Manga manual = buildManga("https://read/manual", null, ownerId);
    Manga disabled = buildManga("https://read/disabled", UUID.randomUUID(), ownerId);
    disabled.setNotificationsEnabled(false);
    mangaRepository.saveAllAndFlush(List.of(candidate, manual, disabled));

    List<Manga> result = mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue();

    assertThat(result).extracting(Manga::getId).containsExactly(candidate.getId());
  }

  @Test
  void deleteById_removesEntity() {
    Manga saved = mangaRepository.save(buildManga("https://read/one-piece", null, null));

    mangaRepository.deleteById(saved.getId());

    assertThat(mangaRepository.findById(saved.getId())).isEmpty();
  }

  private static Manga buildManga(String sourceUrl, UUID mangadexId, UUID ownerId) {
    return Manga.builder()
        .title("One Piece")
        .sourceUrl(sourceUrl)
        .mangadexId(mangadexId)
        .currentChapter(0)
        .latestChapter(100)
        .notificationsEnabled(true)
        .ownerId(ownerId)
        .build();
  }

  private UUID createOwner(String username) {
    return appUserRepository
        .save(AppUser.builder().username(username).passwordHash("hash").role(Role.OWNER).build())
        .getId();
  }
}
