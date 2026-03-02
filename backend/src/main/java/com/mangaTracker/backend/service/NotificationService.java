package com.mangaTracker.backend.service;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.model.NotificationLog;
import com.mangaTracker.backend.repository.NotificationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

  private final NotificationLogRepository notificationLogRepository;
  private final SettingsService settingsService;
  private final JavaMailSender mailSender;

  @Value("${app.notification.from-email}")
  private String fromEmail;

  public NotificationService(
      NotificationLogRepository notificationLogRepository,
      SettingsService settingsService,
      JavaMailSender mailSender) {
    this.notificationLogRepository = notificationLogRepository;
    this.settingsService = settingsService;
    this.mailSender = mailSender;
  }

  public void notify(Manga manga, int newLatestChapter) {
    if (!manga.isNotificationsEnabled()) {
      return;
    }
    if (newLatestChapter <= manga.getCurrentChapter()) {
      return;
    }
    AppSettings settings = settingsService.getSettings();
    if (!settings.isEmailNotificationsEnabled()) {
      return;
    }
    if (notificationLogRepository.existsByMangaIdAndChapterNumber(
        manga.getId(), newLatestChapter)) {
      return;
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(fromEmail);
    message.setTo(settings.getNotificationEmail());
    message.setSubject("New chapter available: " + manga.getTitle());
    message.setText(
        "Chapter "
            + newLatestChapter
            + " of \""
            + manga.getTitle()
            + "\" is now available.\n\n"
            + "Read it here: "
            + manga.getSourceUrl());
    mailSender.send(message);

    NotificationLog log =
        NotificationLog.builder().mangaId(manga.getId()).chapterNumber(newLatestChapter).build();
    notificationLogRepository.save(log);
  }
}
