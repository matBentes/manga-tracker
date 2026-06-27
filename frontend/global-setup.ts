import { chromium, GlobalSetup } from '@playwright/test';

const globalSetup: GlobalSetup = async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.route(/\/api\/auth\/me/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });
  await page.route(/\/api\/auth\/demo-login/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });

  await browser.close();
};

export default globalSetup;
