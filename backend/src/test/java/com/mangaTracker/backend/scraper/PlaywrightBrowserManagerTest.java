package com.mangaTracker.backend.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
  void fetchPage_returnsDocumentAfterWaitingForMangaMeta() {
    stubSuccessfulFetch(playwright, browser, context, page, "123");

    Document document = browserManager.fetchPage(URL);

    assertThat(document.selectFirst("meta[manga-id]")).isNotNull();
    verify(playwrightFactory).create();
    verify(browserLauncher).launch(playwright);
    verify(page).navigate(eq(URL), any(Page.NavigateOptions.class));
    verify(page).waitForSelector(eq("meta[manga-id]"), any(Page.WaitForSelectorOptions.class));
    verify(context).close();
  }

  @Test
  void fetchPage_reusesSharedBrowserAcrossRequests() {
    stubSuccessfulFetch(playwright, browser, context, page, "123");
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

    stubSuccessfulFetch(playwright, browser, context, page, "123");
    stubSuccessfulFetch(playwright2, browser2, context2, page2, "456");
    when(playwrightFactory.create()).thenReturn(playwright, playwright2);
    when(browser.isConnected()).thenReturn(false);

    browserManager.fetchPage(URL);
    Document refreshedDocument = browserManager.fetchPage(URL);

    assertThat(refreshedDocument.selectFirst("meta[manga-id]").attr("manga-id")).isEqualTo("456");
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
    when(page.waitForSelector(eq("meta[manga-id]"), any(Page.WaitForSelectorOptions.class)))
        .thenThrow(new PlaywrightException("challenge timeout"));

    assertThatThrownBy(() -> browserManager.fetchPage(URL))
        .isInstanceOf(ScrapingException.class)
        .hasMessageContaining("Failed to fetch manga page via Playwright");

    verify(context).close();
  }

  @Test
  void shutdown_closesSharedBrowserState() {
    stubSuccessfulFetch(playwright, browser, context, page, "123");

    browserManager.fetchPage(URL);

    browserManager.shutdown();

    verify(browser).close();
    verify(playwright).close();
  }

  private void stubSuccessfulFetch(
      Playwright playwright, Browser browser, BrowserContext context, Page page, String mangaId) {
    when(playwrightFactory.create()).thenReturn(playwright);
    when(browserLauncher.launch(playwright)).thenReturn(browser);
    when(browser.newContext(any(Browser.NewContextOptions.class))).thenReturn(context);
    when(context.newPage()).thenReturn(page);
    when(page.content())
        .thenReturn("<html><head><meta manga-id=\"" + mangaId + "\" /></head></html>");
  }
}
