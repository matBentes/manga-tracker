const { chromium } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

async function globalSetup(config) {
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.route(/\/api\/auth\/me/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });
  await page.route(/\/api\/auth\/demo-login/, async (route) => {
    await route.fulfill({ json: { username: 'demo', role: 'DEMO' } });
  });

  await page.goto(config.projects[0].use.baseURL + '/');
  await page.waitForLoadState('networkidle');

  const storageState = path.join(process.cwd(), 'playwright', '.auth', 'user.json');
  fs.mkdirSync(path.dirname(storageState), { recursive: true });
  await context.storageState({ path: storageState });

  await browser.close();
}

module.exports = globalSetup;
