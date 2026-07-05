package com.mangatracker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mangatracker.backend.exception.MangaDexUpstreamException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class MangaDexClient {

  private static final Logger LOG = LoggerFactory.getLogger(MangaDexClient.class);

  private static final String API_BASE_URL = "https://api.mangadex.org";
  private static final String COVER_BASE_URL = "https://uploads.mangadex.org/covers/";
  private static final String ATTRIBUTES_FIELD = "attributes";
  private static final String COVER_ART_TYPE = "cover_art";
  private static final int SEARCH_LIMIT = 10;
  private static final int CHAPTER_FEED_LIMIT = 10;
  private static final int MAX_ATTEMPTS = 2;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DEFAULT_RATE_LIMIT_RETRY = Duration.ofSeconds(1);
  private static final Duration MAX_RATE_LIMIT_RETRY = Duration.ofSeconds(5);
  private static final Duration REQUEST_SPACING = Duration.ofMillis(200);
  private static final Pattern LEADING_POSITIVE_INTEGER = Pattern.compile("^([1-9]\\d*)");

  private final RestClient restClient;
  private final Sleeper sleeper;
  private final Object requestThrottle = new Object();
  private long nextRequestAtNanos;

  /** Marks the production constructor for Spring; the package-private one is test-only. */
  @Autowired
  public MangaDexClient(RestClient.Builder restClientBuilder) {
    this(buildRestClient(restClientBuilder), Thread::sleep);
  }

  MangaDexClient(RestClient restClient, Sleeper sleeper) {
    this.restClient = restClient;
    this.sleeper = sleeper;
  }

  public List<MangaDexManga> search(String query) {
    String normalizedQuery = requireText(query, "Search query is required");
    JsonNode body =
        getWithRetry(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/manga")
                                .queryParam("title", normalizedQuery)
                                .queryParam("includes[]", COVER_ART_TYPE)
                                .queryParam("limit", SEARCH_LIMIT)
                                .queryParam("offset", 0)
                                .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwUpstreamException)
                    .body(JsonNode.class));
    return parseMangaList(requireBody(body, "MangaDex search response was empty"));
  }

  public MangaDexManga getManga(UUID mangaDexId) {
    if (mangaDexId == null) {
      throw new IllegalArgumentException("mangaDexId is required");
    }
    JsonNode body =
        getWithRetry(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/manga/{id}")
                                .queryParam("includes[]", COVER_ART_TYPE)
                                .build(mangaDexId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwUpstreamException)
                    .body(JsonNode.class));
    MangaDexManga manga =
        parseManga(requireBody(body, "MangaDex manga response was empty").path("data"));
    if (manga == null) {
      throw new MangaDexUpstreamException("MangaDex response did not include manga metadata", null);
    }
    return manga;
  }

  public OptionalInt latestEnglishChapter(UUID mangaDexId) {
    if (mangaDexId == null) {
      throw new IllegalArgumentException("mangaDexId is required");
    }
    JsonNode body =
        getWithRetry(
            () ->
                restClient
                    .get()
                    .uri(
                        uriBuilder ->
                            uriBuilder
                                .path("/manga/{id}/feed")
                                .queryParam("translatedLanguage[]", "en")
                                .queryParam("order[chapter]", "desc")
                                .queryParam("limit", CHAPTER_FEED_LIMIT)
                                .build(mangaDexId))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, this::throwUpstreamException)
                    .body(JsonNode.class));
    JsonNode data = requireBody(body, "MangaDex chapter feed response was empty").path("data");
    if (!data.isArray() || data.isEmpty()) {
      return OptionalInt.empty();
    }
    for (JsonNode entry : data) {
      String chapter = textOrNull(entry.path(ATTRIBUTES_FIELD).path("chapter"));
      OptionalInt parsed = parseLeadingPositiveInteger(chapter, mangaDexId);
      if (parsed.isPresent()) {
        return parsed;
      }
    }
    return OptionalInt.empty();
  }

  private List<MangaDexManga> parseMangaList(JsonNode body) {
    JsonNode data = body.path("data");
    if (!data.isArray()) {
      throw new MangaDexUpstreamException("MangaDex search response did not include data", null);
    }
    List<MangaDexManga> results = new ArrayList<>();
    data.forEach(
        node -> {
          MangaDexManga manga = parseManga(node);
          if (manga != null) {
            results.add(manga);
          }
        });
    return results;
  }

  private MangaDexManga parseManga(JsonNode data) {
    String id = textOrNull(data.path("id"));
    if (id == null) {
      return null;
    }
    UUID mangaDexId;
    try {
      mangaDexId = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      LOG.warn("Skipping MangaDex result with invalid id: {}", id);
      return null;
    }

    JsonNode attributes = data.path(ATTRIBUTES_FIELD);
    String title = localizedText(attributes.path("title"));
    if (title == null) {
      LOG.warn("Skipping MangaDex result without a title: {}", mangaDexId);
      return null;
    }
    String description = localizedText(attributes.path("description"));
    String coverFileName = coverFileName(data.path("relationships"));
    String coverImageUrl =
        coverFileName == null
            ? null
            : COVER_BASE_URL + mangaDexId + "/" + coverFileName + ".512.jpg";
    return new MangaDexManga(mangaDexId, title, description, coverImageUrl);
  }

  private static String localizedText(JsonNode localized) {
    String english = textOrNull(localized.path("en"));
    if (english != null) {
      return english;
    }
    Iterator<String> fieldNames = localized.fieldNames();
    while (fieldNames.hasNext()) {
      String value = textOrNull(localized.path(fieldNames.next()));
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String coverFileName(JsonNode relationships) {
    if (!relationships.isArray()) {
      return null;
    }
    for (JsonNode relationship : relationships) {
      if (COVER_ART_TYPE.equals(textOrNull(relationship.path("type")))) {
        return textOrNull(relationship.path(ATTRIBUTES_FIELD).path("fileName"));
      }
    }
    return null;
  }

  private OptionalInt parseLeadingPositiveInteger(String chapter, UUID mangaDexId) {
    if (chapter == null) {
      LOG.info("Skipping MangaDex chapter for {} because chapter value is missing", mangaDexId);
      return OptionalInt.empty();
    }
    Matcher matcher = LEADING_POSITIVE_INTEGER.matcher(chapter.trim());
    if (!matcher.find()) {
      LOG.info("Skipping non-integer MangaDex chapter for {}: {}", mangaDexId, chapter);
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(matcher.group(1)));
    } catch (NumberFormatException e) {
      LOG.info("Skipping out-of-range MangaDex chapter for {}: {}", mangaDexId, chapter);
      return OptionalInt.empty();
    }
  }

  private <T> T getWithRetry(Supplier<T> request) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        waitForRequestSlot();
        return request.get();
      } catch (MangaDexUpstreamException e) {
        if (!e.isRateLimited() || attempt == MAX_ATTEMPTS) {
          throw e;
        }
        sleepFor(e.getRetryAfter() == null ? DEFAULT_RATE_LIMIT_RETRY : e.getRetryAfter());
      } catch (RestClientException e) {
        throw new MangaDexUpstreamException("MangaDex request failed", e);
      }
    }
    throw new MangaDexUpstreamException("MangaDex request failed", null);
  }

  private void throwUpstreamException(
      org.springframework.http.HttpRequest request,
      org.springframework.http.client.ClientHttpResponse response)
      throws java.io.IOException {
    int status = response.getStatusCode().value();
    Duration retryAfter = retryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    throw new MangaDexUpstreamException(
        "MangaDex request failed with status " + status, status, retryAfter);
  }

  private void waitForRequestSlot() {
    synchronized (requestThrottle) {
      long now = System.nanoTime();
      long waitNanos = nextRequestAtNanos - now;
      if (waitNanos > 0) {
        sleepFor(Duration.ofNanos(waitNanos));
        now = System.nanoTime();
      }
      nextRequestAtNanos = Math.max(now, nextRequestAtNanos) + REQUEST_SPACING.toNanos();
    }
  }

  private void sleepFor(Duration delay) {
    try {
      sleeper.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MangaDexUpstreamException("Interrupted while waiting for MangaDex retry", e);
    }
  }

  private static Duration retryAfter(String header) {
    if (header == null || header.isBlank()) {
      return DEFAULT_RATE_LIMIT_RETRY;
    }
    try {
      long seconds = Long.parseLong(header.trim());
      return Duration.ofSeconds(Math.clamp(seconds, 0L, MAX_RATE_LIMIT_RETRY.toSeconds()));
    } catch (NumberFormatException e) {
      return DEFAULT_RATE_LIMIT_RETRY;
    }
  }

  private static RestClient buildRestClient(RestClient.Builder builder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
    requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
    return builder.baseUrl(API_BASE_URL).requestFactory(requestFactory).build();
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static JsonNode requireBody(JsonNode body, String message) {
    if (body == null) {
      throw new MangaDexUpstreamException(message, null);
    }
    return body;
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }
}
