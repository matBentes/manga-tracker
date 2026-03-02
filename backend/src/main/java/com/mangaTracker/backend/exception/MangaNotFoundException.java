package com.mangaTracker.backend.exception;

public class MangaNotFoundException extends RuntimeException {

  public MangaNotFoundException(String message) {
    super(message);
  }
}
