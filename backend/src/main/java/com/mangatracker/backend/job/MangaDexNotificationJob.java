package com.mangatracker.backend.job;

import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.repository.MangaRepository;
import com.mangatracker.backend.service.MangaDexClient;
import com.mangatracker.backend.service.NotificationService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MangaDexNotificationJob {

  private static final Logger LOG = LoggerFactory.getLogger(MangaDexNotificationJob.class);

  /** Every day at 08:00 in {@link #SCHEDULE_ZONE}. */
  static final String DAILY_CHECK_CRON = "0 0 8 * * *";

  static final String SCHEDULE_ZONE = "America/Sao_Paulo";

  private final MangaRepository mangaRepository;
  private final MangaDexClient mangaDexClient;
  private final NotificationService notificationService;

  public MangaDexNotificationJob(
      MangaRepository mangaRepository,
      MangaDexClient mangaDexClient,
      NotificationService notificationService) {
    this.mangaRepository = mangaRepository;
    this.mangaDexClient = mangaDexClient;
    this.notificationService = notificationService;
  }

  /** Single scheduled check: once a day at 08:00 (America/Sao_Paulo). */
  @Scheduled(cron = DAILY_CHECK_CRON, zone = SCHEDULE_ZONE)
  public void runDailyCheck() {
    LOG.info("MangaDexNotificationJob: running daily 08:00 check");
    try {
      poll();
    } catch (Exception e) {
      LOG.error("MangaDexNotificationJob: failed to run daily check: {}", e.getMessage(), e);
    }
  }

  private void poll() {
    List<Manga> mangaList =
        mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue();
    LOG.info("MangaDexNotificationJob: polling {} MangaDex-backed manga entries", mangaList.size());
    mangaList.stream()
        .collect(
            Collectors.groupingBy(Manga::getMangadexId, LinkedHashMap::new, Collectors.toList()))
        .forEach(this::pollMangaDexId);
  }

  private void pollMangaDexId(UUID mangaDexId, List<Manga> mangaList) {
    try {
      OptionalInt latestEnglishChapter = mangaDexClient.latestEnglishChapter(mangaDexId);
      mangaList.forEach(manga -> pollManga(manga, latestEnglishChapter));
    } catch (Exception e) {
      LOG.error(
          "MangaDexNotificationJob: failed to poll MangaDex id {}: {}",
          mangaDexId,
          e.getMessage(),
          e);
      mangaList.forEach(this::stampFailure);
    }
  }

  private void pollManga(Manga manga, OptionalInt latestEnglishChapter) {
    try {
      LocalDateTime checkedAt = LocalDateTime.now();
      manga.setLastCheckedAt(checkedAt);
      if (latestEnglishChapter.isPresent()) {
        int fetchedChapter = latestEnglishChapter.getAsInt();
        if (fetchedChapter > manga.getLatestChapter()) {
          manga.setLatestChapter(fetchedChapter);
          manga.setLatestChapterAt(checkedAt);
        }
        mangaRepository.save(manga);
        if (fetchedChapter > manga.getCurrentChapter()) {
          notificationService.notify(manga, fetchedChapter);
        }
      } else {
        mangaRepository.save(manga);
      }
    } catch (Exception e) {
      LOG.error(
          "MangaDexNotificationJob: failed to update '{}' ({}): {}",
          manga.getTitle(),
          manga.getMangadexId(),
          e.getMessage(),
          e);
      stampFailure(manga);
    }
  }

  private void stampFailure(Manga manga) {
    manga.setLastCheckedAt(LocalDateTime.now());
    saveFailureStamp(manga);
  }

  private void saveFailureStamp(Manga manga) {
    try {
      mangaRepository.save(manga);
    } catch (Exception e) {
      LOG.error(
          "MangaDexNotificationJob: failed to save lastCheckedAt for '{}' ({}): {}",
          manga.getTitle(),
          manga.getMangadexId(),
          e.getMessage(),
          e);
    }
  }
}
