# Focus Mode+ — Processor and Transfer Register (draft, v17.17)

Working register for the operator. Every legal/contractual field must be verified against the real accounts,
plans and current provider terms. Nothing below is confirmed unless stated as "observed in code".

| Service | Purpose | Data categories | Transfer / region | Contract document to verify | DPA required? | Retention question | Production decision |
|---|---|---|---|---|---|---|---|
| **Static host: GitHub Pages** (GitHub, Inc.) | Serve app files | IP address, User-Agent, URL, timestamp in access logs | Likely USA **[TO BE CONFIRMED]** | GitHub Terms, GitHub Privacy Statement, GitHub DPA **[TO BE CONFIRMED]** | Likely, for access logs **[TO BE CONFIRMED]** | Log retention by GitHub **[TO BE CONFIRMED]** | Acceptable for demo; for production consider a host with a signed DPA and header control |
| **Cloudflare Workers** (Cloudflare, Inc.) | AI proxy `focusmodedemoai.illia-pohukai.workers.dev` | Goal text, photo, schedule, Mission context and feedback, IP, User-Agent; operational logs if enabled | Global edge network; **[TO BE CONFIRMED]** | Cloudflare Self-Serve Subscription Agreement, Cloudflare DPA (incl. SCCs) **[TO BE CONFIRMED]** | Yes **[TO BE CONFIRMED]** | Workers logs/observability retention and whether request bodies are logged **[TO BE CONFIRMED]** | Required: confirm logging is metadata-only (see Worker requirements) |
| **Google Gemini API** (Google entity **[TO BE CONFIRMED]**) | Generate/adapt plans | Prompt content: goal text, photo, schedule, Mission context, feedback | **[TO BE CONFIRMED]** | Gemini API Additional Terms; whether paid tier and "data not used for training" apply; Google Cloud DPA **[TO BE CONFIRMED]** | Yes **[TO BE CONFIRMED]** | Prompt/response retention (abuse monitoring) under the used tier **[TO BE CONFIRMED]** | Must confirm tier and data-use terms before public beta; free tiers may allow use for product improvement **[TO BE CONFIRMED]** |
| **Tailwind Play CDN** (`cdn.tailwindcss.com`, Tailwind Labs **[TO BE CONFIRMED]**) | Runtime CSS generator script | IP address, User-Agent, Origin | **[TO BE CONFIRMED]** | Provider terms/privacy **[TO BE CONFIRMED]** | Unclear **[TO BE CONFIRMED]** | **[TO BE CONFIRMED]** | Replace with self-hosted pre-built CSS before public beta (F-06) |
| **GitHub** (repository) | Source hosting, development | Source code only; no user data | n/a | n/a | No user data | n/a | No user data must ever be committed |
| Google Fonts | — | — | — | — | — | — | **Removed in v17.17** (self-hosted) |
| unpkg.com | — | — | — | — | — | — | **Removed in v17.17** (vendored) |
| Analytics / tracking | none found in code | — | — | — | — | — | None |
| Error reporting service | none found in code | — | — | — | — | — | None |

## Required decisions before public beta

1. Controller identity and contact (see `datenschutzerklaerung-draft.md`).
2. Confirm Gemini tier, data-use and retention terms; record the Google contracting entity.
3. Confirm Cloudflare DPA acceptance and Worker logging configuration.
4. Decide on hosting with header support and a DPA, or document GitHub Pages as accepted.
5. Replace the Tailwind Play CDN.
