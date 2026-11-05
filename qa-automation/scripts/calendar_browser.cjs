/* Run after calendar_e2e.py. Requires PLAYWRIGHT_MODULE and CHROME_PATH; QA stack only. */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fixture = JSON.parse(fs.readFileSync('qa-automation/results/calendar.json', 'utf8'));
const base = 'http://127.0.0.1:23000';
const checks = [], errors = [];
const check = (name, condition) => { assert.ok(condition, name); checks.push(name); console.log('PASS', name); };

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined });
  try {
    const context = await browser.newContext({ timezoneId: 'Asia/Ho_Chi_Minh', viewport: { width: 1680, height: 1100 } });
    const login = await context.request.post(base + '/api/auth/login', { data: { username: fixture.manager, password: 'Demo@123456' } });
    assert.equal(login.status(), 200);
    const auth = await login.json();
    await context.addInitScript(({ accessToken, user }) => {
      sessionStorage.setItem('smart_shift_access_token', accessToken);
      sessionStorage.setItem('smart_shift_current_user', JSON.stringify(user));
    }, auth);
    const board = async () => {
      const response = await context.request.get(`${base}/api/schedule-periods/${fixture.periodId}/board`, { headers: { Authorization: `Bearer ${auth.accessToken}` } });
      assert.equal(response.status(), 200); return response.json();
    };
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', e => errors.push(e.message));
    const url = `${base}/admin/work-shifts?periodId=${fixture.periodId}`;
    await page.goto(url);
    const card = id => page.locator(`[data-shift-id="${id}"]`);
    const employee = index => page.locator(`.roster-people [data-employee-id="${fixture.employees[index].id}"]`);
    await card(fixture.shifts[0].morning).waitFor();
    check('calendar renders all existing shifts', await page.locator('.roster-shift').count() === 15);
    await page.screenshot({ path: 'qa-automation/results/calendar-desktop.png', fullPage: true });

    // Use Chromium's DragEvent/DataTransfer and the real app handlers/API,
    // without depending on OS drag gestures in headless runs.
    const drag = async (from, to, expectedClass) => {
      await from.scrollIntoViewIfNeeded();
      const dataTransfer = await page.evaluateHandle(() => new DataTransfer());
      await from.dispatchEvent('dragstart', { dataTransfer });
      await to.scrollIntoViewIfNeeded();
      await to.dispatchEvent('dragenter', { dataTransfer });
      await to.dispatchEvent('dragover', { dataTransfer });
      await page.waitForFunction(({ id, state }) => document.querySelector(`[data-shift-id="${id}"]`)?.classList.contains(state), { id: await to.getAttribute('data-shift-id'), state: expectedClass }, { timeout: 15000 });
      await to.dispatchEvent('drop', { dataTransfer });
      await from.dispatchEvent('dragend', { dataTransfer }).catch(() => {});
      await dataTransfer.dispose();
      await page.waitForFunction(() => document.querySelector('.roster-board')?.getAttribute('aria-busy') === 'false' && !document.querySelector('.roster-selection'));
    };
    const before = await board();
    await drag(employee(3), card(fixture.shifts[0].morning), 'drop-invalid');
    check('invalid native drop shows error and restores persisted roster',
      (await board()).shifts.find(c => c.shift.id === fixture.shifts[0].morning).assignments.length === before.shifts.find(c => c.shift.id === fixture.shifts[0].morning).assignments.length && await page.locator('.roster-board .ant-alert-error').isVisible());

    await drag(employee(3), card(fixture.shifts[0].evening), 'drop-valid');
    let saved = (await board()).shifts.find(c => c.shift.id === fixture.shifts[0].evening).assignments.find(a => a.userId === fixture.employees[3].id);
    check('native employee drop persists assignment', Boolean(saved));
    const assignmentId = saved.id;
    await page.locator(`[data-assignment-id="${assignmentId}"]`).waitFor();
    await drag(page.locator(`[data-assignment-id="${assignmentId}"]`), card(fixture.shifts[2].evening), 'drop-valid');
    const moved = await board();
    saved = moved.shifts.find(c => c.shift.id === fixture.shifts[2].evening).assignments.find(a => a.id === assignmentId);
    check('native move preserves assignment identity and updates both cards', Boolean(saved) && saved.version === 1 && !moved.shifts.find(c => c.shift.id === fixture.shifts[0].evening).assignments.some(a => a.id === assignmentId));

    await employee(4).click();
    await card(fixture.shifts[0].evening).locator('.roster-drop-action').click();
    await page.waitForFunction(() => document.querySelector('.roster-board')?.getAttribute('aria-busy') === 'false' && !document.querySelector('.roster-selection'));
    check('click selection offers accessible alternative to drag', (await board()).shifts.find(c => c.shift.id === fixture.shifts[0].evening).assignments.some(a => a.userId === fixture.employees[4].id));

    await page.locator('.roster-calendar-controls .ant-segmented-item').last().click();
    await page.locator('.roster-calendar.is-dayGridMonth').waitFor();
    check('month view renders correctly', await page.locator('.roster-shift').count() === 15);
    await page.locator('.roster-calendar-controls .ant-segmented-item').nth(1).click();
    await page.locator('.roster-calendar.is-dayGridWeek').waitFor();
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: 'qa-automation/results/calendar-desktop.png', fullPage: true });

    const mobile = await context.newPage({ viewport: { width: 390, height: 844 } });
    await mobile.setViewportSize({ width: 390, height: 844 });
    mobile.on('pageerror', e => errors.push(e.message));
    await mobile.goto(url);
    await mobile.locator('.roster-calendar.is-dayGridDay').waitFor();
    check('mobile starts with readable single-day view and no horizontal overflow', await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    check('mobile shift cards fill day column', await mobile.locator('.roster-shift').first().evaluate(e => e.clientWidth > 300));
    await mobile.locator('.roster-people-mobile').click();
    await mobile.locator(`.ant-drawer [data-employee-id="${fixture.employees[7].id}"]`).click();
    await mobile.locator(`[data-shift-id="${fixture.shifts[0].evening}"] .roster-drop-action`).click();
    await mobile.waitForFunction(() => document.querySelector('.roster-board')?.getAttribute('aria-busy') === 'false' && !document.querySelector('.roster-selection'));
    check('mobile drawer selection saves assignment', (await board()).shifts.find(c => c.shift.id === fixture.shifts[0].evening).assignments.some(a => a.userId === fixture.employees[7].id));
    await mobile.evaluate(() => scrollTo(0, 0));
    await mobile.screenshot({ path: 'qa-automation/results/calendar-mobile.png', fullPage: true });
    await mobile.close();

    await page.goto(`${base}/admin/work-shifts?periodId=${fixture.readOnlyPeriodId}`);
    await page.locator('.roster-period-badge:not(.is-draft)').waitFor();
    check('published calendar disables dragging and assignment actions',
      await page.locator('.roster-person:not(:disabled), .roster-assignee[draggable="true"], .roster-drop-action:not(:disabled)').count() === 0);
    check('no browser JavaScript errors', errors.length === 0);
    fs.writeFileSync('qa-automation/results/calendar-browser.json', JSON.stringify({ passed: checks.length, checks, errors }, null, 2));
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
