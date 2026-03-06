import { test, expect } from '@playwright/test';

const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '**';

const sampleManga = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'One Punch Man',
  sourceUrl: 'https://sakuramangas.org/manga/one-punch-man/',
  currentChapter: 0,
  latestChapter: 180,
  notificationsEnabled: true,
  lastCheckedAt: null,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

test('add manga by URL - appears in reading list with detected title', async ({ page }) => {
  // Initially no manga
  await page.route(`${API_BASE}/api/manga`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: [] });
    }
  });

  await page.goto('/');
  await expect(page.locator('.empty-state')).toBeVisible();

  // Set up POST to return the new manga and subsequent GET to return it in list
  await page.route(`${API_BASE}/api/manga`, async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ status: 201, json: sampleManga });
    } else if (route.request().method() === 'GET') {
      await route.fulfill({ json: [sampleManga] });
    }
  });

  await page.fill('.url-input', sampleManga.sourceUrl);
  await page.click('.submit-btn');

  await expect(page.locator('.manga-title').first()).toHaveText(sampleManga.title);
  await expect(page.locator('.form-success')).toBeVisible();
});

test('update reading progress - chapter number updates in UI', async ({ page }) => {
  const mangaWithChapters = { ...sampleManga, currentChapter: 5, latestChapter: 10 };

  await page.route(`${API_BASE}/api/manga`, async (route) => {
    await route.fulfill({ json: [mangaWithChapters] });
  });

  await page.goto('/');
  await expect(page.locator('.manga-title').first()).toBeVisible();

  const updatedManga = { ...mangaWithChapters, currentChapter: 7 };
  await page.route(`${API_BASE}/api/manga/${mangaWithChapters.id}`, async (route) => {
    if (route.request().method() === 'PATCH') {
      await route.fulfill({ json: updatedManga });
    }
  });

  const chapterInput = page.locator('.chapter-input').first();
  await chapterInput.fill('7');
  await chapterInput.dispatchEvent('change');

  await expect(chapterInput).toHaveValue('7');
});

test('delete manga - removed from list after confirmation', async ({ page }) => {
  await page.route(`${API_BASE}/api/manga`, async (route) => {
    await route.fulfill({ json: [sampleManga] });
  });

  await page.goto('/');
  await expect(page.locator('.manga-title').first()).toHaveText(sampleManga.title);

  await page.route(`${API_BASE}/api/manga/${sampleManga.id}`, async (route) => {
    if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 });
    }
  });

  page.once('dialog', (dialog) => dialog.accept());
  await page.click('.delete-btn');

  await expect(page.locator('.manga-title')).toHaveCount(0);
  await expect(page.locator('.empty-state')).toBeVisible();
});
