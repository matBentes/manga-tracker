package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.UnsupportedSourceException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScraperRegistry {

  private final List<MangaScraper> scrapers;

  public ScraperRegistry(List<MangaScraper> scrapers) {
    this.scrapers = scrapers;
  }

  public MangaScraper resolve(String url) {
    return scrapers.stream()
        .filter(s -> s.supports(url))
        .findFirst()
        .orElseThrow(() -> new UnsupportedSourceException("No scraper found for URL: " + url));
  }
}
