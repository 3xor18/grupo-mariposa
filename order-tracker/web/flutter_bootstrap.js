{{flutter_js}}
{{flutter_build_config}}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('pwa_worker.js')
      .catch((error) => console.warn('PWA service worker registration failed', error));
  });
}

_flutter.loader.load();
