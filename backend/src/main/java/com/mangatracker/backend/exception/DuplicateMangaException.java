package com.mangatracker.backend.exception;

public class DuplicateMangaException extends RuntimeException {

  public DuplicateMangaException(String message) {
    super(message);
  }
}
