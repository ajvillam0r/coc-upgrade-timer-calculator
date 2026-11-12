// platform.js
// Detects where the app is running and exposes a single source of truth for the
// native-only features. Android is the only platform with the floating overlay
// and the silent alarm bridge, so every other target (web, GitHub Pages, iOS
// "Add to Home Screen") must not render those controls at all.
//
// It also registers the offline service worker, but only on the web build.

var UTCPlatform = (function () {
    var capacitor = window.Capacitor;
    var isNative = !!(capacitor &&
        typeof capacitor.isNativePlatform === 'function' &&
        capacitor.isNativePlatform());

    var name = 'web';
    if (isNative && typeof capacitor.getPlatform === 'function') {
        try {
            name = capacitor.getPlatform() || 'native';
        } catch (error) {
            name = 'native';
        }
    }

    // The floating overlay runs in its own WebView that only exposes the Java
    // bridge, so treat it as Android even without a Capacitor runtime.
    var hasFloatingBridge = typeof window.AndroidFloatingBridge !== 'undefined' && !!window.AndroidFloatingBridge;
    var isAndroid = name === 'android' || hasFloatingBridge;
    var isIOS = name === 'ios';
    var isWeb = !isNative && !hasFloatingBridge;

    var root = document.documentElement;
    var classes = [
        isNative ? 'is-native' : 'is-web',
        isAndroid ? 'is-android' : '',
        isIOS ? 'is-ios' : '',
        (isAndroid || hasFloatingBridge) ? '' : 'no-native-controls'
    ].filter(function (value) {
        return value !== '';
    });
    root.className = (root.className + ' ' + classes.join(' ')).trim();

    function registerServiceWorker() {
        if (!isWeb || !('serviceWorker' in navigator)) {
            return;
        }
        // A file:// preview cannot host a worker; GitHub Pages always can.
        if (window.location.protocol !== 'https:' && window.location.protocol !== 'http:') {
            return;
        }
        navigator.serviceWorker.register('sw.js', { scope: './' }).catch(function () {
            // Offline support is a progressive enhancement - never break the app.
        });
    }

    if (document.readyState === 'complete') {
        registerServiceWorker();
    } else {
        window.addEventListener('load', registerServiceWorker);
    }

    return {
        platform: name,
        isNative: isNative,
        isAndroid: isAndroid,
        isIOS: isIOS,
        isWeb: isWeb,
        isFloatingOverlay: hasFloatingBridge,
        /** True only where the Android overlay + alarm bridges exist. */
        hasNativeControls: isAndroid || hasFloatingBridge
    };
})();
