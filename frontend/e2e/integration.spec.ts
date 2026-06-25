import { test, expect } from '@playwright/test';

/**
 * Integration tests that run against the real backend (docker compose).
 * Run with: ./run-e2e-integration.sh
 */

test.describe('Dashboard', () => {
  test('shows empty state with add-manga form', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Reading List' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Add Manga' })).toBeVisible();
    await expect(page.getByRole('textbox', { name: /manga URL/i })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add Manga' })).toBeDisabled();
  });

  test('add manga button stays disabled when URL is empty', async ({ page }) => {
    await page.goto('/');

    const input = page.getByRole('textbox', { name: /manga URL/i });
    await input.fill('');

    await expect(page.getByRole('button', { name: 'Add Manga' })).toBeDisabled();
  });

  test('shows client-side validation error for non-URL text', async ({ page }) => {
    await page.goto('/');

    const input = page.getByRole('textbox', { name: /manga URL/i });
    await input.fill('not-a-url');

    await expect(page.getByRole('button', { name: 'Add Manga' })).toBeDisabled();
    await expect(page.getByText(/valid URL starting with http/i)).toBeVisible();
  });

  test('enables button for valid URL format', async ({ page }) => {
    await page.goto('/');

    const input = page.getByRole('textbox', { name: /manga URL/i });
    await input.fill('https://example.com/manga/test');

    await expect(page.getByRole('button', { name: 'Add Manga' })).toBeEnabled();
    await expect(page.getByText(/valid URL starting with http/i)).not.toBeVisible();
  });

  test('navigation links work', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: 'Settings' }).click();
    await expect(page).toHaveURL(/\/settings/);

    await page.getByRole('link', { name: 'Reading List' }).click();
    await expect(page).toHaveURL(/\/$/);
  });
});

test.describe('Settings', () => {
  test('shows the push notification settings page', async ({ page }) => {
    await page.goto('/settings');

    await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
    await expect(page.getByText('Phone Notifications')).toBeVisible();
    await expect(
      page.getByText(/push notification on this device when a tracked manga has a new chapter/i),
    ).toBeVisible();
  });

  test('shows the daily new-chapter check schedule', async ({ page }) => {
    await page.goto('/settings');

    await expect(page.getByText('New-chapter check')).toBeVisible();
    await expect(page.getByText(/Every day at 08:00/i)).toBeVisible();
  });

  test('exposes a push control (toggle or unsupported notice)', async ({ page }) => {
    await page.goto('/settings');

    // Push support varies by browser/context. Either the enable/disable toggle
    // or the "not supported" notice must be present — never neither.
    const toggle = page.getByRole('button', { name: /phone notifications/i });
    const unsupported = page.getByText(/Push notifications aren't supported/i);

    await expect(toggle.or(unsupported).first()).toBeVisible();
  });
});
