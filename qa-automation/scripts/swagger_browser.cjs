/* Exercises Swagger UI against the isolated local QA stack. Never saves JWT/passwords. */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = 'http://127.0.0.1:23000';

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined });
  const checks = [], errors = [];
  const check = (name, value = true) => { assert.ok(value, name); checks.push(name); console.log('PASS', name); };
  try {
    const context = await browser.newContext({ viewport: { width: 1520, height: 1100 }, timezoneId: 'Asia/Ho_Chi_Minh' });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    const externalRequests = [];
    page.on('request', request => {
      if (!new URL(request.url()).hostname.match(/^(127\.0\.0\.1|localhost)$/)) externalRequests.push(request.url());
    });
    const openOperation = async (tag, method, path) => {
      const section = page.locator('.opblock-tag-section').filter({ has: page.locator('.opblock-tag').filter({ hasText: tag }) });
      if (!(await section.getAttribute('class')).includes('is-open')) await section.locator('.opblock-tag').click();
      const block = section.locator(`.opblock-${method}`).filter({ has: page.locator(`.opblock-summary-path[data-path="${path}"]`) });
      if (!(await block.getAttribute('class')).includes('is-open')) await block.locator('.opblock-summary-control').click();
      await block.locator('.try-out__btn').click();
      return block;
    };
    await page.goto(base + '/swagger-ui/index.html');
    await page.getByRole('heading', { name: /Smart Shift · Tài liệu API/ }).waitFor({ timeout: 60000 });
    check('Swagger renders through the frontend proxy', await page.locator('.opblock-tag').count() === 23);
    check('Swagger has no resolver or loading errors', await page.locator('.errors-wrapper').count() === 0);
    let block = await openOperation('01 · Xác thực', 'post', '/api/auth/login');
    await block.locator('textarea').fill(JSON.stringify({ username: 'admin', password: 'Demo@123456' }, null, 2));
    let waiting = page.waitForResponse(response => response.url() === base + '/api/auth/login' && response.request().method() === 'POST');
    await block.locator('.execute').click();
    let response = await waiting;
    assert.equal(response.status(), 200);
    const token = (await response.json()).accessToken;
    check('Try it out logs into the actual QA backend', Boolean(token));

    await page.locator('.scheme-container .authorize').click();
    const bearer = page.locator('.auth-container').filter({ hasText: 'bearerAuth' });
    await bearer.locator('input').fill(token);
    await bearer.locator('button.authorize').click();
    await page.locator('.modal-ux .btn-done').last().click();
    block = await openOperation('01 · Xác thực', 'get', '/api/auth/me');
    waiting = page.waitForResponse(response => response.url() === base + '/api/auth/me');
    await block.locator('.execute').click();
    response = await waiting;
    check('Authorize attaches JWT and protected request succeeds', response.status() === 200 && (await response.json()).username === 'admin' && response.request().headers().authorization === 'Bearer ' + token);

    block = await openOperation('03 · Chi nhánh', 'post', '/api/locations');
    const code = 'SW_UI_' + Date.now();
    await block.locator('textarea').fill(JSON.stringify({ code, name: 'Swagger UI QA', timezone: 'Asia/Ho_Chi_Minh', active: true }, null, 2));
    waiting = page.waitForResponse(response => response.url() === base + '/api/locations' && response.request().method() === 'POST');
    await block.locator('.execute').click();
    response = await waiting;
    check('Try it out creates a QA location with documented 201', response.status() === 201 && Number.isInteger((await response.json()).id));

    await page.reload();
    await page.locator('.opblock-tag').first().waitFor();
    block = await openOperation('01 · Xác thực', 'get', '/api/auth/me');
    waiting = page.waitForResponse(response => response.url() === base + '/api/auth/me');
    await block.locator('.execute').click();
    response = await waiting;
    check('refresh clears authorization and protected request returns 401', response.status() === 401 && !response.request().headers().authorization);

    // Capture a clean documentation page with no token, credentials or business response on screen.
    await page.goto(base + '/swagger-ui/index.html');
    await page.locator('.opblock-tag').first().waitFor();
    await page.screenshot({ path: 'qa-automation/results/swagger-desktop.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: 'qa-automation/results/swagger-mobile.png', fullPage: true });
    check('Swagger remains usable on a narrow viewport', await page.locator('.opblock-tag').count() === 23);
    check('no JavaScript errors', errors.length === 0);
    check('Swagger does not contact a remote validator or CDN', externalRequests.length === 0);
    fs.writeFileSync('qa-automation/results/swagger-browser.json', JSON.stringify({ passed: checks.length, checks, errors }, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
