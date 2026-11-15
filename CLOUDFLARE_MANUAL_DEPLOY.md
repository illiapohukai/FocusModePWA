# Cloudflare Manual Deployment (`focusmodedemoai`)

1. Open Cloudflare Dashboard.
2. Open Workers & Pages.
3. Select `focusmodedemoai`.
4. Open **Edit code**.
5. Replace `worker.js` with the standalone `worker.js` file from this release.
6. Click **Deploy**.
7. Confirm Variables and Secrets:
   - `GEMINI_API_KEY`
   - `ALLOWED_ORIGIN`
8. Open `/health` (`https://focusmodedemoai.illia-pohukai.workers.dev/health`) and verify `"model": "gemini-3.8-flash"` and `"version": "v17.4.2"`.
9. Test text Mission.
10. Test image Mission.
11. Test schedule Mission.
