# WORKER_REQUIRED.md — Missing Cloudflare Worker Source

## Status
**Backend Worker deployment and repair cannot be completed from this archive alone.**
The provided ZIP archive contains only the client-side PWA files (`index.html`, `styles/*`, `src/*`, `service-worker.js`, `manifest.webmanifest`, icons, and `docs/`). It does **not** include the Cloudflare Worker repository or deployment configuration serving:
- `https://focusmodedemoai.illia-pohukai.workers.dev/create-mission`
- `https://focusmodedemoai.illia-pohukai.workers.dev/adapt-mission`

In accordance with project rules, no synthetic Worker backend, guessed secrets, or client-side API keys were invented or embedded. All frontend, calendar, image-upload, timeout, error-handling, and scheduling payload repairs have been completed in this archive (`v17.4.1 Mission Generation Repair`).

---

## Exact Missing Files Required to Repair & Deploy Backend

To complete and deploy the backend repair, provide the Cloudflare Worker source repository containing:
1. `wrangler.toml` (or `wrangler.jsonc`) with Worker name, route/domain bindings, compatibility date, and environment/secret binding declarations (must preserve existing `GEMINI_API_KEY` secret binding).
2. Worker entrypoint & modules (e.g., `src/index.ts` or `src/worker.js`, plus any Gemini prompt/schema/retry helper modules).
3. Worker `package.json` / `tsconfig.json` (if TypeScript/Wrangler build tooling is used).

---

## Proven Log Evidence & Required Worker Repairs

Cloudflare logs for `POST /create-mission` (`multipart/form-data`) showed:
- Total Worker execution waited `~119854 ms`, outcome `canceled`, upstream status `524`, `attempts: 4`, response body `{}`.

When the Worker source is available, apply these exact repairs in place (keeping secret bindings and `/create-mission` & `/adapt-mission` endpoints intact):
1. **Verify & Pin Valid Gemini Model + Endpoint**: Check the configured Gemini model ID and `generativelanguage.googleapis.com` URL; remove deprecated/slow or overly heavy thinking configurations that cause ~30s–120s stalls.
2. **Bounded Timeout & Max 1 Transient Retry**: Replace the 4-attempt retry loop with **1 primary attempt + at most 1 retry** only on transient statuses (`408`, `429`, `500`, `502`, `503`, `504`, `524`). Never retry `400`, `401`, `403`, `404`, validation failures, or oversized/unsupported files. Keep each upstream fetch bounded (`<= 22s–26s` text, `<= 30s` image) so total Worker wall time stays well below the frontend abort timeouts (`38s` text / `52s` image) and Cloudflare limits.
3. **Structured JSON Errors (No Empty `{}`)**: Always return `{ ok: false, error: { code, message, retryable } }` with accurate HTTP status (`400`, `413`, `415`, `429`, `502`, `503`, `504`) and CORS headers. The v17.4.1 frontend (`src/services/ai-service.js`) already parses both `{ error: { code, message, retryable } }` and legacy string errors cleanly.
4. **Reliable Multipart Image Processing**: Read `formData.get("image")`, validate MIME (`image/jpeg`, `image/png`, `image/webp`), enforce max processed size, convert cleanly to base64 `inlineData` without overflowing Worker CPU/memory, and avoid redundant prompt bloat.
5. **Integrate Optional `schedule` JSON Field**: Parse optional `formData.get("schedule")` JSON (`currentLocalDate`, `deadlineDate`, `preparationStartDate`, `preparationDays`, `startMode`, `timezone`, optional `preferredWeekdays`, optional `preferredSessionMinutes`). Instruct Gemini to fit sessions between `preparationStartDate` and `deadlineDate` (`scheduledDate <= deadlineDate`), respect `preferredWeekdays` / `preferredSessionMinutes` when provided, and populate `plan.scheduleWarning` if the timeframe is tight.
