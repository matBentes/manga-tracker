package com.mangaTracker.backend.exception;

/** Thrown when a user exceeds the allowed rate of an operation; mapped to HTTP 429. */
public class RateLimitExceededException extends RuntimeException {

  public RateLimitExceededException(String message) {
    super(message);
  }
}
