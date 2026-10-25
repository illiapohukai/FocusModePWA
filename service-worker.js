const CACHE = "focus-mode-plus-v15-modular-baseline";
const CORE = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./styles/app.css",
  "./styles/timer.css",
  "./styles/recovery.css",
  "./styles/quick-focus.css",
  "./styles/missions.css",
  "./src/core/architecture-shell.js",
  "./src/services/storage-service.js",
  "./src/core/navigation.js",
  "./src/features/quick-focus/quick-focus.js",
  "./src/services/ai-service.js",
  "./src/features/missions/mission-renderer.js",
  "./src/repositories/session-repository.js",
  "./src/features/history/session-history.js",
  "./src/features/focus-session/session-model.js",
  "./src/features/focus-session/session-runtime.js",
  "./src/features/focus-session/session-finalization.js",
  "./src/features/recovery/recovery-controller.js",
  "./src/repositories/mission-repository.js",
  "./src/features/missions/mission-management.js",
  "./src/features/missions/multi-mission-ui.js",
  "./src/features/recovery/recovery-router.js",
  "./src/features/recovery/finalized-session-guard.js",
  "./src/core/runtime-ownership.js",
  "./src/features/focus-session/session-controller.js",
  "./src/features/missions/mission-controller.js",
  "./src/core/bootstrap.js",
  "./src/features/optional-adaptation/optional-adaptation.js"
];

self.addEventListener("install", e =>
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(CORE))
      .then(() => self.skipWaiting())
  )
);

self.addEventListener("activate", e =>
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  )
);

self.addEventListener("fetch", e => {
  if (e.request.method !== "GET") return;
  if (e.request.mode === "navigate") {
    e.respondWith(
      fetch(e.request)
        .then(r => {
          caches.open(CACHE).then(c => c.put("./index.html", r.clone()));
          return r;
        })
        .catch(() => caches.match("./index.html"))
    );
    return;
  }
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true }).then(r => r || fetch(e.request))
  );
});
