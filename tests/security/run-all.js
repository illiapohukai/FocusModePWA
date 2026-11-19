'use strict';
/*
 * Runs the Node security suites; add --browser to also run the Playwright suite.
 *   node tests/security/run-all.js
 *   node tests/security/run-all.js --browser
 */
const { spawnSync } = require('child_process');
const path = require('path');

const suites = ['data-security.test.js', 'ai-boundary.test.js', 'sw-policy.test.js'];
if (process.argv.includes('--browser')) suites.push('browser-security.test.js');

let failed = 0;
for (const suite of suites) {
  const res = spawnSync(process.execPath, [path.join(__dirname, suite)], { stdio: 'inherit', env: process.env });
  if (res.status !== 0) failed += 1;
}
console.log(failed ? `\n${failed} suite(s) failed` : '\nAll security suites passed');
process.exitCode = failed ? 1 : 0;
