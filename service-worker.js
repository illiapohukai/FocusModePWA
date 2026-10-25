const CACHE = 'focus-mode-plus-v15-modular-baseline';
const ICON_FILES = [
  'icon-192.png',
  'icon-512.png',
  'icon-maskable-512.png',
  'apple-touch-icon.png'
];
const CORE = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icon-192.png',
  './icon-512.png',
  './icon-maskable-512.png',
  './apple-touch-icon.png',
  './styles/app.css',
  './styles/timer.css',
  './styles/recovery.css',
  './styles/quick-focus.css',
  './styles/missions.css',
  './src/core/architecture-shell.js',
  './src/services/storage-service.js',
  './src/repositories/session-repository.js',
  './src/repositories/mission-repository.js',
  './src/services/ai-service.js',
  './src/core/navigation.js',
  './src/features/quick-focus/quick-focus.js',
  './src/features/history/session-history.js',
  './src/features/focus-session/session-model.js',
  './src/features/focus-session/session-runtime.js',
  './src/features/focus-session/session-controller.js',
  './src/features/focus-session/session-finalization.js',
  './src/features/recovery/recovery-controller.js',
  './src/features/missions/mission-controller.js',
  './src/features/missions/mission-renderer.js',
  './src/features/missions/mission-management.js',
  './src/features/missions/multi-mission-ui.js',
  './src/features/recovery/recovery-router.js',
  './src/features/recovery/finalized-session-guard.js',
  './src/core/runtime-ownership.js',
  './src/core/bootstrap.js',
  './src/features/optional-adaptation/optional-adaptation.js'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(CACHE)
      .then(async c => {
        await c.addAll(CORE);
        for (const icon of ICON_FILES) {
          const cached = await c.match('./' + icon);
          if (cached) {
            await c.put('./icons/' + icon, cached.clone());
          }
        }
      })
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  if (e.request.mode === 'navigate') {
    e.respondWith(
      fetch(e.request)
        .then(r => {
          if (r && r.ok) {
            caches.open(CACHE).then(c => c.put('./index.html', r.clone()));
          }
          return r;
        })
        .catch(() => caches.match('./index.html'))
    );
    return;
  }
  const url = new URL(e.request.url, self.location ? self.location.href : 'https://localhost/');
  const isSameOrigin = !self.location || url.origin === self.location.origin;
  const isCodeOrStyle = isSameOrigin && /\.(?:js|css|webmanifest)$/i.test(url.pathname);
  if (isCodeOrStyle) {
    e.respondWith(
      fetch(e.request)
        .then(r => {
          if (r && r.ok) {
            caches.open(CACHE).then(c => c.put(e.request, r.clone()));
          }
          return r;
        })
        .catch(() => caches.match(e.request, { ignoreSearch: true }))
    );
    return;
  }
  e.respondWith(
    caches.match(e.request, { ignoreSearch: true })
      .then(r => r || fetch(e.request))
  );
});
