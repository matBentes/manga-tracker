package com.mangaTracker.backend.service;

import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.model.NotificationLog;
import com.mangaTracker.backend.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a new chapter warrants a notification and, if so, delivers it via Web Push. The
 * {@link NotificationLog} dedup guarantees the user is not notified twice for the same chapter.
 */
@Service
@Transactional
public class NotificationService {

  private final NotificationLogRepository notificationLogRepository;
  private final PushNotificationService pushNotificationService;

  public NotificationService(
      NotificationLogRepository notificationLogRepository,
      PushNotificationService pushNotificationService) {
    this.notificationLogRepository = notificationLogRepository;
    this.pushNotificationService = pushNotificationService;
  }

  public void notify(Manga manga, int newLatestChapter) {
    if (!manga.isNotificationsEnabled()) {
      return;
    }
    if (newLatestChapter <= manga.getCurrentChapter()) {
      return;
    }
    if (notificationLogRepository.existsByMangaIdAndChapterNumber(
        manga.getId(), newLatestChapter)) {
      return;
    }

    // Save log BEFORE sending: if the DB write fails, no push is sent. This prevents duplicate
    // notifications on the next scraping poll.
    NotificationLog log =
        NotificationLog.builder().mangaId(manga.getId()).chapterNumber(newLatestChapter).build();
    notificationLogRepository.save(log);

    PushMessage message =
        new PushMessage(
            manga.getTitle(),
            "New chapter " + newLatestChapter + " is out",
            manga.getId(),
            manga.getOwnerId(),
            manga.getSourceUrl(),
            manga.getCoverImageUrl());
    pushNotificationService.send(message);
  }
}
