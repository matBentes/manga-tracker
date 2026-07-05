package com.mangatracker.backend.controller;

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

import com.mangatracker.backend.exception.DuplicateMangaException;
import com.mangatracker.backend.exception.MangaDexUpstreamException;
import com.mangatracker.backend.exception.MangaNotFoundException;
import com.mangatracker.backend.exception.RateLimitExceededException;
import com.mangatracker.backend.model.Manga;
import com.mangatracker.backend.model.ReadingStatus;
import com.mangatracker.backend.service.MangaDexManga;
import com.mangatracker.backend.service.MangaService;
import com.mangatracker.backend.service.PushMessage;
import com.mangatracker.backend.service.PushNotificationService;
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

  private static final UUID MANGADEX_ID = UUID.randomUUID();

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
        .andExpect(jsonPath("$[0].mangadexId").value(MANGADEX_ID.toString()))
        .andExpect(jsonPath("$[0].currentChapter").value(0))
        .andExpect(jsonPath("$[0].latestChapter").value(100))
        .andExpect(jsonPath("$[0].readingStatus").value("READING"));
  }

  @Test
  void searchManga_returns200WithMangaDexResults() throws Exception {
    MangaDexManga result =
        new MangaDexManga(MANGADEX_ID, "One Piece", "Pirates", "https://img/cover.jpg");
    when(mangaService.searchManga("one")).thenReturn(List.of(result));

    mockMvc
        .perform(get("/api/manga/search").param("q", "one").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].mangaDexId").value(MANGADEX_ID.toString()))
        .andExpect(jsonPath("$[0].title").value("One Piece"))
        .andExpect(jsonPath("$[0].description").value("Pirates"))
        .andExpect(jsonPath("$[0].coverImageUrl").value("https://img/cover.jpg"));
  }

  @Test
  void searchManga_returns502_onMangaDexUpstreamFailure() throws Exception {
    when(mangaService.searchManga("one"))
        .thenThrow(new MangaDexUpstreamException("MangaDex unavailable", new RuntimeException()));

    mockMvc
        .perform(get("/api/manga/search").param("q", "one"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("MangaDex unavailable"));
  }

  @Test
  void searchManga_returns429_onRateLimitExceeded() throws Exception {
    when(mangaService.searchManga("one")).thenThrow(new RateLimitExceededException("Too many"));

    mockMvc
        .perform(get("/api/manga/search").param("q", "one"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("Too many"));
  }

  @Test
  void addManga_returns201_onValidMangaDexId() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    String sourceUrl = "https://sakuramangas.org/obras/test/";
    when(mangaService.addManga(MANGADEX_ID, sourceUrl, 42, ReadingStatus.PLAN_TO_READ))
        .thenReturn(manga);

    mockMvc
        .perform(
            post("/api/manga")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "mangaDexId":"%s",
                      "sourceUrl":"%s",
                      "currentChapter":42,
                      "readingStatus":"PLAN_TO_READ"
                    }
                    """
                        .formatted(MANGADEX_ID, sourceUrl)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Test Manga"));
  }

  @Test
  void addManga_returns409_onDuplicateMangaDexId() throws Exception {
    when(mangaService.addManga(any(), any(), any(), any()))
        .thenThrow(new DuplicateMangaException("Duplicate manga"));

    mockMvc
        .perform(
            post("/api/manga")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mangaDexId\":\"" + MANGADEX_ID + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  void updateManga_returns200_onProgressStatusAndNotificationsUpdate() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setNotificationsEnabled(false);
    manga.setCurrentChapter(30);
    manga.setLatestChapter(120);
    manga.setReadingStatus(ReadingStatus.COMPLETED);
    when(mangaService.updateManga(id, false, 30, 120, ReadingStatus.COMPLETED)).thenReturn(manga);

    mockMvc
        .perform(
            patch("/api/manga/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "notificationsEnabled":false,
                      "currentChapter":30,
                      "latestChapter":120,
                      "readingStatus":"COMPLETED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationsEnabled").value(false))
        .andExpect(jsonPath("$.currentChapter").value(30))
        .andExpect(jsonPath("$.latestChapter").value(120))
        .andExpect(jsonPath("$.readingStatus").value("COMPLETED"));
  }

  @Test
  void updateManga_returns404_onUnknownId() throws Exception {
    UUID id = UUID.randomUUID();
    when(mangaService.updateManga(eq(id), any(), any(), any(), any()))
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
  void testPush_returns200_andSendsNotificationWithNullableSourceUrl() throws Exception {
    UUID id = UUID.randomUUID();
    Manga manga = buildManga(id);
    manga.setOwnerId(UUID.randomUUID());
    manga.setSourceUrl(null);
    when(mangaService.getById(id)).thenReturn(manga);

    mockMvc.perform(post("/api/manga/" + id + "/test-push")).andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(PushMessage.class);
    org.mockito.Mockito.verify(pushNotificationService).send(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().ownerId())
        .isEqualTo(manga.getOwnerId());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().sourceUrl()).isNull();
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
        .sourceUrl("https://sakuramangas.org/obras/test/")
        .mangadexId(MANGADEX_ID)
        .currentChapter(0)
        .latestChapter(100)
        .readingStatus(ReadingStatus.READING)
        .notificationsEnabled(true)
        .build();
  }
}
