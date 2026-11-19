# Security Policy — Focus Mode+

## Supported versions

Focus Mode+ is a demo-stage, local-first PWA. Only the latest version on the default branch / the deployed
site receives security fixes. Older versions are not supported.

## Reporting a vulnerability

Please report suspected vulnerabilities privately to: **[SECURITY CONTACT TO BE ADDED]**

Include, where possible:

- affected version or commit and the page/flow,
- steps to reproduce or a proof of concept (synthetic data only),
- impact as you understand it.

Please **do not publish** details (issues, social media, blog posts) until a fix is available or we have
agreed on a disclosure date. Do not include real personal data in reports.

We aim to acknowledge reports within **7 days** and to share an initial assessment within **30 days**.
These are targets, not guarantees, for a small project.

## Scope

In scope:

- this repository's web app (HTML/CSS/JS), service worker and manifest,
- the import/export/backup handling,
- the client side of the AI integration,
- the Cloudflare Worker endpoints used by the app (`/create-mission`, `/adapt-mission`) — **only** with
  low-volume, non-destructive testing.

Out of scope:

- denial-of-service or load testing, automated mass scanning, spam, social engineering, physical attacks;
- anything that consumes significant AI budget (mass requests to the Worker);
- third-party services themselves (Cloudflare, Google, GitHub, Tailwind CDN) — report to those vendors;
- **modifying your own local data** (e.g. editing `localStorage` or a backup to change your streak or
  History). Local data is user-controlled by design and is not used for any competitive or paid feature;
- findings that require an already compromised device or browser.

## Good-faith research

If you act in good faith, stay within the scope above, avoid privacy violations and service degradation, and
give us reasonable time to respond, we will not pursue or support action against you for that research.
This statement does not grant permission to access other people's data or to violate any law, and it cannot
bind third parties (such as hosting or AI providers).

## Security documentation

- [`docs/security/security-baseline.md`](docs/security/security-baseline.md) — current controls and limits
- [`docs/security/threat-model.md`](docs/security/threat-model.md)
- [`docs/security/security-audit-v17.17.md`](docs/security/security-audit-v17.17.md)
