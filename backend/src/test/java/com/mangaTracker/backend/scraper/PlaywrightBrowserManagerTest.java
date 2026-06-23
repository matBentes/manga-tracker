package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mangaTracker.backend.exception.ScrapingException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaywrightBrowserManagerTest {

  private static final String URL = "https://sakuramangas.org/obras/chainsaw-man/";
  private static final String READY_SELECTOR = ".chapter-list a";

  @Mock private Playwright playwright;
  @Mock private Browser browser;
  @Mock private BrowserContext context;
  @Mock private Page page;
  @Mock private PlaywrightBrowserManager.PlaywrightFactory playwrightFactory;
  @Mock private PlaywrightBrowserManager.BrowserLauncher browserLauncher;

  private PlaywrightBrowserManager browserManager;

  @BeforeEach
  void setUp() {
    browserManager = new PlaywrightBrowserManager("", playwrightFactory, browserLauncher);
  }

  @Test
  void fetchPage_returnsDocumentAfterStealthSetupAndChapterWait() {
    stubSuccessfulFetch(playwright, browser, context, page, "232");

    Document document = browserManager.fetchPage(URL);

    assertThat(document.selectFirst(READY_SELECTOR)).isNotNull();
    verify(playwrightFactory).create();
    verify(browserLauncher).launch(playwright);
    verify(context).addInitScript(contains("navigator,'webdriver'"));
    verify(page).navigate(eq(URL), any(Page.NavigateOptions.class));
    verify(page).waitForSelector(eq(READY_SELECTOR), any(Page.WaitForSelectorOptions.class));
    verify(context).close();
  }

  @Test
  void fetchPage_reusesSharedBrowserAcrossRequests() {
    stubSuccessfulFetch(playwright, browser, context, page, "232");
    when(browser.isConnected()).thenReturn(true);

    browserManager.fetchPage(URL);
    browserManager.fetchPage(URL);

    verify(playwrightFactory).create();
    verify(browserLauncher).launch(playwright);
    verify(browser, times(2)).newContext(any(Browser.NewContextOptions.class));
  }

  @Test
  void fetchPage_restartsSharedBrowserWhenDisconnected() {
    Playwright playwright2 = org.mockito.Mockito.mock(Playwright.class);
    Browser browser2 = org.mockito.Mockito.mock(Browser.class);
    BrowserContext context2 = org.mockito.Mockito.mock(BrowserContext.class);
    Page page2 = org.mockito.Mockito.mock(Page.class);

    stubSuccessfulFetch(playwright, browser, context, page, "232");
    stubSuccessfulFetch(playwright2, browser2, context2, page2, "456");
    when(playwrightFactory.create()).thenReturn(playwright, playwright2);
    when(browser.isConnected()).thenReturn(false);

    browserManager.fetchPage(URL);
    Document refreshed = browserManager.fetchPage(URL);

    assertThat(refreshed.selectFirst(READY_SELECTOR).text()).isEqualTo("Cap. 456");
    verify(playwrightFactory, times(2)).create();
    verify(browserLauncher, times(2)).launch(any(Playwright.class));
    verify(browser).close();
    verify(playwright).close();
  }

  @Test
  void fetchPage_wrapsPlaywrightFailureAndStillClosesContext() {
    when(playwrightFactory.create()).thenReturn(playwright);
    when(browserLauncher.launch(playwright)).thenReturn(browser);
    when(browser.newContext(any(Browser.NewContextOptions.class))).thenReturn(context);
    when(context.newPage()).thenReturn(page);
    when(page.waitForSelector(eq(READY_SELECTOR), any(Page.WaitForSelectorOptions.class)))
        .thenThrow(new PlaywrightException("challenge timeout"));

    assertThatThrownBy(() -> browserManager.fetchPage(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Failed to fetch manga page via Playwright");

    verify(context).close();
  }

  @Test
  void shutdown_closesSharedBrowserState() {
    stubSuccessfulFetch(playwright, browser, context, page, "232");

    browserManager.fetchPage(URL);
    browserManager.shutdown();

    verify(browser).close();
    verify(playwright).close();
  }

  @Test
  void fetchPage_cleansUpRuntime_whenBrowserLaunchFails() {
    when(playwrightFactory.create()).thenReturn(playwright);
    when(browserLauncher.launch(playwright))
        .thenThrow(new PlaywrightException("chromium executable missing"));

    assertThatThrownBy(() -> browserManager.fetchPage(URL))
        .isInstanceOf(PlaywrightException.class)
        .hasMessageContaining("chromium executable missing");

    verify(playwright).close();
  }

  @Test
  void fetchPage_succeeds_whenConfiguredWithExplicitChromiumExecutable() {
    PlaywrightBrowserManager managerWithPath =
        new PlaywrightBrowserManager("/usr/bin/chromium", playwrightFactory, browserLauncher);
    stubSuccessfulFetch(playwright, browser, context, page, "789");

    Document document = managerWithPath.fetchPage(URL);

    assertThat(document.selectFirst(READY_SELECTOR).text()).isEqualTo("Cap. 789");
  }

  private void stubSuccessfulFetch(
      Playwright playwright, Browser browser, BrowserContext context, Page page, String chapter) {
    when(playwrightFactory.create()).thenReturn(playwright);
    when(browserLauncher.launch(playwright)).thenReturn(browser);
    when(browser.newContext(any(Browser.NewContextOptions.class))).thenReturn(context);
    when(context.newPage()).thenReturn(page);
    when(page.content())
        .thenReturn(
            "<html><body><div class=\"chapter-list\">"
                + "<a href=\"/x/"
                + chapter
                + "\">Cap. "
                + chapter
                + "</a></div></body></html>");
  }
}
