'use strict';

const SHELL_CACHE = 'order-tracker-shell-v1';
const SHELL_FILES = ['./', 'index.html', 'manifest.json', 'favicon.png', 'icons/Icon-192.png'];
const BYPASS_PREFIXES = ['/api/'];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(SHELL_FILES))
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => keys.filter((key) => key !== SHELL_CACHE))
      .then((stale) => Promise.all(stale.map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  const bypass = BYPASS_PREFIXES.some((prefix) => url.pathname.startsWith(prefix));
  if (request.method !== 'GET' || url.origin !== self.location.origin || bypass) {
    return;
  }
  event.respondWith(networkFirst(request, url));
});

async function networkFirst(request, url) {
  const cache = await caches.open(SHELL_CACHE);
  const key = request.mode === 'navigate' ? 'index.html' : url.origin + url.pathname;
  try {
    const response = await fetch(request);
    if (response.ok) {
      await cache.put(key, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await cache.match(key);
    if (cached) {
      return cached;
    }
    throw error;
  }
}
