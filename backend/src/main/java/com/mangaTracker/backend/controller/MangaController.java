package com.mangaTracker.backend.controller;

import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.service.MangaService;
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

  public MangaController(MangaService mangaService) {
    this.mangaService = mangaService;
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
    return mangaService.updateManga(id, request.currentChapter(), request.notificationsEnabled());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteManga(@PathVariable UUID id) {
    mangaService.deleteManga(id);
    return ResponseEntity.noContent().build();
  }

  record AddMangaRequest(String sourceUrl) {}

  record PatchMangaRequest(Integer currentChapter, Boolean notificationsEnabled) {}
}
