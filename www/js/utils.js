// utils.js

// Parse a numeric input safely. Empty fields, NaN, negative values and decimals are
// normalised so blank inputs always behave as 0.
function parseCount(value) {
    const parsed = parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

// Convert days, hours, minutes to minutes
function toMinutes(days, hours, minutes) {
    return parseCount(days) * 24 * 60 +
        parseCount(hours) * 60 +
        parseCount(minutes);
}

// Convert minutes to {days, hours, minutes}
function fromMinutes(total) {
    let safeTotal = Number(total);
    if (!Number.isFinite(safeTotal) || safeTotal < 0) {
        safeTotal = 0;
    }
    safeTotal = Math.floor(safeTotal);

    const days = Math.floor(safeTotal / (24 * 60));
    const hours = Math.floor((safeTotal % (24 * 60)) / 60);
    const minutes = safeTotal % 60;
    return { days, hours, minutes };
}

// Format date in English: Friday 6 June 2025, 18:12
function formatDateHuman(date) {
    return date.toLocaleString('en-GB', {
        weekday: 'long', day: 'numeric', month: 'long',
        year: 'numeric', hour: '2-digit', minute: '2-digit',
        hour12: false
    });
}

// Format an array of [label, value] pairs into styled HTML
function formatResultList(items) {
    return '<div class="result__list">' + items.map(function (item) {
        var raw = item[1];
        var val = (raw === null || raw === undefined || raw === '' ||
            (typeof raw === 'number' && !Number.isFinite(raw)))
            ? '0'
            : String(raw);
        var label = String(item[0]);
        // stack long labels or values on their own line
        var stack = (val.length > 20 || label.length > 20)
            ? ' result__row--stack' : '';
        return '<div class="result__row' + stack + '"><span class="result__label">' + item[0] +
            '</span><span class="result__value">' + val + '</span></div>';
    }).join('') + '</div>';
}

// --- NAVBAR AUTO-HIDE ON SCROLL --- //
// Slides the top bar away while scrolling down and brings it back on scroll up or at
// the top. Works for the normal document scroller and for the floating window, where
// <html> is the overflow container (scroll events are caught in the capture phase).
(function () {
    function initNavbarAutoHide() {
        var navbar = document.querySelector('.navbar');
        if (!navbar) {
            return;
        }

        var menuToggle = document.getElementById('nav-toggle');
        var lastY = 0;
        var ticking = false;
        var HIDE_AFTER = 48;   // keep it visible near the top of the page
        var DELTA = 6;         // ignore tiny scroll jitter

        function scroller() {
            return document.scrollingElement || document.documentElement;
        }

        function update() {
            ticking = false;

            var y = scroller().scrollTop || window.pageYOffset || 0;
            var delta = y - lastY;

            if (y <= HIDE_AFTER) {
                navbar.classList.remove('navbar--hidden');
            } else if (menuToggle && menuToggle.checked) {
                // Keep the bar (and its open menu) visible while the menu is open.
            } else if (delta > DELTA) {
                navbar.classList.add('navbar--hidden');
            } else if (delta < -DELTA) {
                navbar.classList.remove('navbar--hidden');
            }

            lastY = y;
        }

        function onScroll() {
            if (ticking) {
                return;
            }
            ticking = true;
            window.requestAnimationFrame(update);
        }

        lastY = scroller().scrollTop || 0;
        document.addEventListener('scroll', onScroll, true);
        window.addEventListener('scroll', onScroll, { passive: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initNavbarAutoHide);
    } else {
        initNavbarAutoHide();
    }
})();
