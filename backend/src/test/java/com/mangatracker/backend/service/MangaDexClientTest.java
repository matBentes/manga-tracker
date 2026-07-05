package com.mangatracker.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mangatracker.backend.exception.MangaDexUpstreamException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MangaDexClientTest {

  private static final UUID MANGADEX_ID = UUID.fromString("32f3b331-0c6a-4bc0-94b6-866e142e6c3a");

  private MockRestServiceServer server;
  private MangaDexClient client;
  private List<Duration> sleeps;

  @BeforeEach
  void setUp() {
    sleeps = new ArrayList<>();
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.baseUrl("https://api.mangadex.org").build();
    client = new MangaDexClient(restClient, sleeps::add);
  }

  @AfterEach
  void verifyServer() {
    server.verify();
  }

  @Test
  void search_parsesTitleDescriptionAndCoverUrl_preferringEnglish() {
    server
        .expect(request -> assertRequestPath(request, "/manga"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {
                      "id": "32f3b331-0c6a-4bc0-94b6-866e142e6c3a",
                      "attributes": {
                        "title": {"ja": "Wan Pisu", "en": "One Piece"},
                        "description": {"en": "Pirates"}
                      },
                      "relationships": [
                        {
                          "type": "cover_art",
                          "attributes": {"fileName": "one-piece.jpg"}
                        }
                      ]
                    },
                    {
                      "id": "795d6878-9ab8-4bfe-9d2e-6d8e0f2d1f87",
                      "attributes": {
                        "title": {"ja": "Kodoku no Gourmet"},
                        "description": {}
                      },
                      "relationships": []
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    List<MangaDexManga> results = client.search("one piece");

    assertThat(results).hasSize(2);
    assertThat(results.get(0).mangaDexId()).isEqualTo(MANGADEX_ID);
    assertThat(results.get(0).title()).isEqualTo("One Piece");
    assertThat(results.get(0).description()).isEqualTo("Pirates");
    assertThat(results.get(0).coverImageUrl())
        .isEqualTo("https://uploads.mangadex.org/covers/" + MANGADEX_ID + "/one-piece.jpg.512.jpg");
    assertThat(results.get(1).title()).isEqualTo("Kodoku no Gourmet");
    assertThat(results.get(1).coverImageUrl()).isNull();
  }

  @Test
  void getManga_parsesSingleMangaMetadata() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID))
        .andRespond(withSuccess(singleMangaResponse(), MediaType.APPLICATION_JSON));

    MangaDexManga result = client.getManga(MANGADEX_ID);

    assertThat(result.mangaDexId()).isEqualTo(MANGADEX_ID);
    assertThat(result.title()).isEqualTo("One Piece");
    assertThat(result.description()).isEqualTo("Pirates");
  }

  @Test
  void latestEnglishChapter_parsesLeadingPositiveInteger() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {"attributes": {"chapter": "10.5"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    OptionalInt chapter = client.latestEnglishChapter(MANGADEX_ID);

    assertThat(chapter).hasValue(10);
  }

  @Test
  void latestEnglishChapter_returnsFirstNumericChapter_whenNewestEntryIsNonInteger() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {"attributes": {"chapter": "Extra"}},
                    {"attributes": {"chapter": "123"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    OptionalInt chapter = client.latestEnglishChapter(MANGADEX_ID);

    assertThat(chapter).hasValue(123);
  }

  @Test
  void latestEnglishChapter_returnsEmpty_whenNoFeedEntryHasIntegerChapter() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {"attributes": {"chapter": null}},
                    {"attributes": {"chapter": "Extra"}},
                    {"attributes": {"chapter": "0"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    OptionalInt chapter = client.latestEnglishChapter(MANGADEX_ID);

    assertThat(chapter).isEmpty();
  }

  @Test
  void latestEnglishChapter_retriesOnce_onRateLimit() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "0")
                .body("{}"));
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {"attributes": {"chapter": "42"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    OptionalInt chapter = client.latestEnglishChapter(MANGADEX_ID);

    assertThat(chapter).hasValue(42);
  }

  @Test
  void latestEnglishChapter_capsRetryAfterSleepAtFiveSeconds_onRateLimit() {
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60")
                .body("{}"));
    server
        .expect(request -> assertRequestPath(request, "/manga/" + MANGADEX_ID + "/feed"))
        .andRespond(
            withSuccess(
                """
                {
                  "data": [
                    {"attributes": {"chapter": "42"}}
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    OptionalInt chapter = client.latestEnglishChapter(MANGADEX_ID);

    assertThat(chapter).hasValue(42);
    assertThat(sleeps).first().isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void search_throwsMangaDexUpstreamException_onServerError() {
    server
        .expect(request -> assertRequestPath(request, "/manga"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{}"));

    assertThatThrownBy(() -> client.search("one"))
        .isInstanceOf(MangaDexUpstreamException.class)
        .hasMessageContaining("status 500");
  }

  private static String singleMangaResponse() {
    return """
        {
          "data": {
            "id": "32f3b331-0c6a-4bc0-94b6-866e142e6c3a",
            "attributes": {
              "title": {"en": "One Piece"},
              "description": {"en": "Pirates"}
            },
            "relationships": [
              {
                "type": "cover_art",
                "attributes": {"fileName": "one-piece.jpg"}
              }
            ]
          }
        }
        """;
  }

  private static void assertRequestPath(ClientHttpRequest request, String path) {
    assertThat(request.getURI().getScheme()).isEqualTo("https");
    assertThat(request.getURI().getHost()).isEqualTo("api.mangadex.org");
    assertThat(request.getURI().getPath()).isEqualTo(path);
  }
}
