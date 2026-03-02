package com.mangaTracker.backend.scraper;

public interface MangaScraper {

  boolean supports(String url);

  ScrapedManga scrape(String url) throws ScrapingException;
}
