package com.mangaTracker.backend.service;

/** Raised when a Web Push payload cannot be encrypted or delivered to the push service. */
public class WebPushException extends RuntimeException {

  public WebPushException(String message, Throwable cause) {
    super(message, cause);
  }
}
