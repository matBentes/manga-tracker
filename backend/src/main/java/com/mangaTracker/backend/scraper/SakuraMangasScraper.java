package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class SakuraMangasScraper implements MangaScraper {

  private static final String SUPPORTED_HOST = "sakuramangas.org";
  private static final int MAX_ATTEMPTS = 4;
  private static final long DEFAULT_BASE_DELAY_MS = 1000L;
  private static final int TIMEOUT_MS = 15000;
  // Keyword-anchored pattern tried first (e.g. "Capítulo 180", "Chapter 180")
  private static final Pattern CHAPTER_KEYWORD_PATTERN =
      Pattern.compile("(?:chapter|cap[ií]tulo|cap\\.?|ch\\.?)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
  // Fallback: first standalone number in the text
  private static final Pattern CHAPTER_PATTERN = Pattern.compile("(\\d+)(\\.\\d+)?");

  private static final String[] TITLE_SELECTORS = {
    "h1.post-title", "div.post-title h1", "h1.manga-title", ".manga-title", "h1"
  };

  private static final String[] CHAPTER_SELECTORS = {
    "ul.wp-manga-chapter a",
    "ul.version-chap li:first-child a",
    ".chapter-list li:first-child a",
    "ul.row-content-chapter li:first-child a"
  };

  @FunctionalInterface
  interface DocumentLoader {
    Document load(String url) throws IOException;
  }

  private final DocumentLoader loader;
  private final long baseDelayMs;

  public SakuraMangasScraper() {
    this(
        url -> Jsoup.connect(url).timeout(TIMEOUT_MS).userAgent("Mozilla/5.0").get(),
        DEFAULT_BASE_DELAY_MS);
  }

  SakuraMangasScraper(DocumentLoader loader) {
    this(loader, DEFAULT_BASE_DELAY_MS);
  }

  SakuraMangasScraper(DocumentLoader loader, long baseDelayMs) {
    this.loader = loader;
    this.baseDelayMs = baseDelayMs;
  }

  @Override
  public boolean supports(String url) {
    return url != null && url.contains(SUPPORTED_HOST);
  }

  @Override
  public ScrapedManga scrape(String url) throws ScrapingException {
    Document doc = fetchWithRetry(url);
    String title = extractTitle(doc, url);
    int latestChapter = extractLatestChapter(doc, url);
    return new ScrapedManga(title, latestChapter);
  }

  private Document fetchWithRetry(String url) {
    IOException lastException = null;
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      if (attempt > 0) {
        sleepWithBackoff(attempt - 1);
      }
      try {
        return loader.load(url);
      } catch (IOException e) {
        lastException = e;
      }
    }
    throw new ScrapingException(
        "Failed to fetch URL after " + MAX_ATTEMPTS + " attempts: " + url, lastException);
  }

  private void sleepWithBackoff(int retryIndex) {
    try {
      Thread.sleep(baseDelayMs * (1L << retryIndex));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ScrapingException("Interrupted during retry backoff", e);
    }
  }

  private String extractTitle(Document doc, String url) {
    for (String selector : TITLE_SELECTORS) {
      Element el = doc.selectFirst(selector);
      if (el != null && !el.text().isBlank()) {
        return el.text().trim();
      }
    }
    throw new ScrapingException("Could not extract title from: " + url);
  }

  private int extractLatestChapter(Document doc, String url) {
    for (String selector : CHAPTER_SELECTORS) {
      Element el = doc.selectFirst(selector);
      if (el != null) {
        String text = el.text();
        // Try keyword-anchored pattern first to avoid matching unrelated numbers (e.g. dates)
        Matcher km = CHAPTER_KEYWORD_PATTERN.matcher(text);
        if (km.find()) {
          try {
            return Integer.parseInt(km.group(1));
          } catch (NumberFormatException e) {
            // Continue to fallback
          }
        }
        Matcher m = CHAPTER_PATTERN.matcher(text);
        if (m.find()) {
          try {
            return Integer.parseInt(m.group(1));
          } catch (NumberFormatException e) {
            // Continue to next selector
          }
        }
      }
    }
    throw new ScrapingException("Could not extract latest chapter from: " + url);
  }
}
