package com.mangatracker.backend.scraper;

import com.mangatracker.backend.exception.ScrapingException;

public interface MangaScraper {

  boolean supports(String url);

  ScrapedManga scrape(String url) throws ScrapingException;
}
