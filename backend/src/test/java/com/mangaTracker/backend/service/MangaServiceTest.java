package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.exception.UnsupportedSourceException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.model.Role;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.MangaScraper;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import com.mangaTracker.backend.security.AddMangaRateLimiter;
import com.mangaTracker.backend.security.AuthenticatedUser;
import com.mangaTracker.backend.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MangaServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private MangaRepository mangaRepository;

  @Mock private ScraperRegistry scraperRegistry;

  @Mock private CurrentUser currentUser;

  @Mock private AddMangaRateLimiter addMangaRateLimiter;

  @InjectMocks private MangaService mangaService;

  @BeforeEach
  void stubCurrentUser() {
    lenient().when(currentUser.requireId()).thenReturn(USER_ID);
    lenient().when(currentUser.require()).thenReturn(new AuthenticatedUser(USER_ID, Role.OWNER));
  }

  @Test
  void addManga_savesAndReturnsManga_stampedWithOwner() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    MangaScraper scraper = mock(MangaScraper.class);
    when(scraperRegistry.resolve(url)).thenReturn(scraper);
    when(scraper.scrape(url))
        .thenReturn(new ScrapedManga("One Piece", 1000, "https://img/one-piece.jpg"));
    when(mangaRepository.existsBySourceUrlAndOwnerId(url, USER_ID)).thenReturn(false);
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result = mangaService.addManga(url);

    assertThat(result.getTitle()).isEqualTo("One Piece");
    assertThat(result.getLatestChapter()).isEqualTo(1000);
    assertThat(result.getCurrentChapter()).isEqualTo(0);
    assertThat(result.getCoverImageUrl()).isEqualTo("https://img/one-piece.jpg");
    assertThat(result.isNotificationsEnabled()).isTrue();
    assertThat(result.getOwnerId()).isEqualTo(USER_ID);
    verify(mangaRepository).save(any(Manga.class));
  }

  @Test
  void addManga_throwsDuplicateMangaException_whenUrlAlreadyExistsForUser() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    when(mangaRepository.existsBySourceUrlAndOwnerId(url, USER_ID)).thenReturn(true);

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(DuplicateMangaException.class);
  }

  @Test
  void addManga_throwsDuplicateMangaException_onConcurrentInsert() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    MangaScraper scraper = mock(MangaScraper.class);
    when(mangaRepository.existsBySourceUrlAndOwnerId(url, USER_ID)).thenReturn(false);
    when(scraperRegistry.resolve(url)).thenReturn(scraper);
    when(scraper.scrape(url)).thenReturn(new ScrapedManga("One Piece", 1000, null));
    when(mangaRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(DuplicateMangaException.class);
  }

  @Test
  void addManga_throwsUnsupportedSourceException_forUnsupportedUrl() {
    String url = "https://unknown.com/manga/test/";
    when(mangaRepository.existsBySourceUrlAndOwnerId(url, USER_ID)).thenReturn(false);
    when(scraperRegistry.resolve(url))
        .thenThrow(new UnsupportedSourceException("No scraper found for URL: " + url));

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(UnsupportedSourceException.class);
  }

  @Test
  void listManga_returnsOnlyCurrentUsersManga() {
    List<Manga> expected = List.of(buildManga(UUID.randomUUID(), 10));
    when(mangaRepository.findAllByOwnerIdOrderByUpdatedAtDesc(USER_ID)).thenReturn(expected);

    List<Manga> result = mangaService.listManga();

    assertThat(result).isSameAs(expected);
  }

  @Test
  void getById_returnsManga_whenOwnedByUser() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));

    Manga result = mangaService.getById(id);

    assertThat(result).isSameAs(manga);
  }

  @Test
  void getById_throwsMangaNotFoundException_forUnknownId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.getById(id)).isInstanceOf(MangaNotFoundException.class);
  }

  @Test
  void getById_throwsMangaNotFoundException_whenOwnedByAnotherUser() {
    // Another user's manga is invisible: the owner-scoped query returns empty, so 404 (not 403).
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.getById(id)).isInstanceOf(MangaNotFoundException.class);
  }

  @Test
  void updateManga_updatesNotificationsEnabled() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.updateManga(id, false);

    assertThat(result.isNotificationsEnabled()).isFalse();
  }

  @Test
  void updateManga_throwsMangaNotFoundException_forCrossUserId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.updateManga(id, false))
        .isInstanceOf(MangaNotFoundException.class);
    verify(mangaRepository, never()).save(any());
  }

  @Test
  void markRead_setsCurrentChapterToLatest() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    manga.setCurrentChapter(40);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.markRead(id);

    assertThat(result.getCurrentChapter()).isEqualTo(100);
  }

  @Test
  void markUnread_resetsCurrentChapterToZero() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    manga.setCurrentChapter(100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.markUnread(id);

    assertThat(result.getCurrentChapter()).isZero();
  }

  @Test
  void markRead_throwsMangaNotFoundException_forUnknownId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.markRead(id)).isInstanceOf(MangaNotFoundException.class);
  }

  @Test
  void deleteManga_deletesOwnedManga() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));

    mangaService.deleteManga(id);

    verify(mangaRepository).delete(manga);
  }

  @Test
  void deleteManga_throwsMangaNotFoundException_forCrossUserId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.deleteManga(id))
        .isInstanceOf(MangaNotFoundException.class);
    verify(mangaRepository, never()).delete(any());
  }

  private static Manga buildManga(UUID id, int latestChapter) {
    return Manga.builder()
        .id(id)
        .title("Test Manga")
        .sourceUrl("https://sakuramangas.org/manga/test/")
        .currentChapter(0)
        .latestChapter(latestChapter)
        .notificationsEnabled(true)
        .ownerId(USER_ID)
        .build();
  }
}
