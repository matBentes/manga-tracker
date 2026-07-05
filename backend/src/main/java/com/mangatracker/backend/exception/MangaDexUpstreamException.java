package com.mangatracker.backend.exception;

import java.time.Duration;

public class MangaDexUpstreamException extends RuntimeException {

  private final int statusCode;
  private final Duration retryAfter;

  public MangaDexUpstreamException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = 0;
    this.retryAfter = null;
  }

  public MangaDexUpstreamException(String message, int statusCode, Duration retryAfter) {
    super(message);
    this.statusCode = statusCode;
    this.retryAfter = retryAfter;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public Duration getRetryAfter() {
    return retryAfter;
  }

  public boolean isRateLimited() {
    return statusCode == 429;
  }
}
