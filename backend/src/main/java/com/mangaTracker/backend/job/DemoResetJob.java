package com.mangaTracker.backend.job;

import com.mangaTracker.backend.model.AppUser;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.AppUserRepository;
import com.mangaTracker.backend.repository.MangaRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the public demo account's library clean for recruiters: on a configurable daily schedule it
 * purges every manga owned by the {@code demo} user and reseeds a small fixed library. Any changes
 * a visitor makes during the day are wiped overnight; the owner's private library is never touched.
 *
 * <p>Seed URLs point at the real {@code sakuramangas.org} pages so a visitor who opens a demo card
 * lands on the actual manga. Because {@code manga.source_url} is globally unique, the owner must
 * not track these same URLs in their private library, or one of the two inserts will conflict.
 */
@Component
@Order(1) // run after UserSeeder (@Order(0)) so the demo account exists before we seed its library
public class DemoResetJob implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(DemoResetJob.class);

  /** Username of the seeded demo account whose library is reset. */
  static final String DEMO_USERNAME = "demo";

  static final String SCHEDULE_ZONE = "America/Sao_Paulo";

  /**
   * Fixed demo library pointing at real {@code sakuramangas.org} pages. {@code manga.source_url} is
   * globally unique, so the owner must not track these same URLs in their private library.
   */
  private static final List<DemoEntry> DEMO_LIBRARY =
      List.of(
          new DemoEntry(
              "Gachiakuta",
              "https://sakuramangas.org/obras/gachiakuta/",
              95,
              168,
              "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/bx144946-cscic3n2SwdY.jpg"),
          new DemoEntry(
              "One Piece",
              "https://sakuramangas.org/obras/one-piece/",
              1050,
              1100,
              "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/bx30013-BeslEMqiPhlk.jpg"),
          new DemoEntry(
              "Jujutsu Kaisen",
              "https://sakuramangas.org/obras/jujutsu-kaisen/",
              230,
              245,
              "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/bx101517-H3TdM3g5ZUe9.jpg"),
          new DemoEntry(
              "Chainsaw Man",
              "https://sakuramangas.org/obras/chainsaw-man/",
              140,
              156,
              "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/bx105778-euxXZEIfDY2u.png"),
          new DemoEntry(
              "Dandadan",
              "https://sakuramangas.org/obras/dandadan/",
              150,
              168,
              "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/bx132029-prGF4gePdSKv.jpg"));

  private final MangaRepository mangaRepository;
  private final AppUserRepository appUserRepository;

  public DemoResetJob(MangaRepository mangaRepository, AppUserRepository appUserRepository) {
    this.mangaRepository = mangaRepository;
    this.appUserRepository = appUserRepository;
  }

  /**
   * Runs the demo reset on a configurable daily cron (default 04:00 America/Sao_Paulo).
   *
   * <p>{@code @Transactional} is declared here (not only on {@link #resetDemoData()}) because the
   * scheduled path invokes {@code resetDemoData()} via {@code this}, which bypasses the Spring
   * proxy and would otherwise skip transaction advice — leaving the delete + reseed non-atomic.
   */
  @Transactional
  @Scheduled(cron = "${app.demo.reset-cron:0 0 4 * * *}", zone = SCHEDULE_ZONE)
  public void runDailyReset() {
    LOG.info("DemoResetJob: running daily demo library reset");
    resetDemoData();
  }

  /**
   * Fills the demo library on startup so the public landing dashboard is never empty on a fresh
   * boot — without waiting for the daily cron. Delegates to {@link #seedDemoLibraryIfEmpty()}.
   *
   * <p>{@code @Transactional} is on this proxy entry point (not only the delegate) so the count +
   * seed run in one transaction; the internal {@code this} call would otherwise skip tx advice.
   */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedDemoLibraryIfEmpty();
  }

  /**
   * Seeds {@link #DEMO_LIBRARY} only when the demo account exists and its library is empty. Unlike
   * {@link #resetDemoData()} this never purges, so a restart mid-day preserves a visitor's edits;
   * the nightly cron is responsible for resetting them.
   */
  void seedDemoLibraryIfEmpty() {
    UUID demoId = appUserRepository.findByUsername(DEMO_USERNAME).map(AppUser::getId).orElse(null);
    if (demoId == null) {
      LOG.warn("DemoResetJob: no '{}' account found, skipping startup seed", DEMO_USERNAME);
      return;
    }
    if (mangaRepository.countByOwnerId(demoId) > 0) {
      LOG.info("DemoResetJob: demo library already populated, skipping startup seed");
      return;
    }

    List<Manga> seeded = DEMO_LIBRARY.stream().map(entry -> entry.toManga(demoId)).toList();
    mangaRepository.saveAll(seeded);
    LOG.info(
        "DemoResetJob: startup-seeded {} demo manga for account '{}'",
        seeded.size(),
        DEMO_USERNAME);
  }

  /**
   * Purges and reseeds the demo account's library. Idempotent: the resulting library is always the
   * fixed {@link #DEMO_LIBRARY}. No-op (with a warning) when the demo account is not seeded.
   */
  void resetDemoData() {
    UUID demoId = appUserRepository.findByUsername(DEMO_USERNAME).map(AppUser::getId).orElse(null);
    if (demoId == null) {
      LOG.warn("DemoResetJob: no '{}' account found, skipping reset", DEMO_USERNAME);
      return;
    }

    mangaRepository.deleteByOwnerId(demoId);

    List<Manga> seeded = DEMO_LIBRARY.stream().map(entry -> entry.toManga(demoId)).toList();
    mangaRepository.saveAll(seeded);
    LOG.info("DemoResetJob: reseeded {} demo manga for account '{}'", seeded.size(), DEMO_USERNAME);
  }

  /** Static seed definition; {@link #toManga(UUID)} builds a fresh demo-owned entity. */
  private record DemoEntry(
      String title, String sourceUrl, int currentChapter, int latestChapter, String coverUrl) {

    Manga toManga(UUID ownerId) {
      return Manga.builder()
          .title(title)
          .sourceUrl(sourceUrl)
          .currentChapter(currentChapter)
          .latestChapter(latestChapter)
          .coverImageUrl(coverUrl)
          .notificationsEnabled(false)
          .ownerId(ownerId)
          .build();
    }
  }
}
