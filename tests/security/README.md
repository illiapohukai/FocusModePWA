# Security tests (v17.17)

Dependency-free tests for the security and privacy baseline. They are development material only:
the service worker never caches `tests/`, and nothing here is loaded by the app.

```bash
node tests/security/run-all.js              # Node suites (import/export, AI boundary, SW policy)
node tests/security/run-all.js --browser    # + Playwright/Chromium suite (spawns python3 http.server)
```

| Suite | Covers |
|---|---|
| `data-security.test.js` | Hostile import (size, depth, arrays, dangerous/reserved keys, IDs, dates, durations, states, duplicates, unknown fields), integrity digest, rollback on write/quota failure, required pre-import backup, scoped export, canonicalisation, full deletion, trust boundary |
| `ai-boundary.test.js` | AI output: safe/unique step IDs, length/list caps, image signature and header-dimension checks, error/request-ID mapping |
| `sw-policy.test.js` | Service-worker routing (Worker bypass, no dev material, CDN allowlist), cacheable responses, `CORE` consistency with `index.html` |
| `browser-security.test.js` | Real UI at 390×844: CSP violations, third-party requests, SW cache contents, offline reload, hostile-backup rendering, import/export/delete flows, AI ID injection, request lock and cancel race, attachment checks |

Environment variables:

- `FM_TAILWIND_STANDIN` — path to a local copy of the Tailwind 3.x Play CDN script. The browser suite
  serves it for `https://cdn.tailwindcss.com` (often unreachable in sandboxes); without it a stub is served.
- `FM_BASE_URL` / `FM_PORT` — use an existing server / choose the spawned server's port.
- `FM_BASELINE_ROOT` — path to an older checkout; enables the "v17.17 export imports in v17.16" check.

Fixtures in `fixtures/` were produced by the **pre-change** v17.16 export code and by hand for the legacy
root-array format; they contain only synthetic data.

The AI Worker is never contacted: browser tests fulfil every `workers.dev` request with a local mock.
