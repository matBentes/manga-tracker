package com.mangaTracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.exception.UnsupportedSourceException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.MangaScraper;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MangaServiceTest {

  @Mock private MangaRepository mangaRepository;

  @Mock private ScraperRegistry scraperRegistry;

  @InjectMocks private MangaService mangaService;

  @Test
  void addManga_savesAndReturnsManga() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    MangaScraper scraper = mock(MangaScraper.class);
    when(scraperRegistry.resolve(url)).thenReturn(scraper);
    when(scraper.scrape(url)).thenReturn(new ScrapedManga("One Piece", 1000));
    when(mangaRepository.existsBySourceUrl(url)).thenReturn(false);
    when(mangaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    Manga result = mangaService.addManga(url);

    assertThat(result.getTitle()).isEqualTo("One Piece");
    assertThat(result.getLatestChapter()).isEqualTo(1000);
    assertThat(result.getCurrentChapter()).isEqualTo(0);
    assertThat(result.isNotificationsEnabled()).isTrue();
    verify(mangaRepository).save(any(Manga.class));
  }

  @Test
  void addManga_throwsDuplicateMangaException_whenUrlAlreadyExists() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    when(mangaRepository.existsBySourceUrl(url)).thenReturn(true);

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(DuplicateMangaException.class);
  }

  @Test
  void addManga_throwsDuplicateMangaException_onConcurrentInsert() {
    String url = "https://sakuramangas.org/manga/one-piece/";
    MangaScraper scraper = mock(MangaScraper.class);
    when(mangaRepository.existsBySourceUrl(url)).thenReturn(false);
    when(scraperRegistry.resolve(url)).thenReturn(scraper);
    when(scraper.scrape(url)).thenReturn(new ScrapedManga("One Piece", 1000));
    when(mangaRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(DuplicateMangaException.class);
  }

  @Test
  void addManga_throwsUnsupportedSourceException_forUnsupportedUrl() {
    String url = "https://unknown.com/manga/test/";
    when(scraperRegistry.resolve(url))
        .thenThrow(new UnsupportedSourceException("No scraper found for URL: " + url));

    assertThatThrownBy(() -> mangaService.addManga(url))
        .isInstanceOf(UnsupportedSourceException.class);
  }

  @Test
  void listManga_returnsAllSortedByUpdatedAtDesc() {
    List<Manga> expected = List.of(buildManga(UUID.randomUUID(), 10));
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(expected);

    List<Manga> result = mangaService.listManga();

    assertThat(result).isSameAs(expected);
  }

  @Test
  void updateManga_updatesCurrentChapter() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findById(id)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.updateManga(id, 50, null);

    assertThat(result.getCurrentChapter()).isEqualTo(50);
  }

  @Test
  void updateManga_updatesNotificationsEnabled() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findById(id)).thenReturn(Optional.of(manga));
    when(mangaRepository.save(manga)).thenReturn(manga);

    Manga result = mangaService.updateManga(id, null, false);

    assertThat(result.isNotificationsEnabled()).isFalse();
  }

  @Test
  void updateManga_throwsIllegalArgumentException_whenCurrentChapterExceedsLatest() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findById(id)).thenReturn(Optional.of(manga));

    assertThatThrownBy(() -> mangaService.updateManga(id, 101, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateManga_throwsIllegalArgumentException_whenCurrentChapterNegative() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findById(id)).thenReturn(Optional.of(manga));

    assertThatThrownBy(() -> mangaService.updateManga(id, -1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateManga_throwsMangaNotFoundException_forUnknownId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.updateManga(id, 0, null))
        .isInstanceOf(MangaNotFoundException.class);
  }

  @Test
  void deleteManga_deletesById() {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id, 100);
    when(mangaRepository.findById(id)).thenReturn(Optional.of(manga));

    mangaService.deleteManga(id);

    verify(mangaRepository).delete(manga);
  }

  @Test
  void deleteManga_throwsMangaNotFoundException_forUnknownId() {
    UUID id = UUID.randomUUID();
    when(mangaRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> mangaService.deleteManga(id))
        .isInstanceOf(MangaNotFoundException.class);
  }

  private static Manga buildManga(UUID id, int latestChapter) {
    return Manga.builder()
        .id(id)
        .title("Test Manga")
        .sourceUrl("https://sakuramangas.org/manga/test/")
        .currentChapter(0)
        .latestChapter(latestChapter)
        .notificationsEnabled(true)
        .build();
  }
}
