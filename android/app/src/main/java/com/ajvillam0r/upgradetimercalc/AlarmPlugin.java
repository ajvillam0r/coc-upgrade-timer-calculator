package com.ajvillam0r.upgradetimercalc;

import android.content.Intent;
import android.provider.AlarmClock;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

@CapacitorPlugin(name = "AlarmPlugin")
public class AlarmPlugin extends Plugin {

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

            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getActivity().startActivity(intent);

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
}
