package com.mangaTracker.backend.scraper;

import com.mangaTracker.backend.exception.ScrapingException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "scraper.sakura.playwright.enabled", matchIfMissing = true)
public class PlaywrightBrowserManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightBrowserManager.class);
  // Wait for the chapter list to be rendered by the site's own JS. Its presence means both the
  // Cloudflare challenge cleared and the (client-side, security-gated) chapter data has loaded.
  private static final String READY_SELECTOR = ".chapter-list a";
  private static final String SAKURA_REFERER = "https://sakuramangas.org/";
  private static final double PAGE_TIMEOUT_MS = 30_000;

  // Chromium flags that strip the most obvious automation fingerprints. Without these (and the
  // init script below) Cloudflare Bot Management never clears its interactive challenge and the
  // page stays on "Just a moment...". Verified against the live site 2026-06.
  private static final List<String> STEALTH_ARGS =
      List.of(
          "--disable-blink-features=AutomationControlled",
          "--no-sandbox",
          "--disable-dev-shm-usage");

  // Runs before any page script. Masks the JS-visible automation tells Cloudflare probes for.
  private static final String STEALTH_INIT_SCRIPT =
      "Object.defineProperty(navigator,'webdriver',{get:()=>undefined});"
          + "window.chrome={runtime:{}};"
          + "Object.defineProperty(navigator,'plugins',{get:()=>[1,2,3,4,5]});"
          + "Object.defineProperty(navigator,'languages',{get:()=>['pt-BR','pt','en-US','en']});"
          + "const q=window.navigator.permissions.query;"
          + "window.navigator.permissions.query=(p)=>p&&p.name==='notifications'"
          + "?Promise.resolve({state:Notification.permission}):q(p);";

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

  private Playwright playwright;
  private Browser browser;

  @org.springframework.beans.factory.annotation.Autowired
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

  // Thread safety: the Playwright Java connection is NOT safe for unsynchronized concurrent use,
  // and a user-triggered scrape can overlap the scheduled poll job. We therefore serialize the
  // whole browser interaction on browserLock. browserLock is reentrant, so the nested
  // getOrCreateBrowser()/invalidateBrowserIfDisconnected() calls are fine. Scrapes run
  // sequentially — acceptable for a single-site background workload, and it guarantees the shared
  // Browser/BrowserContext are never driven from two threads at once.
  public Document fetchPage(String url) {
    synchronized (browserLock) {
      Browser activeBrowser = getOrCreateBrowser();
      try (BrowserContext context =
          activeBrowser.newContext(
              new Browser.NewContextOptions()
                  .setUserAgent(SakuraMangasScraper.USER_AGENT)
                  .setLocale("pt-BR")
                  .setViewportSize(1280, 800)
                  .setExtraHTTPHeaders(Map.of("Referer", SAKURA_REFERER)))) {
        context.addInitScript(STEALTH_INIT_SCRIPT);
        try (Page page = context.newPage()) {
          page.navigate(url, new Page.NavigateOptions().setTimeout(PAGE_TIMEOUT_MS));
          page.waitForSelector(
              READY_SELECTOR,
              new Page.WaitForSelectorOptions()
                  .setState(WaitForSelectorState.ATTACHED)
                  .setTimeout(PAGE_TIMEOUT_MS));
          return Jsoup.parse(page.content(), url);
        }
      } catch (PlaywrightException e) {
        invalidateBrowserIfDisconnected(activeBrowser);
        throw new ScrapingException("Failed to fetch manga page via Playwright: " + url, e);
      }
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
        new BrowserType.LaunchOptions().setHeadless(true).setArgs(STEALTH_ARGS);
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
}
