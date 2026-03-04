package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import java.util.Base64;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class SakuraMangasScraperTest {

  private static final String MANGA_URL = "https://sakuramangas.org/obras/chainsaw-man/";

  // A valid base64 challenge: "address/middle/path"
  private static final String CHALLENGE =
      Base64.getEncoder().encodeToString("address/middle/path".getBytes());

  private static Document parsePage(String mangaId, String challenge, String csrf) {
    return Jsoup.parse(
        "<html><head>"
            + "<meta manga-id=\""
            + mangaId
            + "\" />"
            + "<meta name=\"header-challenge\" content=\""
            + challenge
            + "\" />"
            + "<meta name=\"csrf-token\" content=\""
            + csrf
            + "\" />"
            + "</head></html>",
        MANGA_URL);
  }

  private static SakuraMangasScraper scraperWith(
      String mangaId,
      String titleJson,
      String chaptersHtml) {
    return new SakuraMangasScraper(
        url -> parsePage(mangaId, CHALLENGE, "csrf-token"),
        url -> "manga_info: 99999 \"X-Verification-Key-1\" = 'key1val' \"X-Verification-Key-2\" = 'key2val'",
        (url, data, headers) -> url.contains("manga_info") ? titleJson : chaptersHtml);
  }

  // ── supports() ────────────────────────────────────────────────────────────

  @Test
  void supports_returnsTrue_forMangaPath() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    assertThat(scraper.supports("https://sakuramangas.org/manga/one-piece/")).isTrue();
  }

  @Test
  void supports_returnsTrue_forObrasPath() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    assertThat(scraper.supports("https://sakuramangas.org/obras/chainsaw-man/")).isTrue();
  }

  @Test
  void supports_returnsFalse_forOtherHost() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    assertThat(scraper.supports("https://mangadex.org/manga/test/")).isFalse();
  }

  @Test
  void supports_returnsFalse_forNull() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    assertThat(scraper.supports(null)).isFalse();
  }

  // ── scrape() ──────────────────────────────────────────────────────────────

  @Test
  void scrape_extractsTitleAndLatestChapterFromApiResponses() {
    String chaptersHtml =
        "<div class=\"capitulo-item\">"
            + "<span class=\"num-capitulo\" data-chapter=\"197\">Capítulo 197</span>"
            + "</div>";
    SakuraMangasScraper scraper =
        scraperWith("123", "{\"titulo\": \"Chainsaw Man\"}", chaptersHtml);

    ScrapedManga result = scraper.scrape(MANGA_URL);

    assertThat(result.title()).isEqualTo("Chainsaw Man");
    assertThat(result.latestChapter()).isEqualTo(197);
  }

  @Test
  void scrape_fallsBackToChapterTextWhenDataChapterAttrMissing() {
    String chaptersHtml =
        "<div class=\"capitulo-item\">"
            + "<span class=\"num-capitulo\">Capítulo 50</span>"
            + "</div>";
    SakuraMangasScraper scraper = scraperWith("123", "{\"titulo\": \"Test\"}", chaptersHtml);

    ScrapedManga result = scraper.scrape(MANGA_URL);
    assertThat(result.latestChapter()).isEqualTo(50);
  }

  @Test
  void scrape_throwsScrapingException_whenMangaIdMetaMissing() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            url ->
                Jsoup.parse(
                    "<html><head>"
                        + "<meta name=\"header-challenge\" content=\"ch\" />"
                        + "<meta name=\"csrf-token\" content=\"tok\" />"
                        + "</head></html>",
                    MANGA_URL),
            url -> "",
            (u, d, h) -> "");

    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("security tokens");
  }

  @Test
  void scrape_throwsScrapingException_whenTitleIsBlankInApiResponse() {
    String chaptersHtml =
        "<div class=\"capitulo-item\">"
            + "<span class=\"num-capitulo\" data-chapter=\"1\">Capítulo 1</span>"
            + "</div>";
    SakuraMangasScraper scraper = scraperWith("123", "{\"titulo\": \"\"}", chaptersHtml);

    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Blank title");
  }

  @Test
  void scrape_throwsScrapingException_whenChapterListIsEmpty() {
    SakuraMangasScraper scraper =
        scraperWith("123", "{\"titulo\": \"Test\"}", "<div>no chapters here</div>");

    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("chapter list");
  }

  @Test
  void scrape_throwsScrapingException_onPageFetchFailure() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            url -> {
              throw new IOException("Connection refused");
            },
            url -> "",
            (u, d, h) -> "");

    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Failed to fetch manga page");
  }

  @Test
  void scrape_throwsScrapingException_onApiCallFailure() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(
            url -> parsePage("123", CHALLENGE, "tok"),
            url -> "manga_info: 99999",
            (url, data, headers) -> {
              throw new IOException("API unavailable");
            });

    assertThatThrownBy(() -> scraper.scrape(MANGA_URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("manga info API");
  }

  // ── generateProof() ───────────────────────────────────────────────────────

  @Test
  void generateProof_producesA64CharHexString() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    String proof = scraper.generateProof(CHALLENGE, 12345L);

    assertThat(proof).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void generateProof_isDeterministic() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    String proof1 = scraper.generateProof(CHALLENGE, 12345L);
    String proof2 = scraper.generateProof(CHALLENGE, 12345L);

    assertThat(proof1).isEqualTo(proof2);
  }

  @Test
  void generateProof_differsByKey() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    String proof1 = scraper.generateProof(CHALLENGE, 0L);
    String proof2 = scraper.generateProof(CHALLENGE, 99999L);

    assertThat(proof1).isNotEqualTo(proof2);
  }

  @Test
  void generateProof_throwsScrapingException_forInvalidChallenge() {
    SakuraMangasScraper scraper =
        new SakuraMangasScraper(url -> null, url -> "", (u, d, h) -> "");
    String badChallenge = Base64.getEncoder().encodeToString("onlytwoparts/here".getBytes());

    assertThatThrownBy(() -> scraper.generateProof(badChallenge, 0L))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("3 slash-separated parts");
  }
}
