package com.mangatracker.backend.exception;

public class ScrapingException extends RuntimeException {

  public ScrapingException(String message) {
    super(message);
  }

  public ScrapingException(String message, Throwable cause) {
    super(message, cause);
  }
}
