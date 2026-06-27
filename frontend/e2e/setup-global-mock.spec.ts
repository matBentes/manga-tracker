import { test as setup } from '@playwright/test';

setup('global auth mock', async ({ page }) => {
  await page.route(/\/api\/auth\/me/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });
  await page.route(/\/api\/auth\/demo-login/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });
});
