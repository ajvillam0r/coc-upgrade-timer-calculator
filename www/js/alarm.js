var AlarmModule = (function() {
    // Native bridge injected by FloatingCalculatorService's floating WebView.
    function nativeBridge() {
        return (typeof window !== 'undefined' && window.AndroidFloatingBridge)
            ? window.AndroidFloatingBridge
            : null;
    }

    // The native overlay service owns post-alarm recovery on its own (a single, non
    // destructive layout refresh). The page deliberately does not ask for it: repeated
    // re-attach requests from JS used to make the floating window blink.

    function isAvailable() {
        // The overlay WebView exposes the Java bridge directly.
        if (nativeBridge()) return true;
        // Android is the only platform with the alarm plugin.
        if (window.UTCPlatform) return window.UTCPlatform.isAndroid;
        return typeof Capacitor !== 'undefined' &&
               Capacitor.Plugins &&
               Capacitor.Plugins.AlarmPlugin !== undefined;
    }

    function hoursUntil(targetDate) {
        var now = new Date();
        var diffMs = targetDate.getTime() - now.getTime();
        return diffMs / (1000 * 60 * 60);
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

    function showLongAlarmWarning(targetDate, onProceed, onCancel) {
        var hours = Math.round(hoursUntil(targetDate));
        var days = Math.floor(hours / 24);
        var alarmTime = targetDate.getHours() + ':' +
                        String(targetDate.getMinutes()).padStart(2, '0');

        var modal = document.createElement('div');
        modal.className = 'modal';
        modal.innerHTML =
            '<div class="modal__content">' +
            '<div class="modal__icon">⚠️</div>' +
            '<h2 class="modal__title">Alarm Limitation</h2>' +
            '<p class="modal__text">This upgrade completes in ' + days + ' days, but Android alarms can only be set within 24 hours.</p>' +
            '<p class="modal__text">The alarm will ring <strong>tomorrow at ' + alarmTime + '</strong>, not when the upgrade actually completes.</p>' +
            '<p class="modal__text modal__text--muted">Consider setting a calendar reminder instead for long upgrades.</p>' +
            '<div class="modal__actions">' +
            '<button class="btn btn--secondary modal__btn" data-action="cancel">Cancel</button>' +
            '<button class="btn btn--primary modal__btn" data-action="proceed">Set Alarm Anyway</button>' +
            '</div>' +
            '</div>';

        document.body.appendChild(modal);
        setTimeout(function() { modal.classList.add('modal--show'); }, 10);

        modal.addEventListener('click', function(e) {
            if (e.target.dataset.action === 'cancel' || e.target === modal) {
                modal.classList.remove('modal--show');
                setTimeout(function() { document.body.removeChild(modal); }, 300);
                if (onCancel) onCancel();
            } else if (e.target.dataset.action === 'proceed') {
                modal.classList.remove('modal--show');
                setTimeout(function() { document.body.removeChild(modal); }, 300);
                if (onProceed) onProceed();
            }
        });
    }

    function setAlarm(targetDate, context) {
        var now = new Date();
        if (targetDate <= now) {
            showToast('Invalid time. Cannot set alarm in the past.');
            return;
        }

        var hour = targetDate.getHours();
        var minute = targetDate.getMinutes();

        var messageMap = {
            'simple': 'Upgrade Complete',
            'builder': 'Builder Upgrade Complete',
            'research': 'Lab Research Complete',
            'pet': 'Pet Upgrade Complete'
        };
        var message = messageMap[context] || 'Upgrade Complete';

        var proceedWithAlarm = function() {
            var bridge = nativeBridge();
            if (bridge) {
                try {
                    var raw = bridge.setAlarm(hour, minute, message);
                    var result = raw ? JSON.parse(raw) : null;
                    if (result && result.success) {
                        showToast('Alarm set for ' + hour + ':' + String(minute).padStart(2, '0'));
                    } else {
                        var bridgeError = (result && result.error) || '';
                        if (bridgeError.indexOf('No Clock app') !== -1) {
                            showToast('No Clock app found. Please install a clock app.');
                        } else {
                            showToast('Failed to set alarm. Please try again.');
                        }
                    }
                } catch (e) {
                    showToast('Failed to set alarm. Please try again.');
                }
                return;
            }

            Capacitor.Plugins.AlarmPlugin.setAlarm({
                hour: hour,
                minute: minute,
                message: message
            }).then(function(result) {
                showToast('Alarm set for ' + hour + ':' + String(minute).padStart(2, '0'));
            }).catch(function(error) {
                var errorMsg = error.message || 'Failed to set alarm';
                if (errorMsg.includes('No Clock app')) {
                    showToast('No Clock app found. Please install a clock app.');
                } else if (errorMsg.includes('Permission denied')) {
                    showToast('Permission denied. Unable to set alarm.');
                } else {
                    showToast('Failed to set alarm. Please try again.');
                }
            });
        };

        var hours = hoursUntil(targetDate);
        if (hours > 24) {
            showLongAlarmWarning(targetDate, proceedWithAlarm, null);
        } else {
            proceedWithAlarm();
        }
    }

    function createAlarmButton(targetDate, context) {
        if (!isAvailable()) return null;

        var button = document.createElement('button');
        button.className = 'btn btn--alarm';
        button.type = 'button';
        button.innerHTML = '<span class="btn__icon">⏰</span> Set Alarm for Completion';

        button.addEventListener('click', function() {
            setAlarm(targetDate, context);
        });

        return button;
    }

    return {
        isAvailable: isAvailable,
        createAlarmButton: createAlarmButton,
        setAlarm: setAlarm
    };
})();
