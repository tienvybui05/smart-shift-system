/* Local QA only. Uses Playwright and reads Grafana's password from the ignored secret file. */
const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined });
  const errors = [], checks = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1600, height: 1100 }, timezoneId: 'Asia/Ho_Chi_Minh' });
    const password = fs.readFileSync('.secrets/grafana_admin_password', 'utf8').trim();
    const login = await context.request.post('http://127.0.0.1:23001/login', { data: { user: 'admin', password } });
    assert.equal(login.status(), 200, 'Grafana login');
    checks.push('Grafana accepts local admin login');
    const page = await context.newPage();
    page.on('pageerror', e => errors.push(e.message));
    await page.goto('http://127.0.0.1:23001/d/smartshift-operations?orgId=1&from=now-30m&to=now&refresh=15s');
    for (const name of ['Backend đang hoạt động', 'Yêu cầu mỗi giây', 'Tỷ lệ lỗi HTTP 5xx', 'Độ trễ p95', 'Bộ nhớ JVM heap', 'CPU của ứng dụng', 'Kết nối cơ sở dữ liệu', 'Yêu cầu theo trạng thái HTTP']) {
      await page.getByText(name, { exact: true }).waitFor({ timeout: 60000 });
    }
    checks.push('all eight monitoring panels render');
    await page.getByText('UP', { exact: true }).first().waitFor({ timeout: 30000 });
    checks.push('dashboard displays live UP sample');
    assert.equal(await page.getByText('No data', { exact: true }).count(), 0, 'all panels have samples');
    checks.push('all eight panels receive real metrics');
    await page.screenshot({ path: 'qa-automation/results/monitoring-grafana.png', fullPage: true });
    assert.equal(errors.length, 0, 'no browser JavaScript errors');
    checks.push('no browser JavaScript errors');
    fs.writeFileSync('qa-automation/results/monitoring-browser.json', JSON.stringify({ passed: checks.length, checks, errors }, null, 2));
    checks.forEach(name => console.log('PASS', name));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
