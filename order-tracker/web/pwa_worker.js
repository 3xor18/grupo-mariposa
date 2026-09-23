'use strict';

const SHELL_CACHE = 'order-tracker-shell-v3';
const CONFIG_PATH = 'config.json';
const DEFAULT_API_BASE = '/api/';
const HTML_CONTENT_TYPE = 'text/html';
const SHELL_FILES = [
  './',
  'index.html',
  'manifest.json',
  'favicon.png',
  'flutter.js',
  'flutter_bootstrap.js',
  'main.dart.js',
];
const SHELL_DIRECTORIES = ['assets/', 'canvaskit/', 'icons/'];
const PRECACHED_FILES = ['./', 'index.html', 'manifest.json', 'favicon.png'];

const scopeUrl = (path) => new URL(path, self.registration.scope).href;
const shellFiles = new Set(SHELL_FILES.map(scopeUrl));
const shellDirectories = SHELL_DIRECTORIES.map(scopeUrl);
let apiBasePromise;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(PRECACHED_FILES))
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
  if (!isCacheCandidate(request)) {
    return;
  }
  event.respondWith(serveShell(request));
});

function isCacheCandidate(request) {
  const url = new URL(request.url);
  return (
    request.method === 'GET' &&
    !request.headers.has('Authorization') &&
    url.origin === self.location.origin &&
    isShellRequest(request, url)
  );
}

function isShellRequest(request, url) {
  const address = url.origin + url.pathname;
  return (
    request.mode === 'navigate' ||
    shellFiles.has(address) ||
    shellDirectories.some((directory) => address.startsWith(directory))
  );
}

async function serveShell(request) {
  const url = new URL(request.url);
  const apiBase = await runtimeApiBase();
  if (url.href.startsWith(apiBase)) {
    return fetch(request);
  }
  const key = request.mode === 'navigate' ? scopeUrl('index.html') : url.origin + url.pathname;
  return networkFirst(request, key);
}

function runtimeApiBase() {
  apiBasePromise ??= fetch(scopeUrl(CONFIG_PATH), { cache: 'no-store' })
    .then((response) => (response.ok ? response.json() : {}))
    .then((config) => new URL(config.apiBaseUrl ?? DEFAULT_API_BASE, self.location.origin).href)
    .catch(() => new URL(DEFAULT_API_BASE, self.location.origin).href);
  return apiBasePromise;
}

function isCacheable(request, response) {
  if (!response.ok) {
    return false;
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  return request.mode !== 'navigate' || contentType.startsWith(HTML_CONTENT_TYPE);
}

async function networkFirst(request, key) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const response = await fetch(request);
    if (isCacheable(request, response)) {
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
