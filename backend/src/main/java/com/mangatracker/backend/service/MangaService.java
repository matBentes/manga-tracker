package com.mangatracker.backend.service;

import com.mangatracker.backend.exception.DuplicateMangaException;
import com.mangatracker.backend.exception.MangaDexUpstreamException;
import com.mangatracker.backend.exception.MangaNotFoundException;
import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.model.ReadingStatus;
import com.mangatracker.backend.repository.MangaRepository;
import com.mangatracker.backend.security.AddMangaRateLimiter;
import com.mangatracker.backend.security.CurrentUser;
import com.mangatracker.backend.security.SearchMangaRateLimiter;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final int MAX_SEARCH_QUERY_LENGTH = 200;
  private static final String INVALID_SOURCE_URL_MESSAGE =
      "sourceUrl must be an absolute http(s) URL";
  private static final Logger LOG = LoggerFactory.getLogger(MangaService.class);

  private final MangaRepository mangaRepository;
  private final MangaDexClient mangaDexClient;
  private final CurrentUser currentUser;
  private final AddMangaRateLimiter addMangaRateLimiter;
  private final SearchMangaRateLimiter searchMangaRateLimiter;

  public MangaService(
      MangaRepository mangaRepository,
      MangaDexClient mangaDexClient,
      CurrentUser currentUser,
      AddMangaRateLimiter addMangaRateLimiter,
      SearchMangaRateLimiter searchMangaRateLimiter) {
    this.mangaRepository = mangaRepository;
    this.mangaDexClient = mangaDexClient;
    this.currentUser = currentUser;
    this.addMangaRateLimiter = addMangaRateLimiter;
    this.searchMangaRateLimiter = searchMangaRateLimiter;
  }

  @Transactional(readOnly = true)
  public List<MangaDexManga> searchManga(String query) {
    String normalizedQuery = requireSearchQuery(query);
    searchMangaRateLimiter.check(currentUser.requireId());
    return mangaDexClient.search(normalizedQuery);
  }

  public Manga addManga(
      UUID mangaDexId, String sourceUrl, Integer currentChapter, ReadingStatus readingStatus) {
    if (mangaDexId == null) {
      throw new IllegalArgumentException("mangaDexId is required");
    }
    int startingChapter = nonNegativeOrDefault(currentChapter, 0, "currentChapter");
    String normalizedSourceUrl = optionalSourceUrl(sourceUrl);
    UUID ownerId = currentUser.requireId();
    // Reject duplicates (scoped to this user) before consuming rate-limit quota, so retrying an
    // already-tracked MangaDex title doesn't burn the limiter, and so the error is accurate.
    if (mangaRepository.existsByMangadexIdAndOwnerId(mangaDexId, ownerId)) {
      throw new DuplicateMangaException("Manga already tracked: " + mangaDexId);
    }
    addMangaRateLimiter.check(ownerId);
    MangaDexManga metadata = mangaDexClient.getManga(mangaDexId);
    OptionalInt latestEnglishChapter;
    try {
      latestEnglishChapter = mangaDexClient.latestEnglishChapter(mangaDexId);
    } catch (MangaDexUpstreamException e) {
      LOG.warn(
          "Unable to fetch latest English MangaDex chapter for {}; adding manga without it",
          mangaDexId,
          e);
      latestEnglishChapter = OptionalInt.empty();
    }
    int latestChapter =
        Math.max(
            latestEnglishChapter.isPresent() ? latestEnglishChapter.getAsInt() : 0,
            startingChapter);
    Manga manga =
        Manga.builder()
            .title(metadata.title())
            .sourceUrl(normalizedSourceUrl)
            .mangadexId(mangaDexId)
            .currentChapter(startingChapter)
            .latestChapter(latestChapter)
            .coverImageUrl(metadata.coverImageUrl())
            .latestChapterAt(latestChapter > 0 ? LocalDateTime.now() : null)
            .readingStatus(readingStatus == null ? ReadingStatus.READING : readingStatus)
            .notificationsEnabled(true)
            .ownerId(ownerId)
            .build();
    try {
      return mangaRepository.save(manga);
    } catch (DataIntegrityViolationException e) {
      // Handles race condition where two concurrent requests pass the duplicate check
      throw new DuplicateMangaException("Manga already tracked: " + mangaDexId);
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

  public Manga updateManga(
      UUID id,
      Boolean notificationsEnabled,
      Integer currentChapter,
      Integer latestChapter,
      ReadingStatus readingStatus) {
    Manga manga = requireOwned(id);
    if (notificationsEnabled != null) {
      manga.setNotificationsEnabled(notificationsEnabled);
    }
    if (currentChapter != null) {
      manga.setCurrentChapter(nonNegativeOrDefault(currentChapter, 0, "currentChapter"));
    }
    if (latestChapter != null) {
      manga.setLatestChapter(nonNegativeOrDefault(latestChapter, 0, "latestChapter"));
    }
    if (readingStatus != null) {
      manga.setReadingStatus(readingStatus);
    }
    if (manga.getCurrentChapter() > manga.getLatestChapter()) {
      manga.setLatestChapter(manga.getCurrentChapter());
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
   * Loads a manga owned by the current user, or throws {@link MangaNotFoundException} (404), which
   * is also what another user's id produces, preventing existence leaks.
   */
  private Manga requireOwned(UUID id) {
    return mangaRepository
        .findByIdAndOwnerId(id, currentUser.requireId())
        .orElseThrow(() -> new MangaNotFoundException("Manga not found: " + id));
  }

  private static int nonNegativeOrDefault(Integer value, int defaultValue, String fieldName) {
    if (value == null) {
      return defaultValue;
    }
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be non-negative");
    }
    return value;
  }

  private static String requireSearchQuery(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Search query is required");
    }
    String trimmed = value.trim();
    if (trimmed.length() > MAX_SEARCH_QUERY_LENGTH) {
      throw new IllegalArgumentException("Search query must be 200 characters or fewer");
    }
    return trimmed;
  }

  private static String optionalSourceUrl(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(INVALID_SOURCE_URL_MESSAGE);
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null
        || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
        || host == null
        || host.isBlank()) {
      throw new IllegalArgumentException(INVALID_SOURCE_URL_MESSAGE);
    }
    return trimmed;
  }
}
