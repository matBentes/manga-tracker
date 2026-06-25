package com.mangaTracker.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.exception.ScrapingException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.MangaScraper;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import com.mangaTracker.backend.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapingJobTest {

  @Mock private MangaRepository mangaRepository;
  @Mock private ScraperRegistry scraperRegistry;
  @Mock private NotificationService notificationService;

  @InjectMocks private ScrapingJob scrapingJob;

  @Test
  void runDailyCheck_updatesLatestChapterAndNotifies_whenNewChapterFound() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    MangaScraper scraper = mock(MangaScraper.class);
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(manga));
    when(scraperRegistry.resolve(manga.getSourceUrl())).thenReturn(scraper);
    when(scraper.scrape(manga.getSourceUrl())).thenReturn(new ScrapedManga("Test Manga", 11, null));
    when(mangaRepository.save(manga)).thenReturn(manga);

    scrapingJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(11);
    assertThat(manga.getLastCheckedAt()).isNotNull();
    verify(notificationService).notify(manga, 11);
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_refreshesCoverImage_whenScraped() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    MangaScraper scraper = mock(MangaScraper.class);
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(manga));
    when(scraperRegistry.resolve(manga.getSourceUrl())).thenReturn(scraper);
    when(scraper.scrape(manga.getSourceUrl()))
        .thenReturn(new ScrapedManga("Test Manga", 10, "https://img/cover.jpg"));
    when(mangaRepository.save(manga)).thenReturn(manga);

    scrapingJob.runDailyCheck();

    assertThat(manga.getCoverImageUrl()).isEqualTo("https://img/cover.jpg");
  }

  @Test
  void runDailyCheck_savesWithoutNotifying_whenNoNewChapter() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    MangaScraper scraper = mock(MangaScraper.class);
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(manga));
    when(scraperRegistry.resolve(manga.getSourceUrl())).thenReturn(scraper);
    when(scraper.scrape(manga.getSourceUrl())).thenReturn(new ScrapedManga("Test Manga", 10, null));
    when(mangaRepository.save(manga)).thenReturn(manga);

    scrapingJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(10);
    verify(notificationService, never()).notify(any(), anyInt());
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_handlesExceptionAndContinues_whenScrapingFails() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(manga));
    when(scraperRegistry.resolve(manga.getSourceUrl()))
        .thenThrow(new ScrapingException("Network error"));
    when(mangaRepository.save(manga)).thenReturn(manga);

    scrapingJob.runDailyCheck();

    assertThat(manga.getLastCheckedAt()).isNotNull();
    verify(notificationService, never()).notify(any(), anyInt());
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_doesNothing_whenListIsEmpty() {
    when(mangaRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());

    scrapingJob.runDailyCheck();

    verify(mangaRepository, never()).save(any());
    verify(notificationService, never()).notify(any(), anyInt());
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
