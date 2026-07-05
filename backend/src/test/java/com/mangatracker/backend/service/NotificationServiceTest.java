package com.mangatracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.repository.NotificationLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationLogRepository notificationLogRepository;
  @Mock private PushNotificationService pushNotificationService;

  @InjectMocks private NotificationService notificationService;

  @Test
  void notify_skips_whenNotificationsDisabled() {
    Manga manga = buildManga(UUID.randomUUID(), false, 0, 10);

    notificationService.notify(manga, 11);

    verify(pushNotificationService, never()).send(any());
  }

  @Test
  void notify_skips_whenNewChapterNotGreaterThanCurrent() {
    Manga manga = buildManga(UUID.randomUUID(), true, 11, 11);

    notificationService.notify(manga, 11);

    verify(pushNotificationService, never()).send(any());
  }

  @Test
  void notify_skips_whenAlreadyNotified() {
    UUID mangaId = UUID.randomUUID();
    Manga manga = buildManga(mangaId, true, 0, 10);
    when(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 11)).thenReturn(true);

    notificationService.notify(manga, 11);

    verify(pushNotificationService, never()).send(any());
  }

  @Test
  void notify_sendsPushAndSavesLog_whenAllConditionsMet() {
    UUID mangaId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Manga manga = buildManga(mangaId, true, 0, 10);
    manga.setOwnerId(ownerId);
    manga.setSourceUrl(null);
    when(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 11)).thenReturn(false);

    notificationService.notify(manga, 11);

    verify(notificationLogRepository).save(any());
    ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
    verify(pushNotificationService).send(captor.capture());
    PushMessage sent = captor.getValue();
    assertThat(sent.title()).isEqualTo("Test Manga");
    assertThat(sent.body()).contains("11");
    assertThat(sent.mangaId()).isEqualTo(mangaId);
    assertThat(sent.ownerId()).isEqualTo(ownerId);
    assertThat(sent.sourceUrl()).isNull();
  }

  @Test
  void notify_savesLogBeforeSending_forDedup() {
    UUID mangaId = UUID.randomUUID();
    Manga manga = buildManga(mangaId, true, 0, 10);
    when(notificationLogRepository.existsByMangaIdAndChapterNumber(mangaId, 11)).thenReturn(false);

    notificationService.notify(manga, 11);

    var inOrder = org.mockito.Mockito.inOrder(notificationLogRepository, pushNotificationService);
    inOrder.verify(notificationLogRepository).save(any());
    inOrder.verify(pushNotificationService).send(any());
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
}
