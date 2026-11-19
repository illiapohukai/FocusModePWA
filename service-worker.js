const CACHE = "focus-mode-v17-17-security-privacy";
const CACHE_PREFIX = "focus-mode";

const CORE = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./apple-touch-icon.png",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-maskable-512.png",
  "./styles/app.css",
  "./styles/navigation.css",
  "./styles/missions.css",
  "./styles/timer.css",
  "./styles/recovery.css",
  "./styles/quick-focus.css",
  "./styles/data-management.css",
  "./styles/motion.css",
  "./styles/insights.css",
  "./styles/home-pull.css",
  "./assets/operator/operator-coffee-pull.json",
  "./assets/operator/operator-coffee-pull-sheet.png",
  "./assets/operator/mission-operator.json",
  "./assets/operator/mission-operator-work.png",
  "./assets/operator/mission-operator-success.png",
  "./assets/vendor/lucide/lucide.min.js",
  "./assets/fonts/inter/inter-latin-wght-normal.woff2",
  "./src/core/architecture-shell.js",
  "./src/core/runtime-ownership.js",
  "./src/services/storage-service.js",
  "./src/data/schema-versions.js",
  "./src/data/data-validator.js",
  "./src/data/migration-runner.js",
  "./src/data/data-export.js",
  "./src/data/data-import.js",
  "./src/data/data-reset.js",
  "./src/repositories/mission-repository.js",
  "./src/repositories/session-repository.js",
  "./src/features/data-management/data-management-controller.js",
  "./src/features/missions/mission-controller.js",
  "./src/features/focus-session/session-model.js",
  "./src/features/focus-session/session-controller.js",
  "./src/services/ai-service.js",
  "./src/features/missions/mission-renderer.js",
  "./src/features/quick-focus/quick-focus.js",
  "./src/features/history/session-history.js",
  "./src/features/history/focus-insights.js",
  "./src/features/league/league.js",
  "./src/features/recovery/recovery-controller.js",
  "./src/features/focus-session/session-runtime.js",
  "./src/features/focus-session/session-finalization.js",
  "./src/features/recovery/recovery-router.js",
  "./src/features/recovery/finalized-session-guard.js",
  "./src/features/optional-adaptation/optional-adaptation.js",
  "./src/features/missions/mission-management.js",
  "./src/features/missions/multi-mission-ui.js",
  "./src/features/missions/mission-calendar.js",
  "./src/core/navigation.js",
  "./src/core/bootstrap.js",
  "./src/core/cosmic-core.js",
  "./src/core/motion-controller.js",
  "./src/features/home-pull/home-pull-operator.js",
  "./src/features/missions/mission-operator.js"
];

/* v17.17: Lucide and Inter are self-hosted (precached above). The only remaining third-party runtime
   asset is the Tailwind Play CDN; no other cross-origin response is ever cached. */
const EXTERNAL_CDN_URLS = [
  "https://cdn.tailwindcss.com"
];
const CACHEABLE_CROSS_ORIGIN_HOSTS = new Set(["cdn.tailwindcss.com"]);

/* AI Worker traffic is never intercepted or cached (any method). */
const WORKER_HOST_SUFFIX = ".workers.dev";
const API_PATH_PATTERN = /\/(create-mission|adapt-mission)$/;

/* Same-origin runtime caching is limited to static asset types. Development material
   (docs, tests, Claude skills, Git data, Markdown) is never cached. */
const RUNTIME_CACHEABLE_EXTENSIONS = /\.(?:js|css|png|jpe?g|webp|svg|ico|woff2|json|webmanifest)$/i;
const NEVER_CACHE_PATH_PATTERN = /(?:^|\/)(?:docs|tests|\.claude|\.git|\.github|scratchpad)(?:\/|$)|\.(?:md|map|txt|zip|log)$/i;

function scopePath() {
  try {
    return new URL(self.registration ? self.registration.scope : "./", self.location.href).pathname;
  } catch (_) {
    return "/";
  }
}

function relativePath(url) {
  const base = scopePath();
  return url.pathname.startsWith(base) ? url.pathname.slice(base.length) : url.pathname.replace(/^\//, "");
}

/* Pure routing decision, exposed for tests. */
function classifyRequest(request) {
  if (!request || request.method !== "GET") return "bypass";
  let url;
  try {
    url = new URL(request.url);
  } catch (_) {
    return "bypass";
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") return "bypass";
  if (url.hostname.endsWith(WORKER_HOST_SUFFIX) || API_PATH_PATTERN.test(url.pathname)) return "bypass";
  const sameOrigin = url.origin === self.location.origin;
  if (request.mode === "navigate") return sameOrigin ? "navigate" : "bypass";
  if (sameOrigin) {
    const rel = relativePath(url);
    if (NEVER_CACHE_PATH_PATTERN.test(rel)) return "bypass";
    return RUNTIME_CACHEABLE_EXTENSIONS.test(url.pathname) || rel === "" ? "same-origin-asset" : "same-origin-network";
  }
  return CACHEABLE_CROSS_ORIGIN_HOSTS.has(url.hostname) ? "cdn" : "bypass";
}

function isAppShellPath(url) {
  const rel = relativePath(url);
  return rel === "" || rel === "index.html";
}

function isCacheableSameOriginResponse(response) {
  if (!response || !response.ok || response.status !== 200 || response.type !== "basic") return false;
  const contentType = response.headers.get("content-type") || "";
  return !contentType.includes("text/html");
}

self.FocusModeSWPolicy = Object.freeze({
  CACHE,
  CORE,
  EXTERNAL_CDN_URLS,
  classifyRequest,
  isAppShellPath,
  isCacheableSameOriginResponse
});

function fetchWithTimeout(request, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("sw_fetch_timeout")), timeoutMs);
    fetch(request).then(
      response => {
        clearTimeout(timer);
        resolve(response);
      },
      error => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

async function fetchExternalForCache(url) {
  try {
    const corsRes = await fetchWithTimeout(url, 4000);
    if (corsRes && corsRes.ok) {
      const body = await corsRes.blob();
      return new Response(body, {
        status: 200,
        headers: {
          "Content-Type": corsRes.headers.get("content-type") || "application/octet-stream"
        }
      });
    }
  } catch (_) {
    // Fall back to no-cors for CDNs without CORS headers (e.g. cdn.tailwindcss.com)
  }
  const noCorsReq = new Request(url, { mode: "no-cors" });
  const noCorsRes = await fetchWithTimeout(noCorsReq, 4000);
  if (noCorsRes && (noCorsRes.ok || noCorsRes.type === "opaque")) {
    return noCorsRes;
  }
  return null;
}

async function warmExternalResources(cache) {
  await Promise.allSettled(
    EXTERNAL_CDN_URLS.map(async url => {
      try {
        const res = await fetchExternalForCache(url);
        if (res) {
          await cache.put(url, res);
        }
      } catch (_) {
        // Optional warmup: never fail SW install if an external CDN is unreachable
      }
    })
  );
}

self.addEventListener("install", e => {
  e.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      await cache.addAll([...new Set(CORE)]);
      await warmExternalResources(cache);
      await self.skipWaiting();
    })()
  );
});

self.addEventListener("activate", e => {
  e.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter(k => k.startsWith(CACHE_PREFIX) && k !== CACHE)
          .map(k => caches.delete(k))
      );
      await self.clients.claim();
    })()
  );
});

self.addEventListener("fetch", e => {
  const route = classifyRequest(e.request);
  if (route === "bypass") return;

  const url = new URL(e.request.url);

  if (route === "navigate") {
    e.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        try {
          const networkResponse = await fetchWithTimeout(e.request, 3000);
          if (networkResponse && networkResponse.ok && networkResponse.status === 200 && networkResponse.type === "basic") {
            const contentType = networkResponse.headers.get("content-type") || "";
            // Only the app shell itself refreshes the cached shell; other pages are never cached.
            if (contentType.includes("text/html") && isAppShellPath(url)) {
              await cache.put("./index.html", networkResponse.clone());
            }
            return networkResponse;
          }
          if (networkResponse && !isAppShellPath(url)) return networkResponse;
        } catch (_) {
          // Offline or network timeout: fall back to cached app shell
        }
        return (
          (await cache.match("./index.html", { ignoreSearch: true })) ||
          (await cache.match("./", { ignoreSearch: true })) ||
          (await caches.match("./index.html", { ignoreSearch: true })) ||
          new Response("Offline", { status: 503, statusText: "Offline", headers: { "Content-Type": "text/plain; charset=utf-8" } })
        );
      })()
    );
    return;
  }

  if (route === "same-origin-asset" || route === "same-origin-network") {
    e.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        const cached = await cache.match(e.request, { ignoreSearch: true });
        if (cached) return cached;

        try {
          const networkResponse = await fetch(e.request);
          // Failed, redirected, opaque or HTML-fallback responses are never stored as assets.
          if (route === "same-origin-asset" && isCacheableSameOriginResponse(networkResponse)) {
            await cache.put(e.request, networkResponse.clone());
          }
          return networkResponse;
        } catch (_) {
          return (
            (await cache.match(e.request, { ignoreSearch: true })) ||
            new Response("", { status: 503, statusText: "Offline" })
          );
        }
      })()
    );
    return;
  }

  // route === "cdn": the allowlisted Tailwind Play CDN only.
  e.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const cached =
        (await cache.match(e.request, { ignoreVary: true })) ||
        (await cache.match(e.request.url, { ignoreVary: true }));
      if (cached) return cached;

      try {
        const networkResponse = await fetch(e.request);
        if (networkResponse && ((networkResponse.ok && networkResponse.status === 200) || networkResponse.type === "opaque")) {
          await cache.put(e.request.url, networkResponse.clone());
        }
        return networkResponse;
      } catch (_) {
        if (e.request.destination === "script" || /\.js($|\?)/i.test(url.href) || url.hostname.includes("tailwindcss")) {
          return new Response("/* offline script fallback */", {
            status: 200,
            headers: { "Content-Type": "application/javascript; charset=utf-8" }
          });
        }
        return new Response("", { status: 503, statusText: "Offline" });
      }
    })()
  );
});
