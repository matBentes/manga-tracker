import { test, expect } from '@playwright/test';

/**
 * Integration tests that run against the real backend (docker compose).
 * Run with: ./run-e2e-integration.sh
 */

test.describe('Dashboard', () => {
  test('shows empty state with add-manga form', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Manga Reading List' })).toBeVisible();
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

  test('adds a Sakura manga through the real backend', async ({ page }) => {
    await page.goto('/');

    const input = page.getByRole('textbox', { name: /manga URL/i });
    await input.fill('https://sakuramangas.org/obras/chainsaw-man/');
    await page.getByRole('button', { name: 'Add Manga' }).click();

    await expect(page.getByRole('button', { name: /adding/i })).toBeVisible();
    await expect(page.getByText(/has been added to your reading list/i)).toBeVisible({
      timeout: 60000,
    });
    await expect(page.locator('.manga-title').first()).toHaveText(/Chainsaw Man/i, {
      timeout: 60000,
    });
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
  test('loads settings form with values from backend', async ({ page }) => {
    await page.goto('/settings');

    await expect(page.getByRole('heading', { name: 'Notification Settings' })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: /email notifications/i })).toBeVisible();
    await expect(page.getByRole('textbox', { name: /notification email/i })).toBeVisible();
    await expect(page.getByRole('spinbutton', { name: /poll interval/i })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save Settings' })).toBeVisible();
  });

  test('saves settings and shows success message', async ({ page }) => {
    await page.goto('/settings');

    const emailInput = page.getByRole('textbox', { name: /notification email/i });
    await emailInput.fill('integration-test@example.com');
    await page.getByRole('button', { name: 'Save Settings' }).click();

    await expect(page.getByText(/settings saved successfully/i)).toBeVisible();
  });

  test('settings persist after page reload', async ({ page }) => {
    await page.goto('/settings');

    const emailInput = page.getByRole('textbox', { name: /notification email/i });
    await emailInput.fill('persist-test@example.com');
    await page.getByRole('button', { name: 'Save Settings' }).click();
    await expect(page.getByText(/settings saved successfully/i)).toBeVisible();

    await page.reload();

    await expect(page.getByRole('textbox', { name: /notification email/i })).toHaveValue(
      'persist-test@example.com',
    );
  });

  test('shows validation error for invalid email', async ({ page }) => {
    await page.goto('/settings');

    const emailInput = page.getByRole('textbox', { name: /notification email/i });
    await emailInput.fill('not-an-email');

    await expect(page.getByText(/valid email address/i)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Save Settings' })).toBeDisabled();
  });

  test('clears email validation error when valid email entered', async ({ page }) => {
    await page.goto('/settings');

    const emailInput = page.getByRole('textbox', { name: /notification email/i });
    await emailInput.fill('bad');
    await expect(page.getByText(/valid email address/i)).toBeVisible();

    await emailInput.fill('good@example.com');
    await expect(page.getByText(/valid email address/i)).not.toBeVisible();
    await expect(page.getByRole('button', { name: 'Save Settings' })).toBeEnabled();
  });

  test('toggle notifications checkbox and save', async ({ page }) => {
    await page.goto('/settings');

    const checkbox = page.getByRole('checkbox', { name: /email notifications/i });
    const wasChecked = await checkbox.isChecked();

    await checkbox.click();
    await page.getByRole('button', { name: 'Save Settings' }).click();
    await expect(page.getByText(/settings saved successfully/i)).toBeVisible();

    await page.reload();

    if (wasChecked) {
      await expect(checkbox).not.toBeChecked();
    } else {
      await expect(checkbox).toBeChecked();
    }

    // Restore original state
    await checkbox.click();
    await page.getByRole('button', { name: 'Save Settings' }).click();
  });
});
