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

  // Cover image: prefer the page's real cover element, then fall back to social meta tags. On
  // sakuramangas the per-manga cover is `img.capa`; og:image there is only a generic site banner,
  // so the page image must come first. The first selector yielding a non-blank URL wins; the second
  // entry is the attribute holding the URL.
  private static final java.util.List<String[]> COVER_SELECTORS =
      java.util.List.of(
          new String[] {"img.capa", "src"},
          new String[] {".manga-cover img, .obra-capa img, .cover img, img.cover", "src"},
          new String[] {"meta[property=og:image]", "content"},
          new String[] {"meta[name=og:image]", "content"},
          new String[] {"meta[name=twitter:image]", "content"},
          new String[] {"meta[property=twitter:image]", "content"},
          new String[] {"link[rel=image_src]", "href"});

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

  /**
   * Accepts a URL only when it is an http/https URL whose host is exactly the allowlisted host or a
   * subdomain of it. Parsing (rather than substring matching) defends against SSRF: it rejects
   * non-http schemes ({@code file://}, etc.), internal IPs, and look-alike hosts that merely
   * contain the allowlisted string in the path, query, userinfo, or a different registrable domain.
   */
  @Override
  public boolean supports(String url) {
    if (url == null) {
      return false;
    }
    final java.net.URI uri;
    try {
      uri = new java.net.URI(url);
    } catch (java.net.URISyntaxException e) {
      return false;
    }
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || host == null) {
      return false;
    }
    scheme = scheme.toLowerCase(java.util.Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      return false;
    }
    host = host.toLowerCase(java.util.Locale.ROOT);
    return host.equals(SUPPORTED_HOST) || host.endsWith("." + SUPPORTED_HOST);
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
    for (String[] selector : COVER_SELECTORS) {
      Element element = doc.selectFirst(selector[0]);
      if (element != null) {
        // absUrl resolves relative paths (e.g. "thumb_256.jpg") against the page base URI;
        // it returns "" when there is no base, so fall back to the raw attribute.
        String url = element.absUrl(selector[1]);
        if (url.isBlank()) {
          url = element.attr(selector[1]).trim();
        }
        if (!url.isBlank()) {
          return url;
        }
      }
    }
    return null;
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
