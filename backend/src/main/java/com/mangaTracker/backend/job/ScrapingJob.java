package com.mangaTracker.backend.job;

import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import com.mangaTracker.backend.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScrapingJob {

  private static final Logger LOG = LoggerFactory.getLogger(ScrapingJob.class);

  private final MangaRepository mangaRepository;
  private final ScraperRegistry scraperRegistry;
  private final NotificationService notificationService;

  public ScrapingJob(
      MangaRepository mangaRepository,
      ScraperRegistry scraperRegistry,
      NotificationService notificationService) {
    this.mangaRepository = mangaRepository;
    this.scraperRegistry = scraperRegistry;
    this.notificationService = notificationService;
  }

  // NOTE: This interval is resolved from application properties at startup.
  // Changes to AppSettings.pollIntervalMinutes at runtime do NOT affect this schedule.
  @Scheduled(fixedDelayString = "#{${app.scraper.poll-interval-minutes:30} * 60000}")
  public void pollAllManga() {
    List<Manga> mangaList = mangaRepository.findAllByOrderByUpdatedAtDesc();
    LOG.info("ScrapingJob: polling {} manga entries", mangaList.size());
    for (Manga manga : mangaList) {
      try {
        ScrapedManga scraped =
            scraperRegistry.resolve(manga.getSourceUrl()).scrape(manga.getSourceUrl());
        manga.setLastCheckedAt(LocalDateTime.now());
        if (scraped.latestChapter() > manga.getLatestChapter()) {
          int newLatestChapter = scraped.latestChapter();
          manga.setLatestChapter(newLatestChapter);
          mangaRepository.save(manga);
          notificationService.notify(manga, newLatestChapter);
        } else {
          mangaRepository.save(manga);
        }
      } catch (Exception e) {
        LOG.error(
            "ScrapingJob: failed to poll '{}' ({}): {}",
            manga.getTitle(),
            manga.getSourceUrl(),
            e.getMessage());
        manga.setLastCheckedAt(LocalDateTime.now());
        mangaRepository.save(manga);
      }
    }
  }
}
