package com.ajvillam0r.upgradetimercalc;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.JSObject;

@CapacitorPlugin(name = "FloatingPlugin")
public class FloatingPlugin extends Plugin {

    @PluginMethod
    public void checkOverlayPermission(PluginCall call) {
        boolean granted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            granted = Settings.canDrawOverlays(getContext());
        }

        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to open overlay permission settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startFloatingMode(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(getContext())) {
                call.reject("Overlay permission not granted");
                return;
            }

            // Guard: never start a second bubble if the service is already alive.
            boolean alreadyRunning = FloatingCalculatorService.isServiceRunning();

            Intent intent = new Intent(getContext(), FloatingCalculatorService.class);
            if (alreadyRunning) {
                // Ask the running service to expand the existing window instead.
                intent.setAction(FloatingCalculatorService.ACTION_SHOW);
                // The service is already in the foreground, so a plain startService()
                // avoids the Android 12+ background-FGS restriction.
                getContext().startService(intent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("alreadyRunning", alreadyRunning);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to start floating mode: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stopFloatingMode(PluginCall call) {
        try {
            Intent broadcast = new Intent(FloatingCalculatorService.ACTION_STOP);
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(getContext())
                .sendBroadcast(broadcast);

            // Fallback in case the receiver is not registered (yet): stop the service directly.
            getContext().stopService(new Intent(getContext(), FloatingCalculatorService.class));

            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to stop floating mode: " + e.getMessage());
        }
    }
}
