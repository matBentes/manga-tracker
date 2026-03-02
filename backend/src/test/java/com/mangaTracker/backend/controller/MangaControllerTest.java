package com.mangaTracker.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.exception.UnsupportedSourceException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.service.MangaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MangaController.class)
class MangaControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private MangaService mangaService;

  @Test
  void listManga_returns200WithMangaList() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    when(mangaService.listManga()).thenReturn(List.of(manga));

    mockMvc
        .perform(get("/api/manga").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Test Manga"))
        .andExpect(jsonPath("$[0].currentChapter").value(0))
        .andExpect(jsonPath("$[0].latestChapter").value(100));
  }

  @Test
  void addManga_returns201_onValidUrl() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    when(mangaService.addManga("https://sakuramangas.org/manga/test/")).thenReturn(manga);

    mockMvc
        .perform(
            post("/api/manga")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceUrl\":\"https://sakuramangas.org/manga/test/\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Test Manga"));
  }

  @Test
  void addManga_returns400_onUnsupportedUrl() throws Exception {
    when(mangaService.addManga(any())).thenThrow(new UnsupportedSourceException("Unsupported source"));

    mockMvc
        .perform(
            post("/api/manga")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceUrl\":\"https://unknown.com/manga/test/\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void addManga_returns409_onDuplicateUrl() throws Exception {
    when(mangaService.addManga(any())).thenThrow(new DuplicateMangaException("Duplicate manga"));

    mockMvc
        .perform(
            post("/api/manga")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceUrl\":\"https://sakuramangas.org/manga/test/\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void updateManga_returns200_onValidUpdate() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setCurrentChapter(50);
    when(mangaService.updateManga(id, 50, null)).thenReturn(manga);

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentChapter\":50}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentChapter").value(50));
  }

  @Test
  void updateManga_returns400_onInvalidCurrentChapter() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.updateManga(eq(id), any(), any()))
        .thenThrow(new IllegalArgumentException("currentChapter cannot exceed latestChapter"));

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentChapter\":999}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void updateManga_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.updateManga(eq(id), any(), any()))
        .thenThrow(new MangaNotFoundException("Manga not found"));

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentChapter\":50}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void deleteManga_returns204_onSuccess() throws Exception {
    UUID id = UUID.randomUUID();
    doNothing().when(mangaService).deleteManga(id);

    mockMvc.perform(delete("/api/manga/" + id)).andExpect(status().isNoContent());
  }

  @Test
  void deleteManga_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(new MangaNotFoundException("Manga not found")).when(mangaService).deleteManga(id);

    mockMvc
        .perform(delete("/api/manga/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  private static Manga buildManga(UUID id) {
    return Manga.builder()
        .id(id)
        .title("Test Manga")
        .sourceUrl("https://sakuramangas.org/manga/test/")
        .currentChapter(0)
        .latestChapter(100)
        .notificationsEnabled(true)
        .build();
  }
}
