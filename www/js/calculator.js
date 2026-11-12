// calculator.js

// --- SIMPLE CALC --- //
document.addEventListener('DOMContentLoaded', function () {
    // Simple calc page
    const simpleForm = document.getElementById('simpleCalcForm');
    if (simpleForm) {
        simpleForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const days = parseCount(simpleForm.days.value);
            const hours = parseCount(simpleForm.hours.value);
            const minutes = parseCount(simpleForm.minutes.value);
            const totalMinutes = toMinutes(days, hours, minutes);

            const now = new Date();
            const endDate = new Date(now.getTime() + totalMinutes * 60000);

            const result = document.getElementById('simpleCalcResult');
            result.innerHTML = formatResultList([
                ['Entered duration', `${days}d ${hours}h ${minutes}m`],
                ['End date', formatDateHuman(endDate)]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'simple');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- BUILDER POTION --- //
    const builderPotionForm = document.getElementById('builderPotionForm');
    if (builderPotionForm) {
        builderPotionForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const days = parseCount(builderPotionForm.days.value);
            const hours = parseCount(builderPotionForm.hours.value);
            const minutes = parseCount(builderPotionForm.minutes.value);
            const potions = parseCount(builderPotionForm.potions.value);
            const totalNormalMinutes = toMinutes(days, hours, minutes);
            const coveredByPotions = potions * 600; // 1 potion = 600 normal min

            let realTimeMinutes, normalTimeLeft;
            if (coveredByPotions >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 10);
                normalTimeLeft = 0;
            } else {
                realTimeMinutes = potions * 60;
                normalTimeLeft = totalNormalMinutes - coveredByPotions;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderPotion = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('builderPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade duration', `${days}d ${hours}h ${minutes}m`],
                ['Potions used', potions],
                ['Time under potion', `${realUnderPotion.hours}h ${realUnderPotion.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'builder');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- BUILDER BOOST --- //
    const builderBoostForm = document.getElementById('builderBoostForm');
    if (builderBoostForm) {
        builderBoostForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const boostHours = parseCount(builderBoostForm.boost_hours.value);
            const boostMinutes = parseCount(builderBoostForm.boost_minutes.value);
            const days = parseCount(builderBoostForm.days.value);
            const hours = parseCount(builderBoostForm.hours.value);
            const minutes = parseCount(builderBoostForm.minutes.value);

            const boostRealMinutes = toMinutes(0, boostHours, boostMinutes);
            const coveredByBoost = boostRealMinutes * 10;
            const totalNormalMinutes = toMinutes(days, hours, minutes);

            let realTimeMinutes, normalTimeLeft, finishedWithBoost = false;
            if (coveredByBoost >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 10);
                normalTimeLeft = 0;
                finishedWithBoost = true;
            } else {
                realTimeMinutes = boostRealMinutes;
                normalTimeLeft = totalNormalMinutes - coveredByBoost;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderBoost = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('builderPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade left', `${days}d ${hours}h ${minutes}m`],
                ['Boost left', `${boostHours}h ${boostMinutes}m`],
                ['Covered by boost', `${coveredByBoost}m`],
                [finishedWithBoost ? 'Finished entirely under boost!' : 'Time under boost', `${realUnderBoost.hours}h ${realUnderBoost.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'builder');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- RESEARCH POTION --- //
    const researchPotionForm = document.getElementById('researchPotionForm');
    if (researchPotionForm) {
        researchPotionForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const days = parseCount(researchPotionForm.days.value);
            const hours = parseCount(researchPotionForm.hours.value);
            const minutes = parseCount(researchPotionForm.minutes.value);
            const potions = parseCount(researchPotionForm.potions.value);
            const totalNormalMinutes = toMinutes(days, hours, minutes);
            const coveredByPotions = potions * 1440; // 1 potion = 1h x24 = 1440 normal min

            let realTimeMinutes, normalTimeLeft;
            if (coveredByPotions >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 24);
                normalTimeLeft = 0;
            } else {
                realTimeMinutes = potions * 60;
                normalTimeLeft = totalNormalMinutes - coveredByPotions;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderPotion = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('researchPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade duration', `${days}d ${hours}h ${minutes}m`],
                ['Potions used', potions],
                ['Time under potion', `${realUnderPotion.hours}h ${realUnderPotion.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'research');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- RESEARCH BOOST --- //
    const researchBoostForm = document.getElementById('researchBoostForm');
    if (researchBoostForm) {
        researchBoostForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const boostHours = parseCount(researchBoostForm.boost_hours.value);
            const boostMinutes = parseCount(researchBoostForm.boost_minutes.value);
            const days = parseCount(researchBoostForm.days.value);
            const hours = parseCount(researchBoostForm.hours.value);
            const minutes = parseCount(researchBoostForm.minutes.value);

            const boostRealMinutes = toMinutes(0, boostHours, boostMinutes);
            const coveredByBoost = boostRealMinutes * 24;
            const totalNormalMinutes = toMinutes(days, hours, minutes);

            let realTimeMinutes, normalTimeLeft, finishedWithBoost = false;
            if (coveredByBoost >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 24);
                normalTimeLeft = 0;
                finishedWithBoost = true;
            } else {
                realTimeMinutes = boostRealMinutes;
                normalTimeLeft = totalNormalMinutes - coveredByBoost;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderBoost = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('researchPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade left', `${days}d ${hours}h ${minutes}m`],
                ['Boost left', `${boostHours}h ${boostMinutes}m`],
                ['Covered by boost', `${coveredByBoost}m`],
                [finishedWithBoost ? 'Finished entirely under boost!' : 'Time under boost', `${realUnderBoost.hours}h ${realUnderBoost.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'research');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- PET POTION --- //
    const petPotionForm = document.getElementById('petPotionForm');
    if (petPotionForm) {
        petPotionForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const days = parseCount(petPotionForm.days.value);
            const hours = parseCount(petPotionForm.hours.value);
            const minutes = parseCount(petPotionForm.minutes.value);
            const potions = parseCount(petPotionForm.potions.value);
            const totalNormalMinutes = toMinutes(days, hours, minutes);
            const coveredByPotions = potions * 1440; // 1 potion = 1h x24 = 1440 normal min

            let realTimeMinutes, normalTimeLeft;
            if (coveredByPotions >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 24);
                normalTimeLeft = 0;
            } else {
                realTimeMinutes = potions * 60;
                normalTimeLeft = totalNormalMinutes - coveredByPotions;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderPotion = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('petPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade duration', `${days}d ${hours}h ${minutes}m`],
                ['Potions used', potions],
                ['Time under potion', `${realUnderPotion.hours}h ${realUnderPotion.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'pet');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- PET BOOST --- //
    const petBoostForm = document.getElementById('petBoostForm');
    if (petBoostForm) {
        petBoostForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const boostHours = parseCount(petBoostForm.boost_hours.value);
            const boostMinutes = parseCount(petBoostForm.boost_minutes.value);
            const days = parseCount(petBoostForm.days.value);
            const hours = parseCount(petBoostForm.hours.value);
            const minutes = parseCount(petBoostForm.minutes.value);

            const boostRealMinutes = toMinutes(0, boostHours, boostMinutes);
            const coveredByBoost = boostRealMinutes * 24;
            const totalNormalMinutes = toMinutes(days, hours, minutes);

            let realTimeMinutes, normalTimeLeft, finishedWithBoost = false;
            if (coveredByBoost >= totalNormalMinutes) {
                realTimeMinutes = Math.ceil(totalNormalMinutes / 24);
                normalTimeLeft = 0;
                finishedWithBoost = true;
            } else {
                realTimeMinutes = boostRealMinutes;
                normalTimeLeft = totalNormalMinutes - coveredByBoost;
            }
            const totalRealMinutes = realTimeMinutes + normalTimeLeft;
            const now = new Date();
            const endDate = new Date(now.getTime() + totalRealMinutes * 60000);

            const realUnderBoost = fromMinutes(realTimeMinutes);
            const normalLeft = fromMinutes(normalTimeLeft);
            const totalLeft = fromMinutes(totalRealMinutes);

            const result = document.getElementById('petPotionResult');
            result.innerHTML = formatResultList([
                ['Upgrade left', `${days}d ${hours}h ${minutes}m`],
                ['Boost left', `${boostHours}h ${boostMinutes}m`],
                ['Covered by boost', `${coveredByBoost}m`],
                [finishedWithBoost ? 'Finished entirely under boost!' : 'Time under boost', `${realUnderBoost.hours}h ${realUnderBoost.minutes}m`],
                ['Normal time left', `${normalLeft.days}d ${normalLeft.hours}h ${normalLeft.minutes}m`],
                ['End date', formatDateHuman(endDate)],
                ['Total remaining', `${totalLeft.days}d ${totalLeft.hours}h ${totalLeft.minutes}m`]
            ]);
            result.classList.remove('result--hidden');
            if (typeof AlarmModule !== 'undefined' && AlarmModule.isAvailable()) {
                var existingBtn = result.querySelector('.btn--alarm');
                if (existingBtn) existingBtn.remove();
                var alarmBtn = AlarmModule.createAlarmButton(endDate, 'pet');
                if (alarmBtn) result.appendChild(alarmBtn);
            }
        });
    }

    // --- LIVE RECALCULATION --- //
    // The on-screen keypad dispatches input/change events. Re-run the existing submit
    // handlers after a short debounce so results update while typing. Blank fields are
    // normalised to 0 by parseCount()/toMinutes(), so clearing an input never yields NaN.
    // These synthetic events are flagged so they do NOT trigger the auto-scroll.
    var calculatorForms = document.querySelectorAll('form[id]');
    Array.prototype.forEach.call(calculatorForms, function (form) {
        var timer = null;
        form.addEventListener('input', function () {
            if (timer) {
                clearTimeout(timer);
            }
            timer = setTimeout(function () {
                timer = null;
                var liveEvent = new Event('submit', { cancelable: true, bubbles: true });
                liveEvent.__live = true;
                form.dispatchEvent(liveEvent);
            }, 220);
        });
    });

    // --- AUTO-SCROLL TO RESULT --- //
    // Registered after the page handlers above, so it runs once the result (and the alarm
    // button) has been rendered. Only an explicit Calculate press scrolls; live keypad
    // updates stay put so the keypad never jumps out of view.
    Array.prototype.forEach.call(document.querySelectorAll('form[id]'), function (form) {
        form.addEventListener('submit', function (e) {
            if (e.__live) {
                return;
            }

            var layout = form.closest('.split-layout');
            var result = layout
                ? layout.querySelector('.result')
                : document.querySelector('.result');
            if (!result) {
                return;
            }

            requestAnimationFrame(function () {
                result.scrollIntoView({ behavior: 'smooth', block: 'end' });
            });
        });
    });
});