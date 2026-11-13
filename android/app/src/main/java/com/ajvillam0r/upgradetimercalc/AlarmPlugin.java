package com.ajvillam0r.upgradetimercalc;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

@CapacitorPlugin(name = "AlarmPlugin")
public class AlarmPlugin extends Plugin {

    private static final String TAG = "AlarmPlugin";
    /** Delay used for the second overlay re-assert, after the alarm screen has settled. */
    private static final long REASSERT_RETRY_DELAY_MS = 800L;

    @PluginMethod
    public void setAlarm(PluginCall call) {
        Integer hour = call.getInt("hour");
        Integer minute = call.getInt("minute");
        String message = call.getString("message", "Upgrade Complete");

        if (hour == null || minute == null) {
            call.reject("hour and minute are required");
            return;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            call.reject("Invalid time: hour must be 0-23, minute must be 0-59");
            return;
        }

        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);

            // Starting an activity from a plugin/service context requires NEW_TASK. The
            // other flags keep the silent alarm screen out of recents, unanimated and
            // unattributed as a user action, so the game underneath is disturbed as
            // little as possible.
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION
            );

            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                // Application context: this works even if the activity that owns this
                // plugin was already paused/stopped or destroyed, and it never brings
                // MainActivity forward. Both entry points share the same launch path.
                FloatingCalculatorService.launchAlarmIntent(getContext(), intent);

                requestFloatingOverlayReassert();

                JSObject ret = new JSObject();
                ret.put("success", true);
                call.resolve(ret);
            } else {
                call.reject("No Clock app found to handle alarm");
            }
        } catch (Exception e) {
            call.reject("Failed to set alarm: " + e.getMessage());
        }
    }

    /**
     * Tells a running FloatingCalculatorService that an alarm intent was launched, so it
     * re-attaches its overlay immediately instead of leaving the user with nothing on
     * screen until the game is closed.
     */
    private void requestFloatingOverlayReassert() {
        if (!FloatingCalculatorService.isServiceRunning()) {
            return;
        }

        try {
            final Intent reassert = new Intent(FloatingCalculatorService.ACTION_REASSERT);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(reassert);

            // Second attempt once the alarm screen has had time to take (and drop) focus.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    LocalBroadcastManager.getInstance(getContext()).sendBroadcast(reassert);
                } catch (Exception retryError) {
                    Log.w(TAG, "Unable to re-assert floating overlay", retryError);
                }
            }, REASSERT_RETRY_DELAY_MS);
        } catch (Exception e) {
            Log.w(TAG, "Unable to re-assert floating overlay", e);
        }
    }
}
