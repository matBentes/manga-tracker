package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.exception.UnsupportedSourceException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScraperRegistryTest {

  @Test
  void resolve_returnsMatchingScraper() {
    MangaScraper scraper = mock(MangaScraper.class);
    when(scraper.supports("https://sakuramangas.org/manga/test/")).thenReturn(true);
    ScraperRegistry registry = new ScraperRegistry(List.of(scraper));

    MangaScraper result = registry.resolve("https://sakuramangas.org/manga/test/");

    assertThat(result).isSameAs(scraper);
  }

  @Test
  void resolve_throwsUnsupportedSourceException_whenNoScraperFound() {
    MangaScraper scraper = mock(MangaScraper.class);
    when(scraper.supports("https://unknown.com/manga/test/")).thenReturn(false);
    ScraperRegistry registry = new ScraperRegistry(List.of(scraper));

    assertThatThrownBy(() -> registry.resolve("https://unknown.com/manga/test/"))
        .isInstanceOf(UnsupportedSourceException.class)
        .hasMessageContaining("No scraper found");
  }

  @Test
  void resolve_throwsUnsupportedSourceException_whenScraperListIsEmpty() {
    ScraperRegistry registry = new ScraperRegistry(List.of());

    assertThatThrownBy(() -> registry.resolve("https://sakuramangas.org/manga/test/"))
        .isInstanceOf(UnsupportedSourceException.class);
  }
}
