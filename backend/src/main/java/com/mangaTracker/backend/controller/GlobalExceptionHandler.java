package com.mangaTracker.backend.controller;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.exception.ScrapingException;
import com.mangaTracker.backend.exception.UnsupportedSourceException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MangaNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(MangaNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex);
  }

  @ExceptionHandler(DuplicateMangaException.class)
  public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateMangaException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex);
  }

  @ExceptionHandler(UnsupportedSourceException.class)
  public ResponseEntity<Map<String, String>> handleUnsupportedSource(
      UnsupportedSourceException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex);
  }

  @ExceptionHandler(ScrapingException.class)
  public ResponseEntity<Map<String, String>> handleScraping(ScrapingException ex) {
    return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex);
  }

  private ResponseEntity<Map<String, String>> errorResponse(HttpStatus status, Exception ex) {
    return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
  }
}
