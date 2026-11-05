/* Product UI login, role routing and logout against local QA only. */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fixture = JSON.parse(fs.readFileSync('qa-automation/results/calendar.json', 'utf8'));
const base = 'http://127.0.0.1:23000';

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined });
  const checks = [], errors = [];
  const check = (name, condition) => { assert.ok(condition, name); checks.push(name); console.log('PASS', name); };
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Ho_Chi_Minh' });
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base + '/admin/work-shifts');
    await page.getByRole('heading', { name: 'Đăng nhập hệ thống' }).waitFor();
    check('unauthenticated route redirects to login', new URL(page.url()).pathname === '/login');
    await page.getByLabel('Tên đăng nhập').fill(fixture.manager);
    await page.getByLabel('Mật khẩu', { exact: true }).fill('incorrect-password');
    await page.locator('.login-submit').click();
    await page.locator('.login-alert').waitFor();
    check('incorrect credentials show a visible error', await page.locator('.login-alert').isVisible());
    await page.getByLabel('Mật khẩu', { exact: true }).fill('Demo@123456');
    await page.locator('.login-submit').click();
    await page.locator('.dashboard-shell').waitFor();
    await page.waitForURL(base + '/admin/work-shifts');
    check('UI login returns manager to the requested page', new URL(page.url()).pathname === '/admin/work-shifts');
    const dashboard = page.waitForResponse(response => response.url() === base + '/api/dashboard');
    await page.goto(base + '/');
    check('manager dashboard loads through UI', (await dashboard).status() === 200);
    await page.locator('.dashboard-shell').waitFor();
    await page.locator('.sidebar-nav a[href="/admin/work-shifts"]').waitFor();
    check('manager sees scheduling navigation', await page.locator('.sidebar-nav a[href="/admin/work-shifts"]').isVisible());
    await page.reload();
    await page.locator('.dashboard-shell').waitFor();
    check('remembered login survives page reload', !page.url().includes('/login'));
    await page.locator('.account-button').click();
    await page.getByText('Đăng xuất', { exact: true }).click();
    await page.getByRole('heading', { name: 'Đăng nhập hệ thống' }).waitFor();
    check('logout clears credentials and returns to login', new URL(page.url()).pathname === '/login');
    await page.getByLabel('Tên đăng nhập').fill(fixture.employees[0].username);
    await page.getByLabel('Mật khẩu', { exact: true }).fill('Demo@123456');
    await page.locator('.login-submit').click();
    await page.locator('.dashboard-shell').waitFor();
    check('employee cannot see scheduling administration', await page.locator('.sidebar-nav a[href="/admin/work-shifts"]').count() === 0);
    await page.goto(base + '/admin/work-shifts');
    await page.waitForURL(base + '/');
    check('employee cannot navigate directly to manager route', new URL(page.url()).pathname === '/');
    await page.locator('.sidebar-nav a[href="/my-schedule"]').click();
    await page.getByRole('button', { name: /Xem lịch/ }).waitFor();
    check('employee can access personal schedule', new URL(page.url()).pathname === '/my-schedule');
    check('no product JavaScript errors', errors.length === 0);
    fs.writeFileSync('qa-automation/results/app-browser.json', JSON.stringify({ passed: checks.length, checks, errors }, null, 2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
