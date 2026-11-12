/* Upgrade Timer Calc - offline-first service worker.
 *
 * Strategy:
 *   install  -> precache every HTML page, stylesheet, script and icon
 *   fetch    -> cache-first, then network; successful responses are written back
 *               to the cache so a second visit picks up new builds
 *   activate -> drop caches from previous versions and take control immediately
 *
 * The app is fully static, so once the precache step succeeds every page keeps
 * working with no network at all.
 */

var CACHE_NAME = 'upgrade-timer-calc-v1';
var CACHE_PREFIX = 'upgrade-timer-calc-';

/* Relative to this file, so the worker also works from a GitHub Pages subpath
 * such as https://<username>.github.io/<repo>/. */
var PRECACHE_URLS = [
    './',
    'index.html',
    'simple-calc.html',
    'builder-potion.html',
    'research-potion.html',
    'pet-potion.html',
    'about.html',
    'manifest.json',
    'robots.txt',
    'sitemap.xml',
    'css/style.css',
    'js/platform.js',
    'js/utils.js',
    'js/calculator.js',
    'js/keypad.js',
    'js/alarm.js',
    'js/floating.js',
    'assets/favicon/favicon.svg',
    'assets/favicon/favicon-16x16.png',
    'assets/favicon/favicon-32x32.png',
    'assets/favicon/apple-touch-icon.png',
    'assets/favicon/android-chrome-192x192.png',
    'assets/favicon/mstile-144x144.png',
    'assets/icons/icon-192.png',
    'assets/icons/icon-512.png',
    'assets/icons/icon-maskable-512.png'
];

self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) {
            // Cache each entry independently: one missing file must not abort the
            // whole install and leave the app without an offline cache.
            return Promise.all(PRECACHE_URLS.map(function (url) {
                return fetch(new Request(url, { cache: 'reload' })).then(function (response) {
                    if (response && response.ok) {
                        return cache.put(url, response);
                    }
                    return null;
                }).catch(function () {
                    return null;
                });
            }));
        }).then(function () {
            return self.skipWaiting();
        })
    );
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(function (names) {
            return Promise.all(names.map(function (name) {
                if (name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME) {
                    return caches.delete(name);
                }
                return null;
            }));
        }).then(function () {
            return self.clients.claim();
        })
    );
});

self.addEventListener('message', function (event) {
    if (event.data && event.data.type === 'SKIP_WAITING') {
        self.skipWaiting();
    }
});

function isCacheable(request) {
    if (request.method !== 'GET') {
        return false;
    }
    if (request.headers.get('range')) {
        return false;
    }
    var url = new URL(request.url);
    if (url.origin !== self.location.origin) {
        return false;
    }
    // Never touch the Capacitor / WebView internal schemes.
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return false;
    }
    return true;
}

self.addEventListener('fetch', function (event) {
    var request = event.request;

    if (!isCacheable(request)) {
        return;
    }

    // Started synchronously so waitUntil() is allowed to extend the worker's
    // lifetime while the refreshed copy is written to the cache.
    var network = fetch(request).then(function (response) {
        if (response && response.ok && response.type === 'basic') {
            var copy = response.clone();
            return caches.open(CACHE_NAME).then(function (cache) {
                return cache.put(request, copy);
            }).then(function () {
                return response;
            }).catch(function () {
                return response;
            });
        }
        return response;
    }).catch(function () {
        return null;
    });

    event.waitUntil(network);

    event.respondWith(
        caches.match(request, { ignoreSearch: true }).then(function (cached) {
            if (cached) {
                return cached;
            }
            return network.then(function (response) {
                if (response) {
                    return response;
                }
                if (request.mode === 'navigate') {
                    return caches.match('index.html').then(function (shell) {
                        return shell || Response.error();
                    });
                }
                return Response.error();
            });
        })
    );
});
