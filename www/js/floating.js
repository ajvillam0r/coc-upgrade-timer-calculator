var FloatingModule = (function() {
    var FloatingPlugin = null;

    function getPlugin() {
        if (FloatingPlugin) return FloatingPlugin;
        if (!window.Capacitor) return null;
        if (window.Capacitor.registerPlugin) {
            FloatingPlugin = window.Capacitor.registerPlugin('FloatingPlugin');
            return FloatingPlugin;
        }
        if (window.Capacitor.Plugins && window.Capacitor.Plugins.FloatingPlugin) {
            FloatingPlugin = window.Capacitor.Plugins.FloatingPlugin;
            return FloatingPlugin;
        }
        return null;
    }

    function isAvailable() {
        // Android only. The floating overlay WebView itself must not offer to
        // start another overlay, and web / iOS have no such bridge at all.
        if (window.UTCPlatform) {
            return window.UTCPlatform.isAndroid && !window.UTCPlatform.isFloatingOverlay;
        }
        if (!window.Capacitor) return false;
        if (typeof window.Capacitor.isNativePlatform === 'function') {
            return window.Capacitor.isNativePlatform();
        }
        return Boolean(getPlugin());
    }

    function showToast(message, duration) {
        duration = duration || 3000;
        var toast = document.createElement('div');
        toast.className = 'toast';
        toast.textContent = message;
        document.body.appendChild(toast);
        setTimeout(function() { toast.classList.add('toast--show'); }, 10);
        setTimeout(function() {
            toast.classList.remove('toast--show');
            setTimeout(function() { document.body.removeChild(toast); }, 300);
        }, duration);
    }

    function showPermissionModal(onGrant, onCancel) {
        var modal = document.createElement('div');
        modal.className = 'modal';
        modal.innerHTML =
            '<div class="modal__content">' +
            '<div class="modal__icon">📱</div>' +
            '<h2 class="modal__title">Overlay Permission Required</h2>' +
            '<p class="modal__text">To use Float Mode, the calculator needs permission to display over other apps.</p>' +
            '<p class="modal__text modal__text--muted">This allows the calculator bubble to float on top of your game and other apps.</p>' +
            '<div class="modal__actions">' +
            '<button class="btn btn--secondary modal__btn" data-action="cancel">Cancel</button>' +
            '<button class="btn btn--primary modal__btn" data-action="grant">Grant Permission</button>' +
            '</div>' +
            '</div>';

        document.body.appendChild(modal);
        setTimeout(function() { modal.classList.add('modal--show'); }, 10);

        modal.addEventListener('click', function(e) {
            if (e.target.dataset.action === 'cancel' || e.target === modal) {
                modal.classList.remove('modal--show');
                setTimeout(function() { document.body.removeChild(modal); }, 300);
                if (onCancel) onCancel();
            } else if (e.target.dataset.action === 'grant') {
                modal.classList.remove('modal--show');
                setTimeout(function() { document.body.removeChild(modal); }, 300);
                if (onGrant) onGrant();
            }
        });
    }

    function startFloating() {
        var plugin = getPlugin();
        if (!plugin) {
            showToast('Floating mode not available on this device');
            return;
        }

        plugin.checkOverlayPermission().then(function(result) {
            if (result.granted) {
                plugin.startFloatingMode().then(function(result) {
                    if (result && result.alreadyRunning) {
                        showToast('Float Mode is already active');
                    } else {
                        showToast('Float Mode activated');
                    }
                }).catch(function(error) {
                    showToast('Failed to start Float Mode: ' + (error.message || 'Unknown error'));
                });
            } else {
                showPermissionModal(
                    function() {
                        plugin.requestOverlayPermission();
                        setTimeout(function() {
                            plugin.checkOverlayPermission().then(function(result) {
                                if (result.granted) {
                                    startFloating();
                                }
                            });
                        }, 1000);
                    },
                    null
                );
            }
        }).catch(function(error) {
            showToast('Failed to check permission: ' + (error.message || 'Unknown error'));
        });
    }

    function stopFloating() {
        var plugin = getPlugin();
        if (!plugin) {
            return;
        }

        plugin.stopFloatingMode().then(function() {
            showToast('Float Mode deactivated');
        }).catch(function(error) {
            showToast('Failed to stop Float Mode: ' + (error.message || 'Unknown error'));
        });
    }

    function createFloatingButton() {
        if (!isAvailable()) return null;

        var button = document.createElement('button');
        button.className = 'btn navbar__float-btn';
        button.type = 'button';
        button.innerHTML = '<span class="btn__icon">🎮</span> Float Mode';

        button.addEventListener('click', function() {
            startFloating();
        });

        return button;
    }

    function init() {
        if (!isAvailable()) return;

        function injectButton() {
            var navbar = document.querySelector('.navbar');
            if (navbar) {
                var burger = document.querySelector('.navbar__burger');
                if (burger) {
                    var button = createFloatingButton();
                    if (button) {
                        navbar.insertBefore(button, burger);
                    }
                }
            }
        }

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', injectButton);
        } else {
            injectButton();
        }
    }

    init();

    return {
        isAvailable: isAvailable,
        startFloating: startFloating,
        stopFloating: stopFloating,
        createFloatingButton: createFloatingButton
    };
})();

