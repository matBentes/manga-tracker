package com.mangaTracker.backend.service;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.MangaScraper;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import com.mangaTracker.backend.security.AddMangaRateLimiter;
import com.mangaTracker.backend.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manga operations, all scoped to the authenticated user. Cross-user access (reading, updating, or
 * deleting another user's manga) yields {@link MangaNotFoundException} (HTTP 404) rather than a
 * forbidden response, so the API never reveals that an id exists for someone else.
 */
@Service
@Transactional
public class MangaService {

  private final MangaRepository mangaRepository;
  private final ScraperRegistry scraperRegistry;
  private final CurrentUser currentUser;
  private final AddMangaRateLimiter addMangaRateLimiter;

  public MangaService(
      MangaRepository mangaRepository,
      ScraperRegistry scraperRegistry,
      CurrentUser currentUser,
      AddMangaRateLimiter addMangaRateLimiter) {
    this.mangaRepository = mangaRepository;
    this.scraperRegistry = scraperRegistry;
    this.currentUser = currentUser;
    this.addMangaRateLimiter = addMangaRateLimiter;
  }

  public Manga addManga(String sourceUrl) {
    UUID ownerId = currentUser.requireId();
    addMangaRateLimiter.check(ownerId);
    // Check duplicate (scoped to this user) before resolving scraper to give accurate error
    if (mangaRepository.existsBySourceUrlAndOwnerId(sourceUrl, ownerId)) {
      throw new DuplicateMangaException("Manga already tracked: " + sourceUrl);
    }
    MangaScraper scraper = scraperRegistry.resolve(sourceUrl);
    ScrapedManga scraped = scraper.scrape(sourceUrl);
    Manga manga =
        Manga.builder()
            .title(scraped.title())
            .sourceUrl(sourceUrl)
            .currentChapter(0)
            .latestChapter(scraped.latestChapter())
            .coverImageUrl(scraped.coverImageUrl())
            .latestChapterAt(LocalDateTime.now())
            .notificationsEnabled(true)
            .ownerId(ownerId)
            .build();
    try {
      return mangaRepository.save(manga);
    } catch (DataIntegrityViolationException e) {
      // Handles race condition where two concurrent requests pass the duplicate check
      throw new DuplicateMangaException("Manga already tracked: " + sourceUrl);
    }
  }

  @Transactional(readOnly = true)
  public List<Manga> listManga() {
    return mangaRepository.findAllByOwnerIdOrderByUpdatedAtDesc(currentUser.requireId());
  }

  @Transactional(readOnly = true)
  public Manga getById(UUID id) {
    return requireOwned(id);
  }

  public Manga updateManga(UUID id, Boolean notificationsEnabled) {
    Manga manga = requireOwned(id);
    if (notificationsEnabled != null) {
      manga.setNotificationsEnabled(notificationsEnabled);
    }
    return mangaRepository.save(manga);
  }

  /** Mark a manga as fully read: caught up to its latest known chapter. */
  public Manga markRead(UUID id) {
    return setReadState(id, true);
  }

  /** Mark a manga as unread again (undo): resets progress so it shows as having new chapters. */
  public Manga markUnread(UUID id) {
    return setReadState(id, false);
  }

  private Manga setReadState(UUID id, boolean read) {
    Manga manga = requireOwned(id);
    manga.setCurrentChapter(read ? manga.getLatestChapter() : 0);
    return mangaRepository.save(manga);
  }

  public void deleteManga(UUID id) {
    mangaRepository.delete(requireOwned(id));
  }

  /**
   * Loads a manga owned by the current user, or throws {@link MangaNotFoundException} (404) — which
   * is also what another user's id produces, preventing existence leaks.
   */
  private Manga requireOwned(UUID id) {
    return mangaRepository
        .findByIdAndOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new MangaNotFoundException("Manga not found: " + id));
  }
}
