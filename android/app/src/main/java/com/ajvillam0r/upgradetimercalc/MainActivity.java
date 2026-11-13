package com.ajvillam0r.upgradetimercalc;

import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

/**
 * Full-screen Capacitor host.
 *
 * <p>The floating overlay (icon + calculator window) is owned exclusively by
 * {@link FloatingCalculatorService}, which adds its views straight to the WindowManager and
 * keeps them alive for as long as the foreground service runs. This activity must never
 * touch those views: no onPause(), onStop() or onWindowFocusChanged() override may remove,
 * hide or re-create them, because the overlay has to stay on screen on top of other apps
 * (for example while an alarm is being set from inside a game).</p>
 */
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(AlarmPlugin.class);
        registerPlugin(FloatingPlugin.class);
        super.onCreate(savedInstanceState);

        // The calculators use their own always-visible on-screen keypad; never let the
        // system keyboard appear over the app.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
}
