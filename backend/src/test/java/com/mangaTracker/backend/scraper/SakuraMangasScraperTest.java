package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class SakuraMangasScraperTest {

  private static final String MANGA_URL = "https://sakuramangas.org/manga/one-piece/";

  private static Document parse(String html) {
    return Jsoup.parse(html, MANGA_URL);
  }

  @Test
  void supports_returnsTrue_forSakuraMangasUrl() {
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(""));
    assertThat(scraper.supports("https://sakuramangas.org/manga/test/")).isTrue();
  }

  @Test
  void supports_returnsFalse_forOtherUrl() {
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(""));
    assertThat(scraper.supports("https://mangadex.org/manga/test/")).isFalse();
  }

  @Test
  void supports_returnsFalse_forNull() {
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(""));
    assertThat(scraper.supports(null)).isFalse();
  }

  @Test
  void scrape_extractsTitleFromPostTitleAndChapterFromWpMangaChapter() {
    String html =
        "<html><body>"
            + "<h1 class=\"post-title\">One Piece</h1>"
            + "<ul class=\"wp-manga-chapter\">"
            + "<li><a href=\"#\">Capítulo 1000</a></li>"
            + "<li><a href=\"#\">Capítulo 999</a></li>"
            + "</ul>"
            + "</body></html>";
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(html));
    ScrapedManga result = scraper.scrape(MANGA_URL);
    assertThat(result.title()).isEqualTo("One Piece");
    assertThat(result.latestChapter()).isEqualTo(1000);
  }

  @Test
  void scrape_extractsTitleFromH1Fallback() {
    String html =
        "<html><body>"
            + "<h1>Naruto</h1>"
            + "<ul class=\"wp-manga-chapter\">"
            + "<li><a href=\"#\">Chapter 700</a></li>"
            + "</ul>"
            + "</body></html>";
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(html));
    ScrapedManga result = scraper.scrape(MANGA_URL);
    assertThat(result.title()).isEqualTo("Naruto");
    assertThat(result.latestChapter()).isEqualTo(700);
  }

  @Test
  void scrape_throwsScrapingException_whenTitleNotFound() {
    String html =
        "<html><body>"
            + "<ul class=\"wp-manga-chapter\"><li><a href=\"#\">Chapter 1</a></li></ul>"
            + "</body></html>";
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(html));
    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("title");
  }

  @Test
  void scrape_throwsScrapingException_whenChapterNotFound() {
    String html = "<html><body>" + "<h1 class=\"post-title\">Some Manga</h1>" + "</body></html>";
    SakuraMangasScraper scraper = new SakuraMangasScraper(url -> parse(html));
    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("chapter");
  }

  @Test
  void scrape_throwsScrapingException_onNetworkFailure() {
    // Use 0ms base delay to avoid waiting during retries in tests
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            url -> {
              throw new IOException("Connection refused");
            },
            0L);
    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Failed to fetch");
  }
}
