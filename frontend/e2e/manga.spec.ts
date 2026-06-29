import { test, expect, type Page, type Route } from '@playwright/test';

const sampleManga = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'One Punch Man',
  sourceUrl: 'https://sakuramangas.org/manga/one-punch-man/',
  currentChapter: 0,
  latestChapter: 180,
  coverImageUrl: null,
  latestChapterAt: null,
  notificationsEnabled: true,
  lastCheckedAt: null,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

type ApiHandler = (route: Route, pathname: string) => Promise<boolean>;

async function mockApi(page: Page, handleManga: ApiHandler): Promise<void> {
  await page.route(/\/api\/.*/, async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    if (pathname === '/api/auth/me' || pathname === '/api/auth/demo-login') {
      await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
      return;
    }

    if (pathname === '/api/auth/csrf') {
      await route.fulfill({
        headers: { 'X-XSRF-TOKEN': 'csrf-token' },
        json: { token: 'csrf-token' },
      });
      return;
    }

    if (pathname.startsWith('/api/manga')) {
      if (await handleManga(route, pathname)) {
        return;
      }
    }

    throw new Error(`Unhandled API request: ${request.method()} ${request.url()}`);
  });
}

test('add manga by URL - appears in reading list with detected title', async ({ page }) => {
  let mangaList: Array<typeof sampleManga> = [];

  await mockApi(page, async (route, pathname) => {
    const method = route.request().method();

    if (pathname === '/api/manga' && method === 'GET') {
      await route.fulfill({ json: mangaList });
      return true;
    }

    if (pathname === '/api/manga' && method === 'POST') {
      mangaList = [sampleManga];
      await route.fulfill({ status: 201, json: sampleManga });
      return true;
    }

    return false;
  });

  await page.goto('/');
  await expect(page.locator('.state-box')).toHaveText(/No manga tracked yet/);

  await page.fill('.url-input', sampleManga.sourceUrl);
  await page.click('.submit-btn');

  await expect(page.locator('.manga-title').first()).toHaveText(sampleManga.title);
  await expect(page.locator('.form-success')).toBeVisible();
});

test('mark as read - card shows caught up after marking', async ({ page }) => {
  let manga = { ...sampleManga, currentChapter: 5, latestChapter: 10 };

  await mockApi(page, async (route, pathname) => {
    const method = route.request().method();

    if (pathname === '/api/manga' && method === 'GET') {
      await route.fulfill({ json: [manga] });
      return true;
    }

    if (pathname === `/api/manga/${manga.id}/read` && method === 'POST') {
      manga = { ...manga, currentChapter: 10 };
      await route.fulfill({ json: manga });
      return true;
    }

    return false;
  });

  await page.goto('/');
  await expect(page.locator('.badge-new')).toBeVisible();

  await page.click('.read-btn');

  await expect(page.locator('.caught-up')).toBeVisible();
  await expect(page.locator('.manga-card.is-unread')).toHaveCount(0);
  await expect(page.locator('.read-btn')).toHaveText('Mark unread');
});

test('delete manga - removed from list after confirmation', async ({ page }) => {
  let mangaList: Array<typeof sampleManga> = [sampleManga];

  await mockApi(page, async (route, pathname) => {
    const method = route.request().method();

    if (pathname === '/api/manga' && method === 'GET') {
      await route.fulfill({ json: mangaList });
      return true;
    }

    if (pathname === `/api/manga/${sampleManga.id}` && method === 'DELETE') {
      mangaList = [];
      await route.fulfill({ status: 204 });
      return true;
    }

    return false;
  });

  await page.goto('/');
  await expect(page.locator('.manga-title').first()).toHaveText(sampleManga.title);

  page.once('dialog', (dialog) => dialog.accept());
  await page.click('.delete-btn');

  await expect(page.locator('.manga-title')).toHaveCount(0);
  await expect(page.locator('.state-box')).toBeVisible();
});
