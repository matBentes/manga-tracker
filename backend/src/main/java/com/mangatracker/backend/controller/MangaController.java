package com.mangatracker.backend.controller;

import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.model.ReadingStatus;
import com.mangatracker.backend.security.JwtCookieAuthFilter;
import com.mangatracker.backend.service.MangaDexManga;
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
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/search")
  @Operation(summary = "Search MangaDex")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "MangaDex search results"),
    @ApiResponse(
        responseCode = "400",
        description = "Missing or invalid query",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Search rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "502",
        description = "MangaDex request failed",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public List<MangaDexManga> searchManga(@RequestParam("q") String query) {
    return mangaService.searchManga(query);
  }

  @PostMapping
  @Operation(summary = "Add a manga from MangaDex")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Manga added"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid add request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid auth cookie",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "MangaDex title already tracked",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Add-manga rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "502",
        description = "MangaDex request failed",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  public ResponseEntity<Manga> addManga(@RequestBody AddMangaRequest request) {
    Manga manga =
        mangaService.addManga(
            request.mangaDexId(),
            request.sourceUrl(),
            request.currentChapter(),
            request.readingStatus());
    return ResponseEntity.status(HttpStatus.CREATED).body(manga);
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update manga progress, status, or notification settings")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Manga updated"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid update request",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
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
    return mangaService.updateManga(
        id,
        request.notificationsEnabled(),
        request.currentChapter(),
        request.latestChapter(),
        request.readingStatus());
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
              description = "MangaDex manga identifier to track",
              example = "32f3b331-0c6a-4bc0-94b6-866e142e6c3a",
              requiredMode = Schema.RequiredMode.REQUIRED)
          UUID mangaDexId,
      @Schema(
              description = "Optional read-here URL",
              example = "https://sakuramangas.org/obras/one-piece/",
              nullable = true)
          String sourceUrl,
      @Schema(description = "Optional starting chapter", example = "42", nullable = true)
          Integer currentChapter,
      @Schema(description = "Optional starting reading status", nullable = true)
          ReadingStatus readingStatus) {}

  record PatchMangaRequest(
      @Schema(
              description = "Whether this manga should trigger new chapter notifications",
              nullable = true)
          Boolean notificationsEnabled,
      @Schema(description = "Chapter the user has read up to", example = "43", nullable = true)
          Integer currentChapter,
      @Schema(description = "Latest known English chapter", example = "100", nullable = true)
          Integer latestChapter,
      @Schema(description = "User reading status", nullable = true) ReadingStatus readingStatus) {}
}
