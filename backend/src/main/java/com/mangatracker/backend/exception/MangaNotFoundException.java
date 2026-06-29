package com.mangatracker.backend.exception;

public class MangaNotFoundException extends RuntimeException {

  public MangaNotFoundException(String message) {
    super(message);
  }
}
