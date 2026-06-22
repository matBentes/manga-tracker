package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Manual end-to-end live verification — NOT run in CI. Drives the full redesigned production path
 * (stealth Playwright fetch → rendered-DOM scrape) against the real sakuramangas.org. Run:
 *
 * <pre>SAKURA_LIVE=true ./gradlew test --tests SakuraE2ELiveTest</pre>
 */
@Tag("live")
class SakuraE2ELiveTest {

  @Test
  void scrapesRealMangaThroughProductionPath() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        "true".equals(System.getenv("SAKURA_LIVE")), "live test disabled (set SAKURA_LIVE=true)");

    PlaywrightBrowserManager manager = new PlaywrightBrowserManager("");
    try {
      SakuraMangasScraper scraper = new SakuraMangasScraper(manager);

      for (String url :
          new String[] {
            "https://sakuramangas.org/obras/chainsaw-man/",
            "https://sakuramangas.org/obras/gachiakuta/"
          }) {
        ScrapedManga r = scraper.scrape(url);
        System.out.println("LIVE " + url + " -> title='" + r.title() + "' chapter=" + r.latestChapter());
        assertThat(r.title()).isNotBlank();
        assertThat(r.latestChapter()).isPositive();
      }
    } finally {
      manager.shutdown();
    }
  }
}
