package com.mangaTracker.backend.scraper;

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
  private static final int MAX_RETRIES = 3;
  private static final int BASE_DELAY_MS = 1000;
  private static final int TIMEOUT_MS = 15000;
  private static final Pattern CHAPTER_PATTERN = Pattern.compile("(\\d+)(\\.\\d+)?");

  private static final String[] TITLE_SELECTORS = {
    "h1.post-title",
    "div.post-title h1",
    "h1.manga-title",
    ".manga-title",
    "h1"
  };

  private static final String[] CHAPTER_SELECTORS = {
    "ul.wp-manga-chapter a",
    "ul.version-chap li:first-child a",
    ".chapter-list li:first-child a",
    "ul.row-content-chapter li:first-child a"
  };

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
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try {
        return Jsoup.connect(url)
            .timeout(TIMEOUT_MS)
            .userAgent("Mozilla/5.0 (compatible; MangaTracker/1.0)")
            .get();
      } catch (IOException e) {
        lastException = e;
        if (attempt < MAX_RETRIES - 1) {
          sleepWithBackoff(attempt);
        }
      }
    }
    throw new ScrapingException(
        "Failed to fetch URL after " + MAX_RETRIES + " attempts: " + url, lastException);
  }

  private void sleepWithBackoff(int attempt) {
    try {
      Thread.sleep(BASE_DELAY_MS * (long) Math.pow(2, attempt));
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
        Matcher m = CHAPTER_PATTERN.matcher(el.text());
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
