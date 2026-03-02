package com.mangaTracker.backend.service;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.repository.MangaRepository;
import com.mangaTracker.backend.scraper.MangaScraper;
import com.mangaTracker.backend.scraper.ScrapedManga;
import com.mangaTracker.backend.scraper.ScraperRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MangaService {

  private final MangaRepository mangaRepository;
  private final ScraperRegistry scraperRegistry;

  public MangaService(MangaRepository mangaRepository, ScraperRegistry scraperRegistry) {
    this.mangaRepository = mangaRepository;
    this.scraperRegistry = scraperRegistry;
  }

  public Manga addManga(String sourceUrl) {
    MangaScraper scraper = scraperRegistry.resolve(sourceUrl);
    if (mangaRepository.existsBySourceUrl(sourceUrl)) {
      throw new DuplicateMangaException("Manga already tracked: " + sourceUrl);
    }
    ScrapedManga scraped = scraper.scrape(sourceUrl);
    Manga manga =
        Manga.builder()
            .title(scraped.title())
            .sourceUrl(sourceUrl)
            .currentChapter(0)
            .latestChapter(scraped.latestChapter())
            .notificationsEnabled(true)
            .build();
    return mangaRepository.save(manga);
  }

  @Transactional(readOnly = true)
  public List<Manga> listManga() {
    return mangaRepository.findAllByOrderByUpdatedAtDesc();
  }

  public Manga updateManga(UUID id, Integer currentChapter, Boolean notificationsEnabled) {
    Manga manga =
        mangaRepository
            .findById(id)
            .orElseThrow(() -> new MangaNotFoundException("Manga not found: " + id));
    if (currentChapter != null) {
      if (currentChapter < 0 || currentChapter > manga.getLatestChapter()) {
        throw new IllegalArgumentException(
            "currentChapter must be between 0 and " + manga.getLatestChapter());
      }
      manga.setCurrentChapter(currentChapter);
    }
    if (notificationsEnabled != null) {
      manga.setNotificationsEnabled(notificationsEnabled);
    }
    return mangaRepository.save(manga);
  }

  public void deleteManga(UUID id) {
    if (!mangaRepository.existsById(id)) {
      throw new MangaNotFoundException("Manga not found: " + id);
    }
    mangaRepository.deleteById(id);
  }
}
