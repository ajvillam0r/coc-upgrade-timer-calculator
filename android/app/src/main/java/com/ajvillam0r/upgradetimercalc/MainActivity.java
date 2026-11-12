package com.ajvillam0r.upgradetimercalc;

import android.view.WindowManager;

import com.getcapacitor.BridgeActivity;

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
