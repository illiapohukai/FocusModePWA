# REGRESSION_REPORT.md — v17.4.2 Gemini 3.8 Flash Integration

## 1. Scope & Honest Test Boundaries
- **Verified locally (Node.js `node:test` + syntax validation)**:
  - 24/24 required mocked Cloudflare Worker (`worker.js`) integration tests + constant assertions (`MODEL = "gemini-3.8-flash"`, `MAX_ATTEMPTS = 2`, per-attempt timeouts `18s/14s` text and `24s/20s` image, total budgets `35s` text and `47s` image).
  - JavaScript syntax validation (`node --check`) across standalone `worker.js` and all frontend `.js` modules.
  - Preserved all v17.4.1 frontend repairs (viewport-fixed calendar bottom sheet, `Zeitplan` board, client-side EXIF-aware image optimization, single-flight lock, structured German error display, input preservation, and retry action).
- **Live tests performed**:
  - Live HTTP `GET` check against `https://focusmodedemoai.illia-pohukai.workers.dev/health` confirmed the currently deployed production Worker is still serving `v17.4.1-mission-repair` (`gemini-2.5-flash`) awaiting manual dashboard paste/deploy of `worker.js`.
- **Live tests not performed & exact reasons**:
  - Live Gemini Developer API (`generativelanguage.googleapis.com`) calls were not executed from this container because no real `GEMINI_API_KEY` secret is provisioned in the local environment (`GEMINI_API_KEY="MY_GEMINI_API_KEY"` placeholder only).
  - Live end-to-end requests against the updated `v17.4.2` Cloudflare Worker were not executed because `worker.js` is deployed manually via Cloudflare Dashboard -> Workers & Pages -> `focusmodedemoai` -> Edit code -> Deploy.

---

## 2. Mocked Worker Tests (`node:test`)
All 24 required scenarios passed:
1. `GET /health` returns `{ ok: true, service: "focus-mode-ai", model: "gemini-3.8-flash", version: "v17.4.2" }` without exposing secrets.
2. `OPTIONS` preflight returns `204` with CORS headers.
3. Disallowed `Origin` returns `403` `INVALID_REQUEST` without calling Gemini.
4. Missing `GEMINI_API_KEY` returns `500` `INTERNAL_ERROR` without calling Gemini.
5. Legacy text request without `schedule` calls `gemini-3.8-flash:generateContent` once and does not invent schedule/deadline fields.
6. Text request with `schedule` parses `YYYY-MM-DD` without UTC day shift and enforces preparation window constraints.
7. Text + image sends a single combined multimodal request (`parts: [{ text }, { inlineData }]`).
8. Text + image + `schedule` combines all constraints in one multimodal Gemini request.
9. Image without text is accepted and instructs Gemini to derive topics/tasks from readable image material.
10. Unsupported file type (`application/pdf`) is rejected with `415` `UNSUPPORTED_FILE_TYPE`.
11. Oversized image (`> 4 MB`) is rejected with `413` `FILE_TOO_LARGE`.
12. Invalid `schedule` JSON is rejected with `400` `INVALID_REQUEST`.
13. Gemini success on first attempt returns `200` `{ ok: true, plan, requestId }`.
14. Gemini `429` followed by `200` retries once and succeeds (2 attempts total).
15. Gemini `503` followed by `200` retries once and succeeds (2 attempts total).
16. Gemini `524` followed by `524` stops after 2 attempts and returns `504` `AI_TIMEOUT`.
17. Per-attempt `AbortController` timeout aborts stalled upstream fetch and returns `504` `AI_TIMEOUT` after at most 2 attempts.
18. Invalid Gemini JSON returns `502` `INVALID_AI_RESPONSE` without retrying (1 attempt).
19. Empty Gemini response returns `502` `INVALID_AI_RESPONSE` without retrying (1 attempt).
20. Mission with `< 2` sessions is rejected with `502` `INVALID_AI_RESPONSE`.
21. Mission with `> 6` sessions is rejected with `502` `INVALID_AI_RESPONSE`.
22. Session `durationMinutes` outside `5–60` minutes is rejected with `502` `INVALID_AI_RESPONSE`.
23. Legacy `POST /adapt-mission` request succeeds with `gemini-3.8-flash`.
24. A single Worker request never performs more than `MAX_ATTEMPTS = 2` upstream attempts across any status code.
