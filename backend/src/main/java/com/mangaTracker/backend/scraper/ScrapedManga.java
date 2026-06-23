package com.mangaTracker.backend.scraper;

/**
 * Result of scraping a manga source page.
 *
 * @param coverImageUrl absolute URL of the cover image, or {@code null} if none was found.
 */
public record ScrapedManga(String title, int latestChapter, String coverImageUrl) {}
