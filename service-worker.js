const CACHE = "focus-mode-v17-2-offline-ready";
const CACHE_PREFIX = "focus-mode";

const CORE = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./apple-touch-icon.png",
  "./icon-192.png",
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
  "./src/features/recovery/recovery-controller.js",
  "./src/features/focus-session/session-runtime.js",
  "./src/features/focus-session/session-finalization.js",
  "./src/features/recovery/recovery-router.js",
  "./src/features/recovery/finalized-session-guard.js",
  "./src/features/optional-adaptation/optional-adaptation.js",
  "./src/features/missions/mission-management.js",
  "./src/features/missions/multi-mission-ui.js",
  "./src/core/navigation.js",
  "./src/core/bootstrap.js"
];

const EXTERNAL_CDN_URLS = [
  "https://cdn.tailwindcss.com",
  "https://unpkg.com/lucide@latest",
  "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap"
];

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
  if (e.request.method !== "GET") return;

  const url = new URL(e.request.url);
  if (
    url.hostname.includes("workers.dev") ||
    url.pathname.endsWith("/create-mission") ||
    url.pathname.endsWith("/adapt-mission")
  ) {
    return;
  }

  if (e.request.mode === "navigate") {
    e.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        try {
          const networkResponse = await fetchWithTimeout(e.request, 3000);
          if (networkResponse && networkResponse.ok && networkResponse.status === 200) {
            const contentType = networkResponse.headers.get("content-type") || "";
            if (contentType.includes("text/html")) {
              await cache.put("./index.html", networkResponse.clone());
            }
            return networkResponse;
          }
        } catch (_) {
          // Offline or network timeout: fall back to cached app shell
        }
        return (
          (await cache.match("./index.html", { ignoreSearch: true })) ||
          (await cache.match("./", { ignoreSearch: true })) ||
          (await caches.match("./index.html", { ignoreSearch: true }))
        );
      })()
    );
    return;
  }

  if (url.origin === self.location.origin) {
    e.respondWith(
      (async () => {
        const cache = await caches.open(CACHE);
        const cached = await cache.match(e.request, { ignoreSearch: true });
        if (cached) return cached;

        try {
          const networkResponse = await fetch(e.request);
          if (networkResponse && networkResponse.ok && networkResponse.status === 200) {
            const contentType = networkResponse.headers.get("content-type") || "";
            const isAsset = /\.(js|css|png|webmanifest)$/i.test(url.pathname);
            if (!isAsset || !contentType.includes("text/html")) {
              await cache.put(e.request, networkResponse.clone());
            }
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

  // External GET resources (Tailwind CDN, Lucide CDN, Google Fonts)
  e.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const cached =
        (await cache.match(e.request, { ignoreVary: true })) ||
        (await cache.match(e.request.url, { ignoreVary: true }));
      if (cached) return cached;

      try {
        const networkResponse = await fetch(e.request);
        if (networkResponse && (networkResponse.ok || networkResponse.type === "opaque")) {
          await cache.put(e.request.url, networkResponse.clone());
        }
        return networkResponse;
      } catch (_) {
        const fallbackCached =
          (await cache.match(e.request, { ignoreVary: true })) ||
          (await cache.match(e.request.url, { ignoreVary: true }));
        if (fallbackCached) return fallbackCached;

        if (e.request.destination === "script" || /\.js($|\?)/i.test(url.href) || url.hostname.includes("tailwindcss") || url.hostname.includes("unpkg")) {
          return new Response("/* offline script fallback */", {
            status: 200,
            headers: { "Content-Type": "application/javascript; charset=utf-8" }
          });
        }
        if (e.request.destination === "style" || url.hostname.includes("fonts.googleapis.com")) {
          return new Response("/* offline style fallback */", {
            status: 200,
            headers: { "Content-Type": "text/css; charset=utf-8" }
          });
        }
        return new Response("", { status: 200 });
      }
    })()
  );
});