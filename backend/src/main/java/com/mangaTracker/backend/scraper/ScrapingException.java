package com.mangaTracker.backend.scraper;

public class ScrapingException extends RuntimeException {

  public ScrapingException(String message) {
    super(message);
  }

  public ScrapingException(String message, Throwable cause) {
    super(message, cause);
  }
}
