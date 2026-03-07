package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.ScrapingException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlaywrightBrowserManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightBrowserManager.class);
  private static final String READY_SELECTOR = "meta[manga-id]";
  private static final String SAKURA_REFERER = "https://sakuramangas.org/";
  private static final double PAGE_TIMEOUT_MS = 30_000;

  @FunctionalInterface
  interface PlaywrightFactory {
    Playwright create();
  }

  @FunctionalInterface
  interface BrowserLauncher {
    Browser launch(Playwright playwright);
  }

  private final Object browserLock = new Object();
  private final String chromiumExecutablePath;
  private final PlaywrightFactory playwrightFactory;
  private final BrowserLauncher browserLauncher;
  private final ThreadLocal<Map<String, String>> sessionCookies = new ThreadLocal<>();

  private Playwright playwright;
  private Browser browser;

  public PlaywrightBrowserManager(
      @Value("${scraper.sakura.chromium-executable:}") String chromiumExecutablePath) {
    this(
        chromiumExecutablePath,
        Playwright::create,
        playwright -> launchBrowser(playwright, chromiumExecutablePath));
  }

  PlaywrightBrowserManager(
      String chromiumExecutablePath,
      PlaywrightFactory playwrightFactory,
      BrowserLauncher browserLauncher) {
    this.chromiumExecutablePath = chromiumExecutablePath == null ? "" : chromiumExecutablePath;
    this.playwrightFactory = playwrightFactory;
    this.browserLauncher = browserLauncher;
  }

  // Thread safety: Browser.newContext() is safe to call concurrently per Playwright docs.
  // Each BrowserContext is fully isolated (own cookies, cache, storage) and closed after use,
  // so concurrent scrape requests do not share any mutable state outside the synchronized
  // getOrCreateBrowser() call.
  public Document fetchPage(String url) {
    Browser activeBrowser = getOrCreateBrowser();
    try (BrowserContext context =
        activeBrowser.newContext(
            new Browser.NewContextOptions()
                .setUserAgent(SakuraMangasScraper.USER_AGENT)
                .setExtraHTTPHeaders(Map.of("Referer", SAKURA_REFERER)))) {
      Page page = context.newPage();
      page.navigate(url, new Page.NavigateOptions().setTimeout(PAGE_TIMEOUT_MS));
      page.waitForSelector(
          READY_SELECTOR,
          new Page.WaitForSelectorOptions()
              .setState(WaitForSelectorState.ATTACHED)
              .setTimeout(PAGE_TIMEOUT_MS));
      sessionCookies.set(extractCookies(context.cookies()));
      return Jsoup.parse(page.content(), url);
    } catch (PlaywrightException e) {
      sessionCookies.remove();
      invalidateBrowserIfDisconnected(activeBrowser);
      throw new ScrapingException("Failed to fetch manga page via Playwright: " + url, e);
    }
  }

  public void applySessionCookies(Connection connection) {
    Map<String, String> cookies = sessionCookies.get();
    if (cookies != null && !cookies.isEmpty()) {
      connection.cookies(cookies);
    }
  }

  @PreDestroy
  public void shutdown() {
    synchronized (browserLock) {
      closeBrowserState();
    }
  }

  private Browser getOrCreateBrowser() {
    synchronized (browserLock) {
      if (browser != null && browser.isConnected()) {
        return browser;
      }

      closeBrowserState();
      try {
        playwright = playwrightFactory.create();
        browser = browserLauncher.launch(playwright);
      } catch (RuntimeException e) {
        closeBrowserState();
        throw e;
      }

      if (chromiumExecutablePath.isBlank()) {
        LOGGER.info("Started shared Playwright Chromium browser for Sakura scraper");
      } else {
        LOGGER.info(
            "Started shared Playwright Chromium browser for Sakura scraper using executable {}",
            chromiumExecutablePath);
      }
      return browser;
    }
  }

  private static Browser launchBrowser(Playwright playwright, String chromiumExecutablePath) {
    BrowserType.LaunchOptions options =
        new BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(List.of("--disable-dev-shm-usage"));
    if (chromiumExecutablePath != null && !chromiumExecutablePath.isBlank()) {
      options.setExecutablePath(Path.of(chromiumExecutablePath));
    }
    return playwright.chromium().launch(options);
  }

  private void invalidateBrowserIfDisconnected(Browser activeBrowser) {
    synchronized (browserLock) {
      if (browser == activeBrowser && browser != null && !browser.isConnected()) {
        LOGGER.warn("Playwright browser disconnected; resetting shared browser state");
        closeBrowserState();
      }
    }
  }

  private void closeBrowserState() {
    sessionCookies.remove();

    if (browser != null) {
      try {
        browser.close();
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to close Playwright browser cleanly", e);
      } finally {
        browser = null;
      }
    }

    if (playwright != null) {
      try {
        playwright.close();
      } catch (RuntimeException e) {
        LOGGER.warn("Failed to close Playwright runtime cleanly", e);
      } finally {
        playwright = null;
      }
    }
  }

  private Map<String, String> extractCookies(List<Cookie> cookies) {
    return cookies.stream()
        .collect(
            Collectors.toMap(
                cookie -> cookie.name,
                cookie -> cookie.value,
                (left, right) -> right,
                LinkedHashMap::new));
  }
}
