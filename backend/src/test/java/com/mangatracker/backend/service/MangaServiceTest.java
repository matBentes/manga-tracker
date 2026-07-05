package com.mangatracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mangatracker.backend.exception.DuplicateMangaException;
import com.mangatracker.backend.exception.MangaDexUpstreamException;
import com.mangatracker.backend.exception.MangaNotFoundException;
import com.mangatracker.backend.exception.RateLimitExceededException;
import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.model.ReadingStatus;
import com.mangatracker.backend.model.Role;
import com.mangatracker.backend.repository.MangaRepository;
import com.mangatracker.backend.security.AddMangaRateLimiter;
import com.mangatracker.backend.security.AuthenticatedUser;
import com.mangatracker.backend.security.CurrentUser;
import com.mangatracker.backend.security.SearchMangaRateLimiter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
  private static final UUID MANGADEX_ID = UUID.randomUUID();

  @Mock private MangaRepository mangaRepository;

  @Mock private MangaDexClient mangaDexClient;

  @Mock private CurrentUser currentUser;

  @Mock private AddMangaRateLimiter addMangaRateLimiter;

  @Mock private SearchMangaRateLimiter searchMangaRateLimiter;

  @InjectMocks private MangaService mangaService;

  @BeforeEach
  void stubCurrentUser() {
    lenient().when(currentUser.requireId()).thenReturn(USER_ID);
    lenient().when(currentUser.require()).thenReturn(new AuthenticatedUser(USER_ID, Role.OWNER));
  }

  @Test
  void searchManga_trimsQueryChecksRateLimiterAndDelegatesToMangaDexClient() {
    List<MangaDexManga> expected =
        List.of(new MangaDexManga(MANGADEX_ID, "One Piece", "Pirates", "https://img/cover.jpg"));
    when(mangaDexClient.search("one piece")).thenReturn(expected);

    List<MangaDexManga> result = mangaService.searchManga(" one piece ");

    assertThat(result).isSameAs(expected);
    verify(searchMangaRateLimiter).check(USER_ID);
  }

  @Test
  void searchManga_rejectsBlankQuery_withoutCallingMangaDex() {
    assertThatThrownBy(() -> mangaService.searchManga(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Search query is required");
    assertThatThrownBy(() -> mangaService.searchManga("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Search query is required");

    verify(searchMangaRateLimiter, never()).check(any());
    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void searchManga_rejectsQueryLongerThan200Characters_withoutCallingMangaDex() {
    String query = " " + "a".repeat(201) + " ";

    assertThatThrownBy(() -> mangaService.searchManga(query))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("200");

    verify(searchMangaRateLimiter, never()).check(any());
    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void searchManga_throwsRateLimitException_withoutCallingMangaDexWhenLimitExceeded() {
    doThrow(new RateLimitExceededException("Too many searches"))
        .when(searchMangaRateLimiter)
        .check(USER_ID);

    assertThatThrownBy(() -> mangaService.searchManga("one"))
        .isInstanceOf(RateLimitExceededException.class);

    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void addManga_acceptsValidHttpsSourceUrl_andSavesManga() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(false);
    when(mangaDexClient.getManga(MANGADEX_ID))
        .thenReturn(
            new MangaDexManga(MANGADEX_ID, "One Piece", "Pirates", "https://img/one-piece.jpg"));
    when(mangaDexClient.latestEnglishChapter(MANGADEX_ID)).thenReturn(OptionalInt.of(1000));
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result =
        mangaService.addManga(
            MANGADEX_ID,
            " https://sakuramangas.org/obras/one-piece/ ",
            42,
            ReadingStatus.PLAN_TO_READ);

    assertThat(result.getTitle()).isEqualTo("One Piece");
    assertThat(result.getMangadexId()).isEqualTo(MANGADEX_ID);
    assertThat(result.getSourceUrl()).isEqualTo("https://sakuramangas.org/obras/one-piece/");
    assertThat(result.getLatestChapter()).isEqualTo(1000);
    assertThat(result.getCurrentChapter()).isEqualTo(42);
    assertThat(result.getCoverImageUrl()).isEqualTo("https://img/one-piece.jpg");
    assertThat(result.getReadingStatus()).isEqualTo(ReadingStatus.PLAN_TO_READ);
    assertThat(result.getLatestChapterAt()).isNotNull();
    assertThat(result.isNotificationsEnabled()).isTrue();
    assertThat(result.getOwnerId()).isEqualTo(USER_ID);
    verify(addMangaRateLimiter).check(USER_ID);
    verify(mangaRepository).save(any(Manga.class));
  }

  @Test
  void addManga_normalizesBlankSourceUrlToNull_andDefaultsOptionalFields() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(false);
    when(mangaDexClient.getManga(MANGADEX_ID))
        .thenReturn(new MangaDexManga(MANGADEX_ID, "One Piece", null, null));
    when(mangaDexClient.latestEnglishChapter(MANGADEX_ID)).thenReturn(OptionalInt.empty());
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result = mangaService.addManga(MANGADEX_ID, "   ", null, null);

    assertThat(result.getSourceUrl()).isNull();
    assertThat(result.getCurrentChapter()).isZero();
    assertThat(result.getLatestChapter()).isZero();
    assertThat(result.getLatestChapterAt()).isNull();
    assertThat(result.getReadingStatus()).isEqualTo(ReadingStatus.READING);
  }

  @Test
  void addManga_rejectsJavascriptSourceUrl() {
    assertThatThrownBy(() -> mangaService.addManga(MANGADEX_ID, "javascript:alert(1)", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sourceUrl must be an absolute http(s) URL");

    verify(addMangaRateLimiter, never()).check(any());
    verify(mangaRepository, never()).save(any());
    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void addManga_rejectsRelativeSourceUrl() {
    assertThatThrownBy(() -> mangaService.addManga(MANGADEX_ID, "/read/one-piece", null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sourceUrl must be an absolute http(s) URL");

    verify(addMangaRateLimiter, never()).check(any());
    verify(mangaRepository, never()).save(any());
    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void addManga_keepsStartingProgressVisible_whenMangaDexLatestIsLower() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(false);
    when(mangaDexClient.getManga(MANGADEX_ID))
        .thenReturn(new MangaDexManga(MANGADEX_ID, "One Piece", null, null));
    when(mangaDexClient.latestEnglishChapter(MANGADEX_ID)).thenReturn(OptionalInt.of(10));
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result = mangaService.addManga(MANGADEX_ID, null, 12, null);

    assertThat(result.getCurrentChapter()).isEqualTo(12);
    assertThat(result.getLatestChapter()).isEqualTo(12);
  }

  @Test
  void addManga_succeedsWithStartingChapter_whenLatestChapterLookupFails() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(false);
    when(mangaDexClient.getManga(MANGADEX_ID))
        .thenReturn(new MangaDexManga(MANGADEX_ID, "One Piece", null, null));
    when(mangaDexClient.latestEnglishChapter(MANGADEX_ID))
        .thenThrow(new MangaDexUpstreamException("MangaDex feed unavailable", 503, null));
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result = mangaService.addManga(MANGADEX_ID, null, 12, null);

    assertThat(result.getCurrentChapter()).isEqualTo(12);
    assertThat(result.getLatestChapter()).isEqualTo(12);
    assertThat(result.getLatestChapterAt()).isNotNull();
  }

  @Test
  void addManga_throwsDuplicateMangaException_whenMangaDexIdAlreadyExistsForUser() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(true);

    assertThatThrownBy(() -> mangaService.addManga(MANGADEX_ID, null, null, null))
        .isInstanceOf(DuplicateMangaException.class);

    verify(addMangaRateLimiter, never()).check(any());
    verifyNoInteractions(mangaDexClient);
  }

  @Test
  void addManga_throwsDuplicateMangaException_onConcurrentInsert() {
    when(mangaRepository.existsByMangadexIdAndOwnerId(MANGADEX_ID, USER_ID)).thenReturn(false);
    when(mangaDexClient.getManga(MANGADEX_ID))
        .thenReturn(new MangaDexManga(MANGADEX_ID, "One Piece", null, null));
    when(mangaDexClient.latestEnglishChapter(MANGADEX_ID)).thenReturn(OptionalInt.of(1000));
    when(mangaRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> mangaService.addManga(MANGADEX_ID, null, null, null))
        .isInstanceOf(DuplicateMangaException.class);
  }

  @Test
  void addManga_rejectsMissingMangaDexId() {
    assertThatThrownBy(() -> mangaService.addManga(null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mangaDexId");
  }

  @Test
  void addManga_rejectsNegativeStartingChapter() {
    assertThatThrownBy(() -> mangaService.addManga(MANGADEX_ID, null, -1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currentChapter");

    verify(mangaRepository, never()).save(any());
    verify(addMangaRateLimiter, never()).check(any());
    verifyNoInteractions(mangaDexClient);
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
  void getById_throwsMangaNotFoundException_forUnknownOrUnownedId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.getById(id)).isInstanceOf(MangaNotFoundException.class);
  }

  @Test
  void updateManga_updatesProgressStatusAndNotificationsEnabled() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.updateManga(id, false, 30, 120, ReadingStatus.COMPLETED);

    assertThat(result.isNotificationsEnabled()).isFalse();
    assertThat(result.getCurrentChapter()).isEqualTo(30);
    assertThat(result.getLatestChapter()).isEqualTo(120);
    assertThat(result.getReadingStatus()).isEqualTo(ReadingStatus.COMPLETED);
  }

  @Test
  void updateManga_throwsMangaNotFoundException_forCrossUserId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.updateManga(id, false, null, null, null))
        .isInstanceOf(MangaNotFoundException.class);
    verify(mangaRepository, never()).save(any());
  }

  @Test
  void updateManga_rejectsNegativeChapter() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findByIdAndOwnerId(id, USER_ID)).thenReturn(Optional.of(manga));

    assertThatThrownBy(() -> mangaService.updateManga(id, null, -1, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("currentChapter");

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
        .sourceUrl("https://sakuramangas.org/obras/test/")
        .mangadexId(MANGADEX_ID)
        .currentChapter(0)
        .latestChapter(latestChapter)
        .readingStatus(ReadingStatus.READING)
        .notificationsEnabled(true)
        .ownerId(USER_ID)
        .build();
  }
}
