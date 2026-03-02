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
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(DuplicateMangaException.class)
  public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateMangaException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(UnsupportedSourceException.class)
  public ResponseEntity<Map<String, String>> handleUnsupportedSource(
      UnsupportedSourceException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(ScrapingException.class)
  public ResponseEntity<Map<String, String>> handleScraping(ScrapingException ex) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(Map.of("error", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
  }
}
