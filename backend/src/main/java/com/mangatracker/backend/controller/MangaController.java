package com.mangatracker.backend.controller;

import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.security.JwtCookieAuthFilter;
import com.mangatracker.backend.service.MangaService;
import com.mangatracker.backend.service.PushMessage;
import com.mangatracker.backend.service.PushNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Manga", description = "Authenticated manga reading list operations")
@SecurityRequirement(name = JwtCookieAuthFilter.COOKIE_NAME)
public class MangaController {

  private final MangaService mangaService;
  private final PushNotificationService pushNotificationService;

  public MangaController(
      MangaService mangaService, PushNotificationService pushNotificationService) {
    this.mangaService = mangaService;
    this.pushNotificationService = pushNotificationService;
  }

  @GetMapping
  @Operation(summary = "List tracked manga")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Manga list"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public List<Manga> listManga() {
    return mangaService.listManga();
  }

  @PostMapping
  @Operation(summary = "Add a manga by source URL")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Manga added"),
    @ApiResponse(
        responseCode = "400",
        description = "Unsupported or invalid source URL",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "Manga URL already tracked",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "Scraper could not extract manga data",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Add-manga rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Manga> addManga(@RequestBody AddMangaRequest request) {
    Manga manga = mangaService.addManga(request.sourceUrl());
    return ResponseEntity.status(HttpStatus.CREATED).body(manga);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update manga notification settings")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Manga updated"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Manga updateManga(@PathVariable UUID id, @RequestBody PatchMangaRequest request) {
    return mangaService.updateManga(id, request.notificationsEnabled());
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "Mark manga as read")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Manga marked read"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Manga markRead(@PathVariable UUID id) {
    return mangaService.markRead(id);
  }

  @PostMapping("/{id}/unread")
  @Operation(summary = "Mark manga as unread")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Manga marked unread"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public Manga markUnread(@PathVariable UUID id) {
    return mangaService.markUnread(id);
  }

  @GetMapping("/{id}/cover")
  @Operation(summary = "Get manga cover image bytes")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cover image bytes"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga or decodable cover not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
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
  @Operation(summary = "Send a test push notification for a manga")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Test push sent"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
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
  @Operation(summary = "Delete a manga")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Manga deleted"),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Manga not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Void> deleteManga(@PathVariable UUID id) {
    mangaService.deleteManga(id);
    return ResponseEntity.noContent().build();
  }

  record AddMangaRequest(
      @Schema(
              description = "Supported manga source URL to scrape and track",
              example = "https://sakuramangas.org/manga/one-piece/",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String sourceUrl) {}

  record PatchMangaRequest(
      @Schema(
              description = "Whether this manga should trigger new chapter notifications",
              requiredMode = Schema.RequiredMode.REQUIRED)
          Boolean notificationsEnabled) {}
}
