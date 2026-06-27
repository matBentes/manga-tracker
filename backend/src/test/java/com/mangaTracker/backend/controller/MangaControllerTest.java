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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mangaTracker.backend.exception.DuplicateMangaException;
import com.mangaTracker.backend.exception.MangaNotFoundException;
import com.mangaTracker.backend.exception.UnsupportedSourceException;
import com.mangaTracker.backend.model.Manga;
import com.mangaTracker.backend.service.MangaService;
import com.mangaTracker.backend.service.PushNotificationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MangaControllerTest {

  private MockMvc mockMvc;

  @Mock private MangaService mangaService;
  @Mock private PushNotificationService pushNotificationService;

  @BeforeEach
  void setUp() {
    MangaController controller = new MangaController(mangaService, pushNotificationService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

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
    when(mangaService.addManga(any()))
        .thenThrow(new UnsupportedSourceException("Unsupported source"));

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
  void updateManga_returns200_onNotificationsToggle() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setNotificationsEnabled(false);
    when(mangaService.updateManga(id, false)).thenReturn(manga);

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationsEnabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationsEnabled").value(false));
  }

  @Test
  void updateManga_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.updateManga(eq(id), any()))
        .thenThrow(new MangaNotFoundException("Manga not found"));

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notificationsEnabled\":true}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void markRead_returns200WithCaughtUpManga() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setCurrentChapter(100);
    when(mangaService.markRead(id)).thenReturn(manga);

    mockMvc
        .perform(post("/api/manga/" + id + "/read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentChapter").value(100));
  }

  @Test
  void markRead_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.markRead(id)).thenThrow(new MangaNotFoundException("Manga not found"));

    mockMvc
        .perform(post("/api/manga/" + id + "/read"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void markUnread_returns200WithResetManga() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    when(mangaService.markUnread(id)).thenReturn(manga);

    mockMvc
        .perform(post("/api/manga/" + id + "/unread"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentChapter").value(0));
  }

  @Test
  void testPush_returns200_andSendsNotification() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    when(mangaService.getById(id)).thenReturn(manga);

    mockMvc.perform(post("/api/manga/" + id + "/test-push")).andExpect(status().isOk());

    org.mockito.Mockito.verify(pushNotificationService).send(any());
  }

  @Test
  void testPush_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.getById(id)).thenThrow(new MangaNotFoundException("Manga not found"));

    mockMvc
        .perform(post("/api/manga/" + id + "/test-push"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void getCover_returnsImageBytes_forDataUrlCover() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    String pngBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
    manga.setCoverImageUrl("data:image/png;base64," + pngBase64);
    when(mangaService.getById(id)).thenReturn(manga);

    mockMvc
        .perform(get("/api/manga/" + id + "/cover"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG));
  }

  @Test
  void getCover_returns404_whenCoverMissing() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setCoverImageUrl(null);
    when(mangaService.getById(id)).thenReturn(manga);

    mockMvc.perform(get("/api/manga/" + id + "/cover")).andExpect(status().isNotFound());
  }

  @Test
  void getCover_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.getById(id)).thenThrow(new MangaNotFoundException("Manga not found"));

    mockMvc.perform(get("/api/manga/" + id + "/cover")).andExpect(status().isNotFound());
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
