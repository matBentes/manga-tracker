package com.mangaTracker.backend.scraper;

public class UnsupportedSourceException extends RuntimeException {

  public UnsupportedSourceException(String url) {
    super("No scraper found for URL: " + url);
  }
}
