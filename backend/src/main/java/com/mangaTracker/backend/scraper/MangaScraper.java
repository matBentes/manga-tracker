package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.ScrapingException;

public interface MangaScraper {

  boolean supports(String url);

  ScrapedManga scrape(String url) throws ScrapingException;
}
