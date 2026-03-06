package com.mangaTracker.backend.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mangaTracker.backend.exception.ScrapingException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Scraper for sakuramangas.org.
 *
 * <p>The site uses a custom API with a SHA-256 proof-of-work security challenge. Each manga page
 * exposes three meta tags (manga-id, header-challenge, csrf-token). The challenge is decoded,
 * combined with a numeric key extracted from a companion security JS file, and hashed 29 times with
 * SHA-256 to produce a proof. The proof is then sent to private API endpoints that return the manga
 * title (JSON) and chapter list (HTML).
 */
@Component
public class SakuraMangasScraper implements MangaScraper {

  private static final Logger LOGGER = LoggerFactory.getLogger(SakuraMangasScraper.class);
  private static final String SUPPORTED_HOST = "sakuramangas.org";
  private static final String BASE_URL = "https://sakuramangas.org";
  private static final String SECURITY_JS_URL = BASE_URL + "/dist/sakura/global/security.oby.js";
  private static final String MANGA_INFO_URL =
      BASE_URL + "/dist/sakura/models/manga/__obf__manga_info.php";
  private static final String MANGA_CHAPTERS_URL =
      BASE_URL + "/dist/sakura/models/manga/__obf__manga_capitulos.php";
  private static final String USER_AGENT =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
          + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final String HEADER_REFERER = "Referer";
  private static final String HEADER_VKEY1 = "X-Verification-Key-1";
  private static final String HEADER_VKEY2 = "X-Verification-Key-2";
  private static final String HEADER_X_REQUESTED_WITH = "X-Requested-With";
  private static final String HEADER_CSRF_TOKEN = "X-CSRF-Token";
  private static final String HEADER_XML_HTTP_REQUEST = "XMLHttpRequest";
  private static final int TIMEOUT_MS = 15000;
  private static final long KEYS_TTL_MS = 3_600_000L;
  private static final int PROOF_ROUNDS = 29;

  // Patterns applied to the (possibly deobfuscated) security.oby.js
  private static final Pattern MANGA_INFO_KEY_PATTERN =
      Pattern.compile("manga_info[:\\s,]++['\"]?+\\s*+(\\d{5,})");
  private static final Pattern VKEY1_PATTERN =
      Pattern.compile(HEADER_VKEY1 + "['\"].*?['\"]([A-Za-z0-9+/=_\\-]{8,})['\"]");
  private static final Pattern VKEY2_PATTERN =
      Pattern.compile(HEADER_VKEY2 + "['\"].*?['\"]([A-Za-z0-9+/=_\\-]{8,})['\"]");

  // ── Testable HTTP abstractions ──────────────────────────────────────────────

  @FunctionalInterface
  interface PageFetcher {
    Document fetch(String url) throws IOException;
  }

  @FunctionalInterface
  interface ScriptFetcher {
    String fetch(String url) throws IOException;
  }

  @FunctionalInterface
  interface ApiCaller {
    String call(String url, Map<String, String> data, Map<String, String> headers)
        throws IOException;
  }

  // ── Fields ─────────────────────────────────────────────────────────────────

  private final PageFetcher pageFetcher;
  private final ScriptFetcher scriptFetcher;
  private final ApiCaller apiCaller;
  private final ObjectMapper objectMapper;

  private SakuraMangasKeys cachedKeys;
  private long keysLoadedAt = 0;

  record SakuraMangasKeys(long mangaInfo, String verificationKey1, String verificationKey2) {}

  // ── Constructors ───────────────────────────────────────────────────────────

  /** Production constructor — uses real Jsoup HTTP calls. */
  public SakuraMangasScraper() {
    this.objectMapper = new ObjectMapper();
    this.pageFetcher =
        url ->
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header(HEADER_REFERER, BASE_URL + "/")
                .timeout(TIMEOUT_MS)
                .get();
    this.scriptFetcher =
        url ->
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header(HEADER_REFERER, BASE_URL + "/")
                .timeout(TIMEOUT_MS)
                .ignoreContentType(true)
                .execute()
                .body();
    this.apiCaller =
        (url, data, headers) -> {
          Connection conn =
              Jsoup.connect(url)
                  .userAgent(USER_AGENT)
                  .header(HEADER_REFERER, BASE_URL + "/")
                  .header(HEADER_X_REQUESTED_WITH, HEADER_XML_HTTP_REQUEST)
                  .timeout(TIMEOUT_MS)
                  .ignoreContentType(true)
                  .method(Connection.Method.POST);
          data.forEach(conn::data);
          headers.forEach(conn::header);
          return conn.execute().body();
        };
  }

  /** Test constructor — allows injecting mocked fetchers. */
  SakuraMangasScraper(PageFetcher pageFetcher, ScriptFetcher scriptFetcher, ApiCaller apiCaller) {
    this.pageFetcher = pageFetcher;
    this.scriptFetcher = scriptFetcher;
    this.apiCaller = apiCaller;
    this.objectMapper = new ObjectMapper();
  }

  // ── MangaScraper API ───────────────────────────────────────────────────────

  @Override
  public boolean supports(String url) {
    return url != null && url.contains(SUPPORTED_HOST);
  }

  @Override
  public ScrapedManga scrape(String url) throws ScrapingException {
    Document doc = fetchPage(url);

    Element mangaIdMeta = doc.selectFirst("meta[manga-id]");
    Element challengeMeta = doc.selectFirst("meta[name=header-challenge]");
    Element csrfMeta = doc.selectFirst("meta[name=csrf-token]");

    if (mangaIdMeta == null || challengeMeta == null || csrfMeta == null) {
      LOGGER.warn(
          "Missing meta tags at {} — manga-id={} header-challenge={} csrf-token={}",
          url,
          mangaIdMeta != null,
          challengeMeta != null,
          csrfMeta != null);
      throw new ScrapingException("Could not find security tokens on manga page: " + url);
    }

    String mangaId = mangaIdMeta.attr("manga-id");
    String challenge = challengeMeta.attr("content");
    String csrfToken = csrfMeta.attr("content");
    LOGGER.debug("manga-id={} challenge-length={}", mangaId, challenge.length());

    SakuraMangasKeys keys = getKeys();
    String proof = generateProof(challenge, keys.mangaInfo());

    String title = fetchTitle(mangaId, challenge, proof, csrfToken, keys);
    int latestChapter = fetchLatestChapter(mangaId, challenge, proof, csrfToken, keys);
    return new ScrapedManga(title, latestChapter);
  }

  // ── Security key loading ───────────────────────────────────────────────────

  private synchronized SakuraMangasKeys getKeys() {
    if (cachedKeys == null || System.currentTimeMillis() - keysLoadedAt >= KEYS_TTL_MS) {
      cachedKeys = loadKeys();
      keysLoadedAt = System.currentTimeMillis();
    }
    return cachedKeys;
  }

  private SakuraMangasKeys loadKeys() {
    String script;
    try {
      script = scriptFetcher.fetch(SECURITY_JS_URL);
    } catch (IOException e) {
      throw new ScrapingException("Failed to fetch security JS", e);
    }

    long mangaInfoKey = extractLong(script, MANGA_INFO_KEY_PATTERN, "manga_info");
    String vKey1 = extractString(script, VKEY1_PATTERN, HEADER_VKEY1);
    String vKey2 = extractString(script, VKEY2_PATTERN, HEADER_VKEY2);

    LOGGER.debug(
        "Keys loaded — manga_info={} key1-present={} key2-present={}",
        mangaInfoKey,
        !vKey1.isBlank(),
        !vKey2.isBlank());
    return new SakuraMangasKeys(mangaInfoKey, vKey1, vKey2);
  }

  private long extractLong(String script, Pattern pattern, String name) {
    Matcher m = pattern.matcher(script);
    if (m.find()) {
      try {
        return Long.parseLong(m.group(1));
      } catch (NumberFormatException e) {
        LOGGER.warn("Failed to parse {} value '{}' as long", name, m.group(1));
      }
    }
    LOGGER.warn("Pattern for {} not found in security script", name);
    return 0L;
  }

  private String extractString(String script, Pattern pattern, String name) {
    Matcher m = pattern.matcher(script);
    if (m.find()) {
      return m.group(1);
    }
    LOGGER.warn("Pattern for {} not found in security script", name);
    return "";
  }

  // ── Proof generation ───────────────────────────────────────────────────────

  /**
   * Generates the SHA-256 proof-of-work.
   *
   * <p>The challenge is a base64 string of the form {@code address/middle/pathSegment}. The seed is
   * {@code address + USER_AGENT + key + pathSegment}, then SHA-256 is applied 29 times.
   */
  String generateProof(String base64Challenge, long key) {
    try {
      byte[] decoded = Base64.getDecoder().decode(base64Challenge);
      String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
      String[] parts = decodedStr.split("/");
      if (parts.length != 3) {
        throw new ScrapingException(
            "Invalid challenge format: expected 3 slash-separated parts, got " + parts.length);
      }

      String address = parts[0];
      String pathSegment = parts[2];
      String result = address + USER_AGENT + key + pathSegment;

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (int i = 0; i < PROOF_ROUNDS; i++) {
        byte[] hash = digest.digest(result.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.reset();
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) {
          sb.append(String.format("%02x", b));
        }
        result = sb.toString();
      }
      return result;
    } catch (NoSuchAlgorithmException e) {
      throw new ScrapingException("SHA-256 not available", e);
    }
  }

  // ── API calls ──────────────────────────────────────────────────────────────

  private Document fetchPage(String url) {
    try {
      return pageFetcher.fetch(url);
    } catch (IOException e) {
      throw new ScrapingException("Failed to fetch manga page: " + url, e);
    }
  }

  private String fetchTitle(
      String mangaId, String challenge, String proof, String csrfToken, SakuraMangasKeys keys) {
    Map<String, String> data =
        Map.of(
            "manga_id", mangaId,
            "dataType", "json",
            "challenge", challenge,
            "proof", proof);
    Map<String, String> headers =
        Map.of(
            HEADER_VKEY1, keys.verificationKey1(),
            HEADER_VKEY2, keys.verificationKey2(),
            HEADER_CSRF_TOKEN, csrfToken);
    String body;
    try {
      body = apiCaller.call(MANGA_INFO_URL, data, headers);
    } catch (IOException e) {
      throw new ScrapingException("Failed to call manga info API", e);
    }
    LOGGER.debug("Manga info response: {}", body.length() > 200 ? body.substring(0, 200) : body);
    try {
      JsonNode json = objectMapper.readTree(body);
      String title = json.path("titulo").asText();
      if (title == null || title.isBlank()) {
        throw new ScrapingException(
            "Blank title in manga info response — API may have rejected the proof. Body: " + body);
      }
      return title;
    } catch (IOException e) {
      throw new ScrapingException("Failed to parse manga info response: " + body, e);
    }
  }

  private int fetchLatestChapter(
      String mangaId, String challenge, String proof, String csrfToken, SakuraMangasKeys keys) {
    Map<String, String> data =
        Map.of(
            "manga_id",
            mangaId,
            "offset",
            "0",
            "order",
            "desc",
            "limit",
            "1",
            "challenge",
            challenge,
            "proof",
            proof);
    Map<String, String> headers =
        Map.of(
            HEADER_VKEY1, keys.verificationKey1(),
            HEADER_VKEY2, keys.verificationKey2(),
            HEADER_CSRF_TOKEN, csrfToken);
    String body;
    try {
      body = apiCaller.call(MANGA_CHAPTERS_URL, data, headers);
    } catch (IOException e) {
      throw new ScrapingException("Failed to call chapters API", e);
    }
    LOGGER.debug("Chapters response: {}", body.length() > 200 ? body.substring(0, 200) : body);

    Document doc = Jsoup.parse(body);
    Element numCap = doc.selectFirst(".capitulo-item .num-capitulo");
    if (numCap == null) {
      LOGGER.warn(
          "No .capitulo-item .num-capitulo in chapters response. Snippet: {}",
          body.length() > 300 ? body.substring(0, 300) : body);
      throw new ScrapingException("Could not find chapter list in API response");
    }

    String dataChapter = numCap.attr("data-chapter");
    if (!dataChapter.isBlank()) {
      try {
        return (int) Float.parseFloat(dataChapter);
      } catch (NumberFormatException e) {
        LOGGER.warn("Failed to parse data-chapter='{}' as number", dataChapter);
      }
    }

    // Fallback: parse number from text (e.g. "Capítulo 197")
    Matcher m = Pattern.compile("(\\d+)").matcher(numCap.text());
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    throw new ScrapingException("Could not extract chapter number from: " + numCap.text());
  }
}
