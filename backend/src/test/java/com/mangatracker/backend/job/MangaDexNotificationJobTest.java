package com.mangatracker.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangatracker.backend.exception.MangaDexUpstreamException;
import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.repository.MangaRepository;
import com.mangatracker.backend.service.MangaDexClient;
import com.mangatracker.backend.service.NotificationService;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MangaDexNotificationJobTest {

  @Mock private MangaRepository mangaRepository;
  @Mock private MangaDexClient mangaDexClient;
  @Mock private NotificationService notificationService;

  @InjectMocks private MangaDexNotificationJob notificationJob;

  @Test
  void runDailyCheck_updatesLatestChapterAndNotifies_whenNewChapterFound() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(manga));
    when(mangaDexClient.latestEnglishChapter(manga.getMangadexId())).thenReturn(OptionalInt.of(11));
    when(mangaRepository.save(manga)).thenReturn(manga);

    notificationJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(11);
    assertThat(manga.getLatestChapterAt()).isNotNull();
    assertThat(manga.getLastCheckedAt()).isNotNull();
    verify(notificationService).notify(manga, 11);
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_stampsLastCheckedAtOnly_whenNoNewChapter() {
    Manga manga = buildManga(UUID.randomUUID(), 10, 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(manga));
    when(mangaDexClient.latestEnglishChapter(manga.getMangadexId())).thenReturn(OptionalInt.of(10));
    when(mangaRepository.save(manga)).thenReturn(manga);

    notificationJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(10);
    assertThat(manga.getLatestChapterAt()).isNull();
    assertThat(manga.getLastCheckedAt()).isNotNull();
    verify(notificationService, never()).notify(any(), anyInt());
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_retriesNotification_whenLatestChapterWasAlreadyAdvanced() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(manga));
    when(mangaDexClient.latestEnglishChapter(manga.getMangadexId()))
        .thenReturn(OptionalInt.of(11), OptionalInt.of(11));
    when(mangaRepository.save(manga)).thenReturn(manga);
    doThrow(new RuntimeException("notification log write failed"))
        .doNothing()
        .when(notificationService)
        .notify(manga, 11);

    notificationJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(11);
    assertThat(manga.getLatestChapterAt()).isNotNull();
    assertThat(manga.getLastCheckedAt()).isNotNull();

    notificationJob.runDailyCheck();

    verify(notificationService, times(2)).notify(manga, 11);
    verify(mangaDexClient, times(2)).latestEnglishChapter(manga.getMangadexId());
  }

  @Test
  void runDailyCheck_stampsLastCheckedAtOnly_whenNoEnglishChapterFound() {
    Manga manga = buildManga(UUID.randomUUID(), 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(manga));
    when(mangaDexClient.latestEnglishChapter(manga.getMangadexId()))
        .thenReturn(OptionalInt.empty());
    when(mangaRepository.save(manga)).thenReturn(manga);

    notificationJob.runDailyCheck();

    assertThat(manga.getLatestChapter()).isEqualTo(10);
    assertThat(manga.getLatestChapterAt()).isNull();
    assertThat(manga.getLastCheckedAt()).isNotNull();
    verify(notificationService, never()).notify(any(), anyInt());
    verify(mangaRepository).save(manga);
  }

  @Test
  void runDailyCheck_stampsFailureAndContinues_whenOneMangaDexRequestFails() {
    Manga failing = buildManga(UUID.randomUUID(), 10);
    Manga next = buildManga(UUID.randomUUID(), 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(failing, next));
    when(mangaDexClient.latestEnglishChapter(failing.getMangadexId()))
        .thenThrow(new MangaDexUpstreamException("Network error", null));
    when(mangaDexClient.latestEnglishChapter(next.getMangadexId())).thenReturn(OptionalInt.of(12));
    when(mangaRepository.save(failing)).thenReturn(failing);
    when(mangaRepository.save(next)).thenReturn(next);

    notificationJob.runDailyCheck();

    assertThat(failing.getLatestChapter()).isEqualTo(10);
    assertThat(failing.getLastCheckedAt()).isNotNull();
    assertThat(next.getLatestChapter()).isEqualTo(12);
    assertThat(next.getLatestChapterAt()).isNotNull();
    assertThat(next.getLastCheckedAt()).isNotNull();
    verify(mangaRepository).save(failing);
    verify(mangaRepository).save(next);
    verify(notificationService).notify(next, 12);
  }

  @Test
  void runDailyCheck_fetchesOnceAndAppliesToAllRows_whenOwnersTrackSameMangaDexId() {
    UUID mangaDexId = UUID.randomUUID();
    Manga ownerA = buildManga(mangaDexId, 10);
    Manga ownerB = buildManga(mangaDexId, 10);
    when(mangaRepository.findAllByMangadexIdIsNotNullAndNotificationsEnabledTrue())
        .thenReturn(List.of(ownerA, ownerB));
    when(mangaDexClient.latestEnglishChapter(mangaDexId)).thenReturn(OptionalInt.of(12));
    when(mangaRepository.save(ownerA)).thenReturn(ownerA);
    when(mangaRepository.save(ownerB)).thenReturn(ownerB);

    notificationJob.runDailyCheck();

    assertThat(ownerA.getLatestChapter()).isEqualTo(12);
    assertThat(ownerA.getLatestChapterAt()).isNotNull();
    assertThat(ownerA.getLastCheckedAt()).isNotNull();
    assertThat(ownerB.getLatestChapter()).isEqualTo(12);
    assertThat(ownerB.getLatestChapterAt()).isNotNull();
    assertThat(ownerB.getLastCheckedAt()).isNotNull();
    verify(mangaDexClient, times(1)).latestEnglishChapter(mangaDexId);
    verify(notificationService).notify(ownerA, 12);
    verify(notificationService).notify(ownerB, 12);
  }

  private static Manga buildManga(UUID mangaDexId, int latestChapter) {
    return buildManga(mangaDexId, 0, latestChapter);
  }

  private static Manga buildManga(UUID mangaDexId, int currentChapter, int latestChapter) {
    return Manga.builder()
        .id(UUID.randomUUID())
        .title("Test Manga")
        .mangadexId(mangaDexId)
        .sourceUrl("https://sakuramangas.org/manga/test/")
        .currentChapter(currentChapter)
        .latestChapter(latestChapter)
        .notificationsEnabled(true)
        .ownerId(UUID.randomUUID())
        .build();
  }
}
