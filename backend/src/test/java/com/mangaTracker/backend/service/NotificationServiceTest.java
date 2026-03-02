package com.mangaTracker.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.model.AppSettings;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.NotificationLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationLogRepository notificationLogRepository;
  @Mock private SettingsService settingsService;
  @Mock private JavaMailSender mailSender;

  @InjectMocks private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(notificationService, "fromEmail", "noreply@manga.test");
  }

  @Test
  void notify_skips_whenNotificationsDisabled() {
    Manga manga = buildManga(UUID.randomUUID(), false, 0, 10);

    notificationService.notify(manga, 11);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notify_skips_whenNewChapterNotGreaterThanCurrent() {
    Manga manga = buildManga(UUID.randomUUID(), true, 11, 11);

    notificationService.notify(manga, 11);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notify_skips_whenEmailNotificationsDisabled() {
    Manga manga = buildManga(UUID.randomUUID(), true, 0, 10);
    AppSettings settings = buildSettings(false, "user@test.com");
    when(settingsService.getSettings()).thenReturn(settings);

    notificationService.notify(manga, 11);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notify_skips_whenAlreadyNotified() {
    UUID mangaId = UUID.randomUUID();
    Manga manga = buildManga(mangaId, true, 0, 10);
    AppSettings settings = buildSettings(true, "user@test.com");
    when(settingsService.getSettings()).thenReturn(settings);
    when(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 11)).thenReturn(true);

    notificationService.notify(manga, 11);

    verify(mailSender, never()).send(any(SimpleMailMessage.class));
  }

  @Test
  void notify_sendsEmailAndSavesLog_whenAllConditionsMet() {
    UUID mangaId = UUID.randomUUID();
    Manga manga = buildManga(mangaId, true, 0, 10);
    AppSettings settings = buildSettings(true, "user@test.com");
    when(settingsService.getSettings()).thenReturn(settings);
    when(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 11)).thenReturn(false);

    notificationService.notify(manga, 11);

    verify(mailSender).send(any(SimpleMailMessage.class));
    verify(notificationLogRepository).save(any());
  }

  private static Manga buildManga(
      UUID id, boolean notificationsEnabled, int currentChapter, int latestChapter) {
    return Manga.builder()
        .id(id)
        .title("Test Manga")
        .sourceUrl("https://sakuramangas.org/manga/test/")
        .currentChapter(currentChapter)
        .latestChapter(latestChapter)
        .notificationsEnabled(notificationsEnabled)
        .build();
  }

  private static AppSettings buildSettings(boolean emailEnabled, String email) {
    return AppSettings.builder()
        .id(1)
        .emailNotificationsEnabled(emailEnabled)
        .notificationEmail(email)
        .pollIntervalMinutes(30)
        .build();
  }
}
