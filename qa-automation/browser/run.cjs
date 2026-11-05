const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const suites = ['app_browser.cjs', 'calendar_browser.cjs', 'swagger_browser.cjs'];
const environment = { ...process.env, PLAYWRIGHT_MODULE: require.resolve('playwright') };
fs.mkdirSync(path.join(root, 'qa-automation/results'), { recursive: true });
for (const suite of suites) {
  console.log(`Running ${suite}`);
  const result = spawnSync(process.execPath, [path.join(root, 'qa-automation/scripts', suite)], {
    cwd: root, env: environment, stdio: 'inherit', timeout: 180000,
  });
  if (result.error || result.status !== 0) {
    console.error(`Browser suite failed: ${suite}${result.error ? ` (${result.error.code})` : ''}`);
    process.exit(result.status || 1);
  }
}
console.log('All browser suites passed');
