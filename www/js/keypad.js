// keypad.js
// Always-visible numeric keypad rendered directly inside each calculator card,
// below the numeric textboxes. The textboxes are readonly + inputmode="none", so
// the Android soft keyboard never opens; every digit is inserted by this keypad
// into whichever input was tapped (the "active target").

var KeypadModule = (function () {
    var inputs = [];
    var activeInput = null;

    var KEYPAD_HTML =
        '<div class="keypad__grid">' +
            '<button type="button" class="keypad__key" data-key="1">1</button>' +
            '<button type="button" class="keypad__key" data-key="2">2</button>' +
            '<button type="button" class="keypad__key" data-key="3">3</button>' +
            '<button type="button" class="keypad__key" data-key="4">4</button>' +
            '<button type="button" class="keypad__key" data-key="5">5</button>' +
            '<button type="button" class="keypad__key" data-key="6">6</button>' +
            '<button type="button" class="keypad__key" data-key="7">7</button>' +
            '<button type="button" class="keypad__key" data-key="8">8</button>' +
            '<button type="button" class="keypad__key" data-key="9">9</button>' +
            '<button type="button" class="keypad__key keypad__key--action" data-key="clear" aria-label="Clear input">C</button>' +
            '<button type="button" class="keypad__key" data-key="0">0</button>' +
            '<button type="button" class="keypad__key keypad__key--action" data-key="back" aria-label="Backspace">⌫</button>' +
        '</div>';

    function isVisible(element) {
        return !!element && element.offsetParent !== null;
    }

    function inputList() {
        return Array.prototype.slice.call(
            document.querySelectorAll('input.form__input[type="number"]')
        );
    }

    function firstVisibleInput(scope) {
        var list = scope
            ? Array.prototype.slice.call(
                scope.querySelectorAll('input.form__input[type="number"]')
            )
            : inputs;

        for (var i = 0; i < list.length; i++) {
            if (isVisible(list[i]) && !list[i].disabled) {
                return list[i];
            }
        }
        return null;
    }

    function setActive(input) {
        if (!input) {
            return;
        }

        activeInput = input;
        inputs.forEach(function (item) {
            item.classList.toggle('form__input--active', item === input);
        });
    }

    function currentInput() {
        if (activeInput &&
            document.contains(activeInput) &&
            isVisible(activeInput) &&
            !activeInput.disabled) {
            return activeInput;
        }

        var fallback = firstVisibleInput();
        if (fallback) {
            setActive(fallback);
        }
        return fallback;
    }

    function dispatchChange(input) {
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    function insertDigit(input, digit) {
        var value = input.value || '';
        if (value === '0') {
            value = '';
        }
        if (value.length >= 6) {
            return;
        }

        input.value = value + digit;
        dispatchChange(input);
    }

    function backspace(input) {
        var value = input.value || '';
        if (!value) {
            return;
        }

        input.value = value.slice(0, -1);
        dispatchChange(input);
    }

    function clearInput(input) {
        if (!input.value) {
            return;
        }

        input.value = '';
        dispatchChange(input);
    }

    // Tapping a key must not move focus away from the active textbox.
    function keepFocus(event) {
        if (event.target && event.target.closest && event.target.closest('button')) {
            event.preventDefault();
        }
    }

    function targetForForm(form) {
        if (activeInput && form.contains(activeInput) && isVisible(activeInput)) {
            return activeInput;
        }

        var fallback = firstVisibleInput(form);
        if (fallback) {
            setActive(fallback);
        }
        return fallback;
    }

    function buildKeypadFor(form) {
        if (!form || form.querySelector('.keypad')) {
            return;
        }

        var keypad = document.createElement('div');
        keypad.className = 'keypad';
        keypad.setAttribute('role', 'group');
        keypad.setAttribute('aria-label', 'Numeric keypad');
        keypad.innerHTML = KEYPAD_HTML;

        // Place the grid directly below the last row of textboxes, before the
        // Calculate button.
        var rows = form.querySelectorAll('.form__row');
        var anchor = rows.length ? rows[rows.length - 1] : null;
        if (anchor) {
            form.insertBefore(keypad, anchor.nextSibling);
        } else {
            form.insertBefore(keypad, form.firstChild);
        }

        keypad.addEventListener('pointerdown', keepFocus);
        keypad.addEventListener('mousedown', keepFocus);

        keypad.addEventListener('click', function (event) {
            var button = event.target.closest
                ? event.target.closest('button[data-key]')
                : null;
            if (!button) {
                return;
            }

            var target = targetForForm(form);
            if (!target) {
                return;
            }

            var key = button.getAttribute('data-key');
            if (key === 'back') {
                backspace(target);
            } else if (key === 'clear') {
                clearInput(target);
            } else if (key !== null) {
                insertDigit(target, key);
            }
        });
    }

    function buildKeypads() {
        var forms = document.querySelectorAll('form');
        Array.prototype.forEach.call(forms, function (form) {
            if (form.querySelector('input.form__input[type="number"]')) {
                buildKeypadFor(form);
            }
        });
    }

    function selectInputFromEvent(event) {
        var target = event.target;
        if (target && target.matches && target.matches('input.form__input[type="number"]')) {
            setActive(target);
        }
    }

    function bindInputSelection() {
        // Capture phase so selection works even though the inputs are readonly.
        document.addEventListener('pointerdown', selectInputFromEvent, true);
        document.addEventListener('focusin', selectInputFromEvent, true);
        document.addEventListener('click', selectInputFromEvent, true);
    }

    function bindTabs() {
        // The pages toggle .form--hidden on tab clicks. After that runs, move the
        // active target to the first visible input of the newly shown form.
        document.addEventListener('click', function (event) {
            var tab = event.target.closest
                ? event.target.closest('.tabs__btn')
                : null;
            if (!tab) {
                return;
            }

            setTimeout(function () {
                var next = firstVisibleInput();
                if (next) {
                    setActive(next);
                }
            }, 0);
        });
    }

    function init() {
        inputs = inputList();
        if (!inputs.length) {
            return;
        }

        inputs.forEach(function (input) {
            input.setAttribute('inputmode', 'none');
            input.setAttribute('readonly', 'readonly');
            input.setAttribute('autocomplete', 'off');
            input.setAttribute('autocorrect', 'off');
            input.setAttribute('spellcheck', 'false');
        });

        buildKeypads();
        bindInputSelection();
        bindTabs();

        var initial = firstVisibleInput();
        if (initial) {
            setActive(initial);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    return {
        setActive: setActive,
        currentInput: currentInput
    };
})();
