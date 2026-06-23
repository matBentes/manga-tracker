package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Scraper for sakuramangas.org.
 *
 * <p>The site is protected by Cloudflare Bot Management and renders its chapter list client-side
 * via its own JavaScript (which performs the site's proprietary security handshake). Rather than
 * reimplementing that handshake, we let a real (stealth-configured) browser load the page — see
 * {@link PlaywrightBrowserManager}. By the time {@code PageFetcher} returns the rendered HTML, the
 * Cloudflare challenge is solved and the chapter list is populated, so we just read the DOM:
 *
 * <ul>
 *   <li>title from the {@code <h1>} / {@code og:title}
 *   <li>latest chapter from the highest number in {@code .chapter-list}
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "scraper.sakura.playwright.enabled", matchIfMissing = true)
public class SakuraMangasScraper implements MangaScraper {

  private static final Logger LOGGER = LoggerFactory.getLogger(SakuraMangasScraper.class);
  private static final String SUPPORTED_HOST = "sakuramangas.org";

  // Presented by the Playwright browser context (see PlaywrightBrowserManager). A current desktop
  // Chrome UA reduces the automation signals Cloudflare Bot Management looks for.
  static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
          + " (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

  // Title meta is "Ler <title> - Sakura Mangás"; strip the localized prefix/suffix.
  private static final String TITLE_PREFIX = "Ler ";
  private static final String TITLE_SUFFIX = " - Sakura Mangás";

  // Chapter anchors read "Cap. 168" / "Cap. 167.2".
  private static final String CHAPTER_LIST_SELECTOR = ".chapter-list a";
  private static final Pattern CHAPTER_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

  // Cover image is exposed as the Open Graph image meta tag.
  private static final String COVER_IMAGE_SELECTOR = "meta[property=og:image]";

  // ── Testable HTTP abstraction ────────────────────────────────────────────────

  @FunctionalInterface
  interface PageFetcher {
    Document fetch(String url) throws IOException;
  }

  private final PageFetcher pageFetcher;

  // ── Constructors ─────────────────────────────────────────────────────────────

  /** Production constructor — fetches the fully rendered page through a stealth browser. */
  @Autowired
  public SakuraMangasScraper(PlaywrightBrowserManager browserManager) {
    this.pageFetcher = browserManager::fetchPage;
  }

  /** Test constructor — allows injecting a mocked/static page fetcher. */
  SakuraMangasScraper(PageFetcher pageFetcher) {
    this.pageFetcher = pageFetcher;
  }

  // ── MangaScraper API ─────────────────────────────────────────────────────────

  @Override
  public boolean supports(String url) {
    return url != null && url.contains(SUPPORTED_HOST);
  }

  @Override
  public ScrapedManga scrape(String url) throws ScrapingException {
    Document doc = fetchPage(url);
    String title = extractTitle(doc, url);
    int latestChapter = extractLatestChapter(doc, url);
    String coverImageUrl = extractCoverImageUrl(doc);
    LOGGER.debug(
        "Scraped {} -> title='{}' latestChapter={} cover={}",
        url,
        title,
        latestChapter,
        coverImageUrl);
    return new ScrapedManga(title, latestChapter, coverImageUrl);
  }

  /** Cover image is optional: return {@code null} rather than failing the whole scrape. */
  private String extractCoverImageUrl(Document doc) {
    Element ogImage = doc.selectFirst(COVER_IMAGE_SELECTOR);
    if (ogImage == null) {
      return null;
    }
    String content = ogImage.attr("content").trim();
    return content.isBlank() ? null : content;
  }

  // ── Extraction ───────────────────────────────────────────────────────────────

  private Document fetchPage(String url) {
    try {
      return pageFetcher.fetch(url);
    } catch (ScrapingException e) {
      throw e;
    } catch (IOException e) {
      throw new ScrapingException("Failed to fetch manga page: " + url, e);
    }
  }

  private String extractTitle(Document doc, String url) {
    Element h1 = doc.selectFirst("h1");
    if (h1 != null && !h1.text().isBlank()) {
      return h1.text().trim();
    }

    Element ogTitle = doc.selectFirst("meta[property=og:title]");
    if (ogTitle != null) {
      String content = ogTitle.attr("content").trim();
      if (content.startsWith(TITLE_PREFIX)) {
        content = content.substring(TITLE_PREFIX.length());
      }
      int suffixAt = content.lastIndexOf(TITLE_SUFFIX);
      if (suffixAt > 0) {
        content = content.substring(0, suffixAt);
      }
      if (!content.isBlank()) {
        return content.trim();
      }
    }

    throw new ScrapingException("Could not extract manga title from page: " + url);
  }

  private int extractLatestChapter(Document doc, String url) {
    Elements chapters = doc.select(CHAPTER_LIST_SELECTOR);
    if (chapters.isEmpty()) {
      throw new ScrapingException("No chapters found in chapter list on page: " + url);
    }

    double latest = -1;
    for (Element chapter : chapters) {
      Matcher m = CHAPTER_NUMBER_PATTERN.matcher(chapter.text());
      if (m.find()) {
        try {
          latest = Math.max(latest, Double.parseDouble(m.group(1)));
        } catch (NumberFormatException e) {
          LOGGER.warn("Failed to parse chapter number from '{}'", chapter.text());
        }
      }
    }

    if (latest < 0) {
      throw new ScrapingException("Could not parse any chapter number on page: " + url);
    }
    return (int) latest;
  }
}
