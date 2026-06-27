package com.mangaTracker.backend.controller;

import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.service.MangaService;
import com.mangaTracker.backend.service.PushMessage;
import com.mangaTracker.backend.service.PushNotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manga")
public class MangaController {

  private final MangaService mangaService;
  private final PushNotificationService pushNotificationService;

  public MangaController(
      MangaService mangaService, PushNotificationService pushNotificationService) {
    this.mangaService = mangaService;
    this.pushNotificationService = pushNotificationService;
  }

  @GetMapping
  public List<Manga> listManga() {
    return mangaService.listManga();
  }

  @PostMapping
  public ResponseEntity<Manga> addManga(@RequestBody AddMangaRequest request) {
    Manga manga = mangaService.addManga(request.sourceUrl());
    return ResponseEntity.status(HttpStatus.CREATED).body(manga);
  }

  @PatchMapping("/{id}")
  public Manga updateManga(@PathVariable UUID id, @RequestBody PatchMangaRequest request) {
    return mangaService.updateManga(id, request.notificationsEnabled());
  }

  @PostMapping("/{id}/read")
  public Manga markRead(@PathVariable UUID id) {
    return mangaService.markRead(id);
  }

  @PostMapping("/{id}/unread")
  public Manga markUnread(@PathVariable UUID id) {
    return mangaService.markUnread(id);
  }

  @GetMapping("/{id}/cover")
  public ResponseEntity<byte[]> getCover(@PathVariable UUID id) {
    Manga manga = mangaService.getById(id);
    String cover = manga.getCoverImageUrl();
    if (cover == null || !cover.startsWith("data:")) {
      return ResponseEntity.notFound().build();
    }
    int comma = cover.indexOf(',');
    int semicolon = cover.indexOf(';');
    if (comma < 0 || semicolon < 0 || semicolon > comma) {
      return ResponseEntity.notFound().build();
    }
    String mediaType = cover.substring("data:".length(), semicolon);
    byte[] bytes;
    try {
      bytes = java.util.Base64.getDecoder().decode(cover.substring(comma + 1));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok()
        .contentType(org.springframework.http.MediaType.parseMediaType(mediaType))
        .body(bytes);
  }

  @PostMapping("/{id}/test-push")
  public ResponseEntity<Void> testPush(@PathVariable UUID id) {
    Manga manga = mangaService.getById(id);
    PushMessage message =
        new PushMessage(
            "Chapter " + manga.getLatestChapter(),
            "Test notification for " + manga.getTitle(),
            manga.getId(),
            manga.getOwnerId(),
            manga.getSourceUrl(),
            manga.getCoverImageUrl());
    pushNotificationService.send(message);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteManga(@PathVariable UUID id) {
    mangaService.deleteManga(id);
    return ResponseEntity.noContent().build();
  }

  record AddMangaRequest(String sourceUrl) {}

  record PatchMangaRequest(Boolean notificationsEnabled) {}
}
