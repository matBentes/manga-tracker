package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class SakuraMangasScraperTest {

  private static final String URL = "https://sakuramangas.org/obras/gachiakuta/";

  private SakuraMangasScraper scraperReturning(String html) {
    return new SakuraMangasScraper((SakuraMangasScraper.PageFetcher) url -> Jsoup.parse(html, url));
  }

  // ── supports() ────────────────────────────────────────────────────────────────

  @Test
  void supports_trueForSakuraUrl() {
    assertThat(scraperReturning("<html></html>").supports(URL)).isTrue();
  }

  @Test
  void supports_falseForOtherHost() {
    assertThat(scraperReturning("<html></html>").supports("https://example.com/manga")).isFalse();
  }

  @Test
  void supports_falseForNull() {
    assertThat(scraperReturning("<html></html>").supports(null)).isFalse();
  }

  // ── scrape() happy path ─────────────────────────────────────────────────────────

  @Test
  void scrape_extractsTitleFromH1AndHighestChapter() {
    String html =
        "<html><body><h1>Gachiakuta</h1>"
            + "<div class=\"chapter-list\">"
            + "<a href=\"/o/168\">Cap. 168</a>"
            + "<a href=\"/o/167.2\">Cap. 167.2</a>"
            + "<a href=\"/o/167\">Cap. 167</a>"
            + "</div></body></html>";

    ScrapedManga result = scraperReturning(html).scrape(URL);

    assertThat(result.title()).isEqualTo("Gachiakuta");
    assertThat(result.latestChapter()).isEqualTo(168);
  }

  @Test
  void scrape_takesMaxChapterRegardlessOfDomOrder() {
    String html =
        "<html><body><h1>One Piece</h1>"
            + "<div class=\"chapter-list\">"
            + "<a href=\"/o/1\">Cap. 1</a>"
            + "<a href=\"/o/1100\">Cap. 1100</a>"
            + "<a href=\"/o/1099\">Cap. 1099</a>"
            + "</div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).latestChapter()).isEqualTo(1100);
  }

  @Test
  void scrape_fallsBackToOgTitleWhenNoH1() {
    String html =
        "<html><head><meta property=\"og:title\" content=\"Ler Naruto - Sakura Mangás\"></head>"
            + "<body><div class=\"chapter-list\"><a href=\"/o/700\">Cap. 700</a></div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).title()).isEqualTo("Naruto");
  }

  @Test
  void scrape_extractsCoverImageFromOgImage() {
    String html =
        "<html><head><meta property=\"og:image\" content=\"https://img/cover.jpg\"></head>"
            + "<body><h1>Gachiakuta</h1>"
            + "<div class=\"chapter-list\"><a href=\"/o/10\">Cap. 10</a></div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).coverImageUrl())
        .isEqualTo("https://img/cover.jpg");
  }

  @Test
  void scrape_returnsNullCover_whenNoImageMeta() {
    String html =
        "<html><body><h1>Gachiakuta</h1>"
            + "<div class=\"chapter-list\"><a href=\"/o/10\">Cap. 10</a></div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).coverImageUrl()).isNull();
  }

  @Test
  void scrape_prefersPageCoverOverOgImageBanner() {
    String html =
        "<html><head><meta property=\"og:image\" content=\"https://site/og_banner.png\"></head>"
            + "<body><h1>Gachiakuta</h1>"
            + "<img class=\"img-fluid capa\" src=\"https://img/gachiakuta/thumb_256.jpg\">"
            + "<div class=\"chapter-list\"><a href=\"/o/10\">Cap. 10</a></div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).coverImageUrl())
        .isEqualTo("https://img/gachiakuta/thumb_256.jpg");
  }

  @Test
  void scrape_fallsBackToTwitterImage_whenNoOgImage() {
    String html =
        "<html><head><meta name=\"twitter:image\" content=\"https://img/tw.jpg\"></head>"
            + "<body><h1>Gachiakuta</h1>"
            + "<div class=\"chapter-list\"><a href=\"/o/10\">Cap. 10</a></div></body></html>";

    assertThat(scraperReturning(html).scrape(URL).coverImageUrl()).isEqualTo("https://img/tw.jpg");
  }

  // ── scrape() error paths ─────────────────────────────────────────────────────────

  @Test
  void scrape_throwsWhenNoTitle() {
    String html =
        "<html><body><div class=\"chapter-list\"><a href=\"/o/5\">Cap. 5</a></div></body></html>";
    SakuraMangasScraper scraper = scraperReturning(html);

    assertThatThrownBy(() -> scraper.scrape(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Could not extract manga title");
  }

  @Test
  void scrape_throwsWhenNoChapters() {
    String html = "<html><body><h1>Bleach</h1><div class=\"chapter-list\"></div></body></html>";
    SakuraMangasScraper scraper = scraperReturning(html);

    assertThatThrownBy(() -> scraper.scrape(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("No chapters found");
  }

  @Test
  void scrape_throwsWhenChapterNumbersUnparseable() {
    String html =
        "<html><body><h1>Berserk</h1>"
            + "<div class=\"chapter-list\"><a href=\"/o/x\">Cap. final</a></div></body></html>";
    SakuraMangasScraper scraper = scraperReturning(html);

    assertThatThrownBy(() -> scraper.scrape(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Could not parse any chapter number");
  }

  @Test
  void scrape_wrapsIoExceptionFromFetcher() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            (SakuraMangasScraper.PageFetcher)
                url -> {
                  throw new IOException("network down");
                });

    assertThatThrownBy(() -> scraper.scrape(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Failed to fetch manga page")
        .hasRootCauseInstanceOf(IOException.class);
  }

  @Test
  void scrape_propagatesScrapingExceptionFromFetcher() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            (SakuraMangasScraper.PageFetcher)
                url -> {
                  throw new ScrapingException("cloudflare blocked");
                });

    assertThatThrownBy(() -> scraper.scrape(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessage("cloudflare blocked");
  }
}
