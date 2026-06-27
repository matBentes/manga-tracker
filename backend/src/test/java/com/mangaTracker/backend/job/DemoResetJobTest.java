package com.mangaTracker.backend.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.model.AppUser;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.model.Role;
import com.mangaTracker.backend.repository.AppUserRepository;
import com.mangaTracker.backend.repository.MangaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DemoResetJobTest {

  @Mock private MangaRepository mangaRepository;
  @Mock private AppUserRepository appUserRepository;

  private DemoResetJob job() {
    return new DemoResetJob(mangaRepository, appUserRepository);
  }

  private void demoExists(UUID demoId) {
    when(appUserRepository.findByUsername("demo"))
        .thenReturn(
            Optional.of(AppUser.builder().id(demoId).username("demo").role(Role.DEMO).build()));
  }

  @SuppressWarnings("unchecked")
  private List<Manga> captureSaved() {
    ArgumentCaptor<List<Manga>> captor = ArgumentCaptor.forClass(List.class);
    verify(mangaRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  void resetDemoData_deletesDemoOwnedMangaThenReseeds_scopedToDemoOwner() {
    UUID demoId = UUID.randomUUID();
    demoExists(demoId);

    job().resetDemoData();

    // Delete must happen before reseed, and only the demo owner is purged.
    InOrder order = inOrder(mangaRepository);
    order.verify(mangaRepository).deleteByOwnerId(demoId);
    order.verify(mangaRepository).saveAll(any());

    List<Manga> seeded = captureSaved();
    assertThat(seeded).isNotEmpty();
    assertThat(seeded)
        .allMatch(
            m ->
                demoId.equals(m.getOwnerId())
                    && m.getSourceUrl() != null
                    && !m.getSourceUrl().isBlank()
                    && m.getTitle() != null
                    && !m.getTitle().isBlank()
                    && m.getLatestChapter() >= m.getCurrentChapter());
  }

  @Test
  void resetDemoData_seedUrlsAreUnique_toAvoidGlobalUniqueConstraintConflict() {
    UUID demoId = UUID.randomUUID();
    demoExists(demoId);

    job().resetDemoData();

    List<Manga> seeded = captureSaved();
    long distinctUrls = seeded.stream().map(Manga::getSourceUrl).distinct().count();
    assertThat(distinctUrls).isEqualTo(seeded.size());
  }

  @Test
  void resetDemoData_isIdempotent_sameSeedSetAcrossRuns() {
    UUID demoId = UUID.randomUUID();
    demoExists(demoId);

    DemoResetJob job = job();
    job.resetDemoData();
    List<String> firstUrls =
        captureSavedAll().stream().flatMap(List::stream).map(Manga::getSourceUrl).sorted().toList();

    // Second run captured together with first via captureSavedAll below; assert determinism.
    job.resetDemoData();
    List<List<Manga>> allRuns = captureSavedAll();
    assertThat(allRuns).hasSize(2);
    List<String> run1 = allRuns.get(0).stream().map(Manga::getSourceUrl).sorted().toList();
    List<String> run2 = allRuns.get(1).stream().map(Manga::getSourceUrl).sorted().toList();
    assertThat(run2).isEqualTo(run1);
    assertThat(firstUrls).isEqualTo(run1);
  }

  @SuppressWarnings("unchecked")
  private List<List<Manga>> captureSavedAll() {
    ArgumentCaptor<List<Manga>> captor = ArgumentCaptor.forClass(List.class);
    verify(mangaRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
    return captor.getAllValues();
  }

  @Test
  void resetDemoData_doesNothing_whenDemoAccountMissing() {
    when(appUserRepository.findByUsername("demo")).thenReturn(Optional.empty());

    job().resetDemoData();

    verify(mangaRepository, never()).deleteByOwnerId(any());
    verify(mangaRepository, never()).saveAll(any());
  }

  @Test
  void seedDemoLibraryIfEmpty_seedsLibrary_whenDemoEmpty() {
    UUID demoId = UUID.randomUUID();
    demoExists(demoId);
    when(mangaRepository.countByOwnerId(demoId)).thenReturn(0L);

    job().seedDemoLibraryIfEmpty();

    // Startup seed never purges; it only fills an empty library.
    verify(mangaRepository, never()).deleteByOwnerId(any());
    List<Manga> seeded = captureSaved();
    assertThat(seeded).isNotEmpty();
    assertThat(seeded).allMatch(m -> demoId.equals(m.getOwnerId()));
  }

  @Test
  void seedDemoLibraryIfEmpty_doesNothing_whenDemoAlreadyPopulated() {
    UUID demoId = UUID.randomUUID();
    demoExists(demoId);
    when(mangaRepository.countByOwnerId(demoId)).thenReturn(5L);

    job().seedDemoLibraryIfEmpty();

    verify(mangaRepository, never()).saveAll(any());
    verify(mangaRepository, never()).deleteByOwnerId(any());
  }

  @Test
  void seedDemoLibraryIfEmpty_doesNothing_whenDemoAccountMissing() {
    when(appUserRepository.findByUsername("demo")).thenReturn(Optional.empty());

    job().seedDemoLibraryIfEmpty();

    verify(mangaRepository, never()).saveAll(any());
  }
}
