package com.ajvillam0r.upgradetimercalc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.AlarmClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FloatingCalculatorService extends Service {
    private static final String TAG = "UpgradeTimerService";

    public static final String ACTION_STOP = "com.ajvillam0r.upgradetimercalc.STOP_FLOATING";
    /** Sent when Float Mode is requested again while the service is already alive. */
    public static final String ACTION_SHOW = "com.ajvillam0r.upgradetimercalc.SHOW_FLOATING";

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "upgrade_timer_overlay";
    private static final int BUBBLE_SIZE_DP = 56;
    private static final int TAP_SLOP_DP = 10;
    private static final int DISMISS_SIZE_DP = 64;
    private static final int DISMISS_MARGIN_DP = 34;
    private static final int RESIZE_GRIP_DP = 42;
    private static final int WINDOW_MIN_WIDTH_DP = 210;
    private static final int WINDOW_MIN_HEIGHT_DP = 260;
    private static final int WINDOW_MIN_HEIGHT_LANDSCAPE_DP = 220;
    private static final int WINDOW_MAX_WIDTH_DP = 560;
    private static final int WINDOW_MAX_HEIGHT_DP = 760;
    // Reference width for proportional content scaling: the page always lays out at
    // ~360dp and is visually zoomed to the current window width.
    private static final int SCALE_BASELINE_DP = 360;
    private static final String ASSET_BASE_URL = "file:///android_asset/public/";
    private static final String BRIDGE_NAME = "AndroidFloatingBridge";

    /**
     * Guard that prevents a second bubble from ever being added to the
     * WindowManager when Float Mode is started more than once.
     */
    private static boolean isRunning = false;

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private View windowView;
    private WindowManager.LayoutParams windowParams;
    private WebView webView;
    private SoftInputWebView softInputWebView;
    private View dismissView;
    private WindowManager.LayoutParams dismissParams;
    private boolean isExpanded = false;

    // Remembered window geometry (px). Kept across minimize/reopen so the resized
    // floating window and the WebView survive collapsing.
    private int windowWidth = 0;
    private int windowHeight = 0;
    private int windowX = Integer.MIN_VALUE;
    private int windowY = Integer.MIN_VALUE;

    private boolean overDismissTarget = false;
    private boolean scaleUpdateScheduled = false;
    private int bubbleX = 0;
    private int bubbleY = 0;
    private StopReceiver stopReceiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable scaleUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            scaleUpdateScheduled = false;
            applyWindowScale();
        }
    };

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        boolean alreadyActive = isRunning && (bubbleView != null || windowView != null);
        if (alreadyActive) {
            // Never add a second bubble: reuse the bubble/window that already exists.
            if (isExpanded) {
                toast("Float Mode is already active");
            } else {
                // Minimized (or ACTION_SHOW requested): bring the existing window back.
                expandCalculator();
            }
            return START_STICKY;
        }

        registerStopReceiver();
        showBubble();

        // Only claim the running state once the bubble is really on screen, otherwise a
        // failed addView() would leave isRunning == true and every later start would be
        // swallowed by the alreadyActive branch above.
        if (bubbleView == null || bubbleView.getParent() == null) {
            isRunning = false;
            toast("Unable to show floating calculator");
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Upgrade Timer Overlay",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, FloatingCalculatorService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Upgrade Timer Calc")
            .setContentText("Upgrade Timer Calc is floating. Tap to manage.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(0, "Stop", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }

    /* ---------------------------------------------------------------------
     * Bubble
     * ------------------------------------------------------------------- */

    private View createBubbleView() {
        FrameLayout bubble = new FrameLayout(this);
        bubble.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(BUBBLE_SIZE_DP), dpToPx(BUBBLE_SIZE_DP)));

        // Circular 56dp container: white disc with a soft outer shadow ring + hairline stroke.
        GradientDrawable shadowRing = new GradientDrawable();
        shadowRing.setShape(GradientDrawable.OVAL);
        shadowRing.setColor(0x2E000000);

        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFFFFFFFF);
        disc.setStroke(Math.max(1, dpToPx(1)), 0x22000000);

        LayerDrawable background = new LayerDrawable(new Drawable[]{shadowRing, disc});
        background.setLayerInset(1, dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));

        bubble.setBackground(background);
        bubble.setElevation(dpToPx(6));
        bubble.setClipToOutline(true);
        bubble.setContentDescription("Open floating calculator");

        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        icon.setLayoutParams(iconParams);
        // FIT_CENTER keeps the whole launcher icon (adaptive icons included) visible instead
        // of zooming/cropping it, so the bubble matches the app drawer icon.
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));

        GradientDrawable iconDisc = new GradientDrawable();
        iconDisc.setShape(GradientDrawable.OVAL);
        iconDisc.setColor(0xFFFFFFFF);
        icon.setBackground(iconDisc);
        icon.setClipToOutline(true);

        // Use the real launcher icon of the installed APK, exactly like the app drawer.
        Drawable appIcon = loadAppIcon();
        if (appIcon != null) {
            icon.setImageDrawable(appIcon);
        } else {
            icon.setImageResource(R.mipmap.ic_launcher);
        }

        bubble.addView(icon);
        setupBubbleTouchListener(bubble);

        return bubble;
    }

    private Drawable loadAppIcon() {
        try {
            return getPackageManager().getApplicationIcon(getApplicationInfo());
        } catch (Exception first) {
            try {
                return getPackageManager().getApplicationIcon(getPackageName());
            } catch (Exception second) {
                Log.w(TAG, "Unable to load launcher icon", second);
                return null;
            }
        }
    }

    private void setupBubbleTouchListener(View bubble) {
        final int tapSlop = dpToPx(TAP_SLOP_DP);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX;
            private float initialTouchY;
            private int initialWindowX;
            private int initialWindowY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialWindowX = bubbleParams != null ? bubbleParams.x : 0;
                        initialWindowY = bubbleParams != null ? bubbleParams.y : 0;
                        moved = false;
                        overDismissTarget = false;
                        hideDismissTarget();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        if (Math.abs(deltaX) > tapSlop || Math.abs(deltaY) > tapSlop) {
                            moved = true;
                        }

                        if (bubbleParams != null) {
                            bubbleParams.x = initialWindowX + (int) deltaX;
                            bubbleParams.y = initialWindowY + (int) deltaY;
                            try {
                                windowManager.updateViewLayout(bubble, bubbleParams);
                            } catch (IllegalArgumentException ignored) {
                                // View was detached while dragging.
                            }
                            bubbleX = bubbleParams.x;
                            bubbleY = bubbleParams.y;
                        }

                        if (moved) {
                            showDismissTarget();
                            boolean over = isOverDismissTarget(event.getRawX(), event.getRawY());
                            if (over != overDismissTarget) {
                                overDismissTarget = over;
                                updateDismissHighlight(over);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        float upDeltaX = Math.abs(event.getRawX() - initialTouchX);
                        float upDeltaY = Math.abs(event.getRawY() - initialTouchY);

                        // Tap-vs-drag threshold: a small, stationary press is a tap.
                        boolean isTap = event.getPointerCount() == 1
                            && !moved
                            && upDeltaX < tapSlop
                            && upDeltaY < tapSlop;

                        boolean droppedOnTarget = moved
                            && isOverDismissTarget(event.getRawX(), event.getRawY());

                        hideDismissTarget();

                        if (isTap) {
                            v.performClick();
                            // Defer the expand until the current touch dispatch is finished:
                            // expandCalculator() removes this very view from the WindowManager.
                            v.post(FloatingCalculatorService.this::expandCalculator);
                        } else if (droppedOnTarget) {
                            dismissService();
                        } else {
                            snapBubbleToEdge();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        hideDismissTarget();
                        snapBubbleToEdge();
                        return true;
                }
                return false;
            }
        });
    }

    private void snapBubbleToEdge() {
        if (bubbleView == null || bubbleParams == null) {
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bubbleSize = dpToPx(BUBBLE_SIZE_DP);

        if (bubbleParams.x + bubbleSize / 2 < screenWidth / 2) {
            bubbleParams.x = 0;
        } else {
            bubbleParams.x = Math.max(0, screenWidth - bubbleSize);
        }
        bubbleParams.y = Math.max(0, Math.min(bubbleParams.y, screenHeight - bubbleSize));

        try {
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        } catch (IllegalArgumentException ignored) {
            // View was detached.
        }

        bubbleX = bubbleParams.x;
        bubbleY = bubbleParams.y;
    }

    /* ---------------------------------------------------------------------
     * Drag-to-dismiss target
     * ------------------------------------------------------------------- */

    private View createDismissTarget() {
        int size = dpToPx(DISMISS_SIZE_DP);

        FrameLayout target = new FrameLayout(this);
        target.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        target.setContentDescription("Drop here to stop the floating calculator");

        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setColor(0x33FF3B30);

        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xE6222026);
        disc.setStroke(Math.max(1, dpToPx(2)), 0xFFFF3B30);

        LayerDrawable background = new LayerDrawable(new Drawable[]{glow, disc});
        background.setLayerInset(1, dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        target.setBackground(background);
        target.setElevation(dpToPx(10));

        TextView icon = new TextView(this);
        icon.setText("✕");
        icon.setTextColor(0xFFFFFFFF);
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        icon.setTypeface(null, android.graphics.Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        target.addView(icon, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        target.setAlpha(0.82f);
        return target;
    }

    private WindowManager.LayoutParams getDismissLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        int size = dpToPx(DISMISS_SIZE_DP);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dpToPx(DISMISS_MARGIN_DP);
        return params;
    }

    private void showDismissTarget() {
        if (dismissView == null) {
            dismissView = createDismissTarget();
        }
        if (dismissView.getParent() != null) {
            return;
        }

        dismissParams = getDismissLayoutParams();
        try {
            windowManager.addView(dismissView, dismissParams);
        } catch (Exception e) {
            Log.w(TAG, "Unable to show dismiss target", e);
            return;
        }

        overDismissTarget = false;
        dismissView.setScaleX(1f);
        dismissView.setScaleY(1f);
        dismissView.setAlpha(0.82f);
    }

    private void hideDismissTarget() {
        overDismissTarget = false;
        if (dismissView != null) {
            removeViewSafely(dismissView);
        }
        if (bubbleView != null) {
            bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        }
    }

    private void updateDismissHighlight(boolean highlighted) {
        if (dismissView == null) {
            return;
        }

        float scale = highlighted ? 1.18f : 1f;
        dismissView.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(highlighted ? 1f : 0.82f)
            .setDuration(120)
            .start();

        if (bubbleView != null) {
            float bubbleScale = highlighted ? 0.9f : 1f;
            bubbleView.animate().scaleX(bubbleScale).scaleY(bubbleScale).setDuration(120).start();
        }
    }

    private boolean isOverDismissTarget(float rawX, float rawY) {
        if (dismissView == null || dismissView.getParent() == null) {
            return false;
        }

        float centerX;
        float centerY;
        int[] location = new int[2];
        dismissView.getLocationOnScreen(location);
        centerX = location[0] + dismissView.getWidth() / 2f;
        centerY = location[1] + dismissView.getHeight() / 2f;

        // getLocationOnScreen() can return 0,0 for one frame after addView(); fall back to
        // the expected bottom-center position in that case.
        if (location[0] == 0 && location[1] == 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            centerX = metrics.widthPixels / 2f;
            centerY = metrics.heightPixels - dpToPx(DISMISS_MARGIN_DP) - dpToPx(DISMISS_SIZE_DP) / 2f;
        }

        float radius = dpToPx(DISMISS_SIZE_DP) / 2f + dpToPx(BUBBLE_SIZE_DP) / 2f + dpToPx(12);
        float dx = rawX - centerX;
        float dy = rawY - centerY;
        return (dx * dx + dy * dy) <= (radius * radius);
    }

    private void dismissService() {
        hideDismissTarget();
        removeViewSafely(bubbleView);
        bubbleView = null;
        bubbleParams = null;
        stopSelf();
    }

    private void showBubble() {
        if (bubbleView == null) {
            bubbleView = createBubbleView();
        }
        if (bubbleView.getParent() != null) {
            return;
        }
        bubbleParams = getBubbleLayoutParams();
        try {
            windowManager.addView(bubbleView, bubbleParams);
        } catch (Exception e) {
            Log.e(TAG, "Unable to add floating bubble", e);
        }
    }

    /* ---------------------------------------------------------------------
     * Expanded calculator window
     * ------------------------------------------------------------------- */

    private View createFloatingWindow() {
        RootLayout window = new RootLayout(this);
        window.setClipToOutline(true);
        // The window must be focusable so the IME can type into the calculator inputs.
        window.setFocusable(true);
        window.setFocusableInTouchMode(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFFFFFFF);
        background.setCornerRadius(dpToPx(16));
        window.setBackground(background);
        window.setElevation(dpToPx(12));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        window.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        content.addView(createDragHandle());

        softInputWebView = new SoftInputWebView(this);
        webView = softInputWebView;
        webView.setBackgroundColor(0xFFFFFFFF);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ));

        configureWebView(webView);
        content.addView(webView);

        // Overlay the resize grip last so it sits above the WebView and receives touches
        // in the bottom-right corner.
        window.addView(createResizeGrip());

        return window;
    }

    /**
     * Slim top bar used to move the window. It also carries the small minimize button on
     * the upper-right corner; tapping outside the window no longer collapses it.
     */
    private View createDragHandle() {
        FrameLayout handle = new FrameLayout(this);
        handle.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(30)
        ));
        handle.setBackgroundColor(0xFFFFFFFF);
        handle.setContentDescription("Drag to move calculator");

        View pill = new View(this);
        int pillHeight = dpToPx(4);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
            dpToPx(38),
            pillHeight,
            Gravity.CENTER
        );
        pill.setLayoutParams(pillParams);

        GradientDrawable pillBackground = new GradientDrawable();
        pillBackground.setShape(GradientDrawable.RECTANGLE);
        pillBackground.setCornerRadius(pillHeight / 2f);
        pillBackground.setColor(0xFFC7C7CC);
        pill.setBackground(pillBackground);

        handle.addView(pill);

        TextView minimize = new TextView(this);
        GradientDrawable minimizeBackground = new GradientDrawable();
        minimizeBackground.setShape(GradientDrawable.OVAL);
        minimizeBackground.setColor(0xFFF0F0F3);
        minimizeBackground.setStroke(Math.max(1, dpToPx(1)), 0x14000000);
        minimize.setBackground(minimizeBackground);
        minimize.setText("–");
        minimize.setTextColor(0xFF3A3A3C);
        minimize.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        minimize.setGravity(Gravity.CENTER);
        minimize.setIncludeFontPadding(false);
        minimize.setPadding(0, 0, 0, 0);
        minimize.setMinWidth(0);
        minimize.setMinHeight(0);
        minimize.setClickable(true);
        minimize.setFocusable(true);
        minimize.setContentDescription("Minimize calculator");
        minimize.setOnClickListener(v -> mainHandler.post(FloatingCalculatorService.this::collapseToBubble));

        FrameLayout.LayoutParams minimizeParams = new FrameLayout.LayoutParams(
            dpToPx(26),
            dpToPx(26),
            Gravity.END | Gravity.CENTER_VERTICAL
        );
        minimizeParams.rightMargin = dpToPx(6);
        handle.addView(minimize, minimizeParams);

        setupWindowDrag(handle);

        return handle;
    }

    private View createResizeGrip() {
        ResizeGripView grip = new ResizeGripView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            dpToPx(RESIZE_GRIP_DP),
            dpToPx(RESIZE_GRIP_DP),
            Gravity.END | Gravity.BOTTOM
        );
        params.rightMargin = dpToPx(2);
        params.bottomMargin = dpToPx(2);
        grip.setLayoutParams(params);
        grip.setContentDescription("Resize calculator");
        setupResizeGrip(grip);
        return grip;
    }

    private void setupWindowDrag(View handle) {
        final int dragSlop = dpToPx(4);

        handle.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startX;
            private int startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (windowParams == null || windowView == null) {
                    return false;
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = windowParams.x;
                        startY = windowParams.y;
                        dragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;

                        if (!dragging && (Math.abs(deltaX) > dragSlop || Math.abs(deltaY) > dragSlop)) {
                            dragging = true;
                        }

                        if (dragging) {
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            int maxX = Math.max(0, metrics.widthPixels - windowParams.width);
                            int maxY = Math.max(0, metrics.heightPixels - windowParams.height);
                            windowParams.x = clamp(startX + (int) deltaX, 0, maxX);
                            windowParams.y = clamp(startY + (int) deltaY, 0, maxY);
                            windowX = windowParams.x;
                            windowY = windowParams.y;
                            try {
                                windowManager.updateViewLayout(windowView, windowParams);
                            } catch (IllegalArgumentException ignored) {
                                // View was detached while dragging.
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void setupResizeGrip(View grip) {
        final int dragSlop = dpToPx(3);

        grip.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startWidth;
            private int startHeight;
            private int startX;
            private int startY;
            private boolean resizing;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (windowParams == null || windowView == null) {
                    return false;
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startWidth = windowParams.width;
                        startHeight = windowParams.height;
                        startX = windowParams.x;
                        startY = windowParams.y;
                        resizing = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;

                        if (!resizing && (Math.abs(deltaX) > dragSlop || Math.abs(deltaY) > dragSlop)) {
                            resizing = true;
                        }

                        if (resizing) {
                            DisplayMetrics metrics = getResources().getDisplayMetrics();
                            boolean landscape = metrics.widthPixels > metrics.heightPixels;

                            int maxWidth = Math.min(
                                dpToPx(WINDOW_MAX_WIDTH_DP),
                                Math.max(dpToPx(WINDOW_MIN_WIDTH_DP), metrics.widthPixels - dpToPx(8))
                            );
                            int maxHeight = Math.min(
                                dpToPx(WINDOW_MAX_HEIGHT_DP),
                                Math.max(dpToPx(WINDOW_MIN_HEIGHT_DP), metrics.heightPixels - dpToPx(8))
                            );
                            int minWidth = Math.min(dpToPx(WINDOW_MIN_WIDTH_DP), maxWidth);
                            int minHeight = Math.min(
                                dpToPx(landscape ? WINDOW_MIN_HEIGHT_LANDSCAPE_DP : WINDOW_MIN_HEIGHT_DP),
                                maxHeight
                            );

                            int newWidth = clamp(startWidth + (int) deltaX, minWidth, maxWidth);
                            int newHeight = clamp(startHeight + (int) deltaY, minHeight, maxHeight);

                            windowParams.width = newWidth;
                            windowParams.height = newHeight;

                            // Keep the whole window on screen after growing it.
                            windowParams.x = clamp(
                                startX,
                                0,
                                Math.max(0, metrics.widthPixels - newWidth)
                            );
                            windowParams.y = clamp(
                                startY,
                                0,
                                Math.max(0, metrics.heightPixels - newHeight)
                            );

                            windowWidth = newWidth;
                            windowHeight = newHeight;
                            windowX = windowParams.x;
                            windowY = windowParams.y;

                            try {
                                windowManager.updateViewLayout(windowView, windowParams);
                            } catch (IllegalArgumentException ignored) {
                                // View was detached while resizing.
                            }

                            scheduleScaleUpdate();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (resizing) {
                            applyWindowScale();
                        }
                        resizing = false;
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Root container of the expanded window. It no longer watches outside touches: the
     * only minimize affordance is the "–" button in the top bar.
     */
    private class RootLayout extends FrameLayout {
        RootLayout(Context context) {
            super(context);
        }
    }

    /**
     * WebView that never opens the system IME. The numeric keypad rendered inside the
     * page is the only input; the textboxes are readonly with inputmode="none".
     */
    private class SoftInputWebView extends WebView {
        SoftInputWebView(Context context) {
            super(context);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return false;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            return null;
        }
    }

    /** Small diagonal grip drawn in the bottom-right corner of the floating window. */
    private class ResizeGripView extends View {        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int step;

        ResizeGripView(Context context) {
            super(context);
            step = dpToPx(5);
            paint.setColor(0x70000000);
            paint.setStrokeWidth(Math.max(1f, dpToPx(2)));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dpToPx(9);
            float width = getWidth();
            float height = getHeight();

            for (int i = 0; i < 3; i++) {
                float offset = inset + i * step;
                canvas.drawLine(
                    width - offset,
                    height - inset,
                    width - inset,
                    height - offset,
                    paint
                );
            }
        }
    }

    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Keyboard input: the WebView (and its window) must be focusable and in touch mode.
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);

        view.addJavascriptInterface(new FloatingJsBridge(), BRIDGE_NAME);
        view.setWebChromeClient(new WebChromeClient());
        view.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return null;
                }
                return interceptHtmlRequest(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView webView, String url) {
                return interceptHtmlRequest(url != null ? Uri.parse(url) : null);
            }

            @Override
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webView, url);
                // Safety net for pages that were not served through interceptHtmlRequest.
                webView.evaluateJavascript(BRIDGE_JS, null);
                // Belt-and-braces: keypad.js relies on this class when the HTML was not
                // served through interceptHtmlRequest (e.g. a fallback load).
                webView.evaluateJavascript(
                    "document.documentElement.classList.add('utc-floating');",
                    null
                );
                applyWindowScale();
                webView.requestFocus(View.FOCUS_DOWN);
            }
        });

        view.loadUrl(ASSET_BASE_URL + "index.html");
        view.requestFocus(View.FOCUS_DOWN);
    }

    /**
     * Serves the bundled HTML with the native bridge injected into &lt;head&gt; so that
     * Capacitor.Plugins.AlarmPlugin exists before alarm.js/calculator.js run.
     */
    private WebResourceResponse interceptHtmlRequest(Uri uri) {
        try {
            if (uri == null || !"file".equals(uri.getScheme())) {
                return null;
            }

            String path = uri.getPath();
            if (path == null || !path.endsWith(".html")) {
                return null;
            }

            String marker = "/android_asset/";
            int markerIndex = path.indexOf(marker);
            if (markerIndex < 0) {
                return null;
            }

            String assetPath = path.substring(markerIndex + marker.length());
            String html = readAsset(assetPath);
            if (html == null) {
                return null;
            }

            String injected = injectBridgeIntoHtml(html);
            return new WebResourceResponse(
                "text/html",
                "UTF-8",
                new ByteArrayInputStream(injected.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            Log.w(TAG, "HTML interception failed, falling back to default loading", e);
            return null;
        }
    }

    private String readAsset(String assetPath) {
        InputStream input = null;
        try {
            input = getAssets().open(assetPath);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    private String injectBridgeIntoHtml(String html) {
        String injection = "<style id=\"utc-floating-style\">" + FLOATING_CSS + "</style>"
            + "<script>" + BRIDGE_JS + "</script>";

        int headIndex = html.indexOf("</head>");
        if (headIndex >= 0) {
            html = html.substring(0, headIndex) + injection + html.substring(headIndex);
        } else {
            int bodyIndex = html.indexOf("<body");
            if (bodyIndex >= 0) {
                html = html.substring(0, bodyIndex) + injection + html.substring(bodyIndex);
            } else {
                html = injection + html;
            }
        }

        return markAsFloatingDocument(html);
    }

    /**
     * Flags the document so the compact floating-window styles only apply inside the
     * overlay WebView and never to the full-screen app.
     */
    private String markAsFloatingDocument(String html) {
        int htmlTagIndex = html.indexOf("<html");
        if (htmlTagIndex < 0) {
            return html;
        }

        int tagEnd = html.indexOf('>', htmlTagIndex);
        if (tagEnd < 0) {
            return html;
        }

        String openTag = html.substring(htmlTagIndex, tagEnd);
        if (openTag.toLowerCase(Locale.US).contains("class=")) {
            return html;
        }

        return html.substring(0, tagEnd) + " class=\"utc-floating\"" + html.substring(tagEnd);
    }

    private void expandCalculator() {
        if (isExpanded && windowView != null && windowView.getParent() != null) {
            return;
        }

        removeViewSafely(bubbleView);
        bubbleView = null;
        bubbleParams = null;

        try {
            if (windowView == null) {
                windowView = createFloatingWindow();
            }
            windowParams = getWindowLayoutParams();
            if (windowView.getParent() == null) {
                windowManager.addView(windowView, windowParams);
            }
            isExpanded = true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to add floating calculator window", e);
            isExpanded = false;
            // Keep windowView/webView alive so the next tap can retry without losing the
            // current page, scroll position or typed values.
            windowParams = null;
            toast("Unable to open floating calculator");
            showBubble();
            return;
        }

        applyWindowScale();

        windowView.setFocusableInTouchMode(true);
        windowView.requestFocus();

        if (webView != null) {
            webView.onResume();
            final WebView view = webView;
            view.post(() -> {
                view.setFocusableInTouchMode(true);
                view.requestFocus(View.FOCUS_DOWN);
            });
        }
    }

    /**
     * Minimizes the calculator without destroying its WebView. The window view is only
     * detached from the WindowManager, so the loaded page, scroll position and inputs are
     * preserved for the next expandCalculator() call. The WebView is destroyed in
     * onDestroy() when the service actually stops.
     */
    private void collapseToBubble() {
        if (!isExpanded && (windowView == null || windowView.getParent() == null)) {
            return;
        }

        hideSoftKeyboard();
        mainHandler.removeCallbacks(scaleUpdateRunnable);
        scaleUpdateScheduled = false;
        if (windowView != null) {
            windowView.clearFocus();
        }
        if (webView != null) {
            webView.clearFocus();
            webView.onPause();
        }

        removeViewSafely(windowView);
        isExpanded = false;
        showBubble();
    }

    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return;
            }
            View target = windowView != null ? windowView : webView;
            if (target != null) {
                imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to hide soft keyboard", e);
        }
    }

    private void destroyWebView() {
        if (webView == null) {
            return;
        }

        try {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface(BRIDGE_NAME);
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "Error destroying WebView", e);
        } finally {
            webView = null;
            softInputWebView = null;
        }
    }

    /* ---------------------------------------------------------------------
     * Layout params
     * ------------------------------------------------------------------- */

    private WindowManager.LayoutParams getBubbleLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dpToPx(BUBBLE_SIZE_DP),
            dpToPx(BUBBLE_SIZE_DP),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bubbleX;
        params.y = bubbleY;

        return params;
    }

    private WindowManager.LayoutParams getWindowLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        boolean landscape = screenWidth > screenHeight;

        int defaultWidth;
        int defaultHeight;
        if (landscape) {
            // Wide and short: keep most of the height so the calculator is usable while
            // playing your game in landscape.
            defaultWidth = clamp((int) (screenWidth * 0.52f), dpToPx(300), dpToPx(440));
            defaultHeight = clamp((int) (screenHeight * 0.82f), dpToPx(260), dpToPx(380));
        } else {
            defaultWidth = clamp((int) (screenWidth * 0.88f), dpToPx(280), dpToPx(400));
            defaultHeight = clamp((int) (screenHeight * 0.65f), dpToPx(360), dpToPx(540));
        }

        defaultWidth = Math.min(defaultWidth, Math.max(dpToPx(160), screenWidth - dpToPx(8)));
        defaultHeight = Math.min(defaultHeight, Math.max(dpToPx(160), screenHeight - dpToPx(8)));

        int maxWidth = Math.min(
            dpToPx(WINDOW_MAX_WIDTH_DP),
            Math.max(dpToPx(WINDOW_MIN_WIDTH_DP), screenWidth - dpToPx(8))
        );
        int maxHeight = Math.min(
            dpToPx(WINDOW_MAX_HEIGHT_DP),
            Math.max(dpToPx(WINDOW_MIN_HEIGHT_DP), screenHeight - dpToPx(8))
        );
        int minWidth = Math.min(dpToPx(WINDOW_MIN_WIDTH_DP), maxWidth);
        int minHeight = Math.min(
            dpToPx(landscape ? WINDOW_MIN_HEIGHT_LANDSCAPE_DP : WINDOW_MIN_HEIGHT_DP),
            maxHeight
        );

        int width = windowWidth > 0 ? clamp(windowWidth, minWidth, maxWidth) : defaultWidth;
        int height = windowHeight > 0 ? clamp(windowHeight, minHeight, maxHeight) : defaultHeight;
        int x = windowX != Integer.MIN_VALUE
            ? windowX
            : Math.max(0, (screenWidth - width) / 2);
        int y = windowY != Integer.MIN_VALUE
            ? windowY
            : (landscape ? dpToPx(8) : dpToPx(64));

        x = clamp(x, 0, Math.max(0, screenWidth - width));
        y = clamp(y, 0, Math.max(0, screenHeight - height));

        windowWidth = width;
        windowHeight = height;
        windowX = x;
        windowY = y;

        // No FLAG_NOT_FOCUSABLE here: the window must be focusable so the in-page keypad
        // and taps work. FLAG_NOT_TOUCH_MODAL lets taps outside the window reach the game;
        // no WATCH_OUTSIDE flag is used anymore.
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        // The system keyboard is never used: the page renders its own numeric keypad
        // and the WebView returns no input connection. Keep the IME hidden regardless.
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.setTitle("Upgrade Timer Calc");

        return params;
    }

    /**
     * Scales the whole web page to the current window width. The page always lays out at
     * the ~360dp baseline and CSS zoom magnifies it to the window, so text, gaps, cards,
     * inputs, buttons and keypad keys all scale together with no horizontal overflow.
     */
    private void applyWindowScale() {
        if (webView == null || windowParams == null) {
            return;
        }

        int baseline = Math.max(1, dpToPx(SCALE_BASELINE_DP));
        float scale = clampFloat(windowParams.width / (float) baseline, 0.55f, 1.15f);

        // CSS zoom handles all scaling; keep the native text zoom neutral to avoid
        // scaling text twice.
        try {
            webView.getSettings().setTextZoom(100);
        } catch (Exception ignored) {
            // WebView already detached.
        }

        String scaleValue = String.format(Locale.US, "%.3f", scale);
        String js = "(function(){try{var r=document.documentElement;"
            + "r.style.setProperty('--ui-scale','" + scaleValue + "');"
            + "r.style.setProperty('zoom','" + scaleValue + "');"
            + "}catch(e){}})();";
        try {
            webView.evaluateJavascript(js, null);
        } catch (Exception ignored) {
            // Page not ready yet; onPageFinished and the next resize will reapply it.
        }
    }

    private void scheduleScaleUpdate() {
        if (scaleUpdateScheduled) {
            return;
        }
        scaleUpdateScheduled = true;
        mainHandler.postDelayed(scaleUpdateRunnable, 32);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (!isExpanded || windowView == null || windowView.getParent() == null) {
            return;
        }

        try {
            windowParams = getWindowLayoutParams();
            windowManager.updateViewLayout(windowView, windowParams);
            applyWindowScale();
        } catch (Exception e) {
            Log.w(TAG, "Unable to resize floating window after rotation", e);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int pxToDp(int px) {
        return (int) (px / getResources().getDisplayMetrics().density);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    /* ---------------------------------------------------------------------
     * JS bridge (AlarmPlugin shim for the floating WebView)
     * ------------------------------------------------------------------- */

    private class FloatingJsBridge {
        @JavascriptInterface
        public String setAlarm(int hour, int minute, String message) {
            return handleSetAlarm(hour, minute, message);
        }

        @JavascriptInterface
        public void expandCalculator() {
            mainHandler.post(FloatingCalculatorService.this::expandCalculator);
        }

        @JavascriptInterface
        public void minimizeCalculator() {
            mainHandler.post(FloatingCalculatorService.this::collapseToBubble);
        }

        @JavascriptInterface
        public void closeCalculator() {
            mainHandler.post(FloatingCalculatorService.this::stopSelf);
        }
    }

    private String handleSetAlarm(int hour, int minute, String message) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "{\"success\":false,\"error\":\"Invalid time: hour must be 0-23, minute must be 0-59\"}";
        }

        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE,
                message != null && !message.isEmpty() ? message : "Upgrade Complete");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                return "{\"success\":true}";
            }

            return "{\"success\":false,\"error\":\"No Clock app found to handle alarm\"}";
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"Failed to set alarm: " + escapeJson(e.getMessage()) + "\"}";
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ");
    }

    /**
     * Compact layout injected only into pages rendered inside the floating window.
     * Scoped to html.utc-floating so the normal full-screen app is untouched.
     */
    private static final String FLOATING_CSS =
        "html.utc-floating{--ui-zoom:1;--ui-scale:1;overflow-y:auto;overflow-x:hidden;}" +
        "html.utc-floating,html.utc-floating body{min-height:calc(100vh / var(--ui-scale,1));}" +
        "html.utc-floating body{word-break:break-word;}" +
        "html.utc-floating img{max-width:100%;}" +
        "html.utc-floating .navbar{height:calc(46px * var(--ui-zoom));padding:0 calc(8px * var(--ui-zoom));}" +
        "html.utc-floating .navbar__logo{font-size:0.95rem;max-width:58%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}" +
        "html.utc-floating .navbar__burger{gap:calc(3px * var(--ui-zoom));margin-left:calc(6px * var(--ui-zoom));}" +
        "html.utc-floating .navbar__burger-line{width:calc(20px * var(--ui-zoom));height:calc(3px * var(--ui-zoom));}" +
        "html.utc-floating .navbar__links{top:calc(44px * var(--ui-zoom));right:calc(6px * var(--ui-zoom));min-width:calc(140px * var(--ui-zoom));padding:calc(6px * var(--ui-zoom)) 0;border-radius:14px;}" +
        "html.utc-floating .navbar__link{padding:calc(6px * var(--ui-zoom)) calc(12px * var(--ui-zoom));font-size:0.92rem;}" +
        "html.utc-floating .navbar__float-btn{display:none !important;}" +
        "html.utc-floating .container{max-width:none;padding:calc(12px * var(--ui-zoom)) calc(10px * var(--ui-zoom)) calc(26px * var(--ui-zoom));}" +
        "html.utc-floating .container--centered{min-height:0;justify-content:flex-start;padding-top:calc(12px * var(--ui-zoom));}" +
        "html.utc-floating .title{font-size:1.3rem;line-height:1.15;margin:0 0 0.45em;letter-spacing:0.01em;}" +
        "html.utc-floating .subtitle{font-size:0.9rem;margin-bottom:1em;}" +
        "html.utc-floating .grid{gap:0.65rem;}" +
        "html.utc-floating .grid--potions{grid-template-columns:repeat(auto-fit,minmax(calc(150px * var(--ui-zoom)),1fr));gap:0.65rem;margin-top:0.9em;}" +
        "html.utc-floating .card,html.utc-floating .form__input,html.utc-floating .btn{box-sizing:border-box;}" +
        "html.utc-floating .card{padding:calc(16px * var(--ui-zoom)) calc(10px * var(--ui-zoom)) calc(12px * var(--ui-zoom));border-radius:14px;max-width:none;}" +
        "html.utc-floating .card--content{min-width:0;}" +
        "html.utc-floating .card--main{min-height:calc(104px * var(--ui-zoom));}" +
        "html.utc-floating .card__title{font-size:0.92rem;margin-top:0.65em;}" +
        "html.utc-floating .card__icon--img{width:calc(38px * var(--ui-zoom));height:calc(38px * var(--ui-zoom));margin-bottom:calc(6px * var(--ui-zoom));border-radius:10px;}" +
        "html.utc-floating .form{gap:0.75rem;margin:0.9em auto 0;}" +
        "html.utc-floating .form__row{gap:0.45rem;}" +
        "html.utc-floating .form__group{min-width:calc(60px * var(--ui-zoom));max-width:calc(96px * var(--ui-zoom));flex:1 1 calc(60px * var(--ui-zoom));gap:0.2rem;}" +
        "html.utc-floating .form__label{font-size:0.8rem;min-height:1.9rem;line-height:1.15;}" +
        "html.utc-floating .form__input{width:calc(50px * var(--ui-zoom));padding:calc(8px * var(--ui-zoom)) calc(5px * var(--ui-zoom));font-size:0.95rem;border-radius:9px;}" +
        "html.utc-floating .keypad{margin:0;}" +
        "html.utc-floating .keypad__grid{gap:calc(4px * var(--ui-zoom));}" +
        "html.utc-floating .keypad__key{min-height:calc(30px * var(--ui-zoom));padding:calc(4px * var(--ui-zoom)) 0;font-size:0.95rem;}" +
        "html.utc-floating .btn{font-size:0.95rem;padding:calc(10px * var(--ui-zoom)) calc(16px * var(--ui-zoom));border-radius:10px;}" +
        "html.utc-floating .tabs{gap:0.35rem;margin:0.8em 0 0.2em;flex-wrap:wrap;}" +
        "html.utc-floating .tabs__btn{font-size:0.85rem;padding:0.5em 0.85em;}" +
        "html.utc-floating .split-layout{gap:0.9rem;}" +
        "html.utc-floating .result{margin-top:0.9em;padding:calc(0.8em * var(--ui-zoom));font-size:0.92em;border-radius:14px;}" +
        "html.utc-floating .result__placeholder{padding:0.9em 0.4em;}" +
        "html.utc-floating .result__icon{height:calc(36px * var(--ui-zoom));}" +
        "html.utc-floating .result__text{font-size:0.88em;}" +
        "html.utc-floating .btn--alarm{font-size:0.88rem;padding:calc(9px * var(--ui-zoom)) calc(14px * var(--ui-zoom));}" +
        "html.utc-floating .modal__content{padding:1.2em;margin:0.7em;border-radius:16px;}" +
        "html.utc-floating .modal__icon{font-size:2em;}" +
        "html.utc-floating .modal__title{font-size:1.15em;}" +
        "html.utc-floating .modal__text{font-size:0.92em;}" +
        "html.utc-floating .about__card{margin-top:1em;padding:1.2rem 1rem;font-size:0.95rem;border-radius:1rem;}" +
        "@media (orientation:landscape){" +
        "html.utc-floating .navbar{height:calc(40px * var(--ui-zoom));}" +
        "html.utc-floating .navbar__links{top:calc(38px * var(--ui-zoom));}" +
        "html.utc-floating .container{padding:calc(8px * var(--ui-zoom)) calc(10px * var(--ui-zoom)) calc(18px * var(--ui-zoom));}" +
        "html.utc-floating .container--centered{padding-top:calc(6px * var(--ui-zoom));}" +
        "html.utc-floating .title{font-size:1.1rem;margin-bottom:0.3em;}" +
        "html.utc-floating .subtitle{font-size:0.85rem;margin-bottom:0.7em;}" +
        "html.utc-floating .card{padding:calc(12px * var(--ui-zoom)) calc(8px * var(--ui-zoom)) calc(10px * var(--ui-zoom));}" +
        "html.utc-floating .card--main{min-height:calc(88px * var(--ui-zoom));}" +
        "html.utc-floating .card__icon--img{width:calc(32px * var(--ui-zoom));height:calc(32px * var(--ui-zoom));margin-bottom:calc(4px * var(--ui-zoom));}" +
        "html.utc-floating .form{margin-top:0.6em;gap:0.6rem;}" +
        "html.utc-floating .form__label{min-height:1.6rem;}" +
        "html.utc-floating .form__input{width:calc(46px * var(--ui-zoom));padding:calc(7px * var(--ui-zoom)) calc(4px * var(--ui-zoom));}" +
        "html.utc-floating .keypad__key{min-height:calc(26px * var(--ui-zoom));}" +
        "}" +
        "@media (orientation:landscape) and (min-width:380px){" +
        "html.utc-floating .split-layout{flex-direction:row;align-items:flex-start;gap:0.7rem;min-height:0;}" +
        "html.utc-floating .split-layout__left{width:58%;max-width:none;padding:0;}" +
        "html.utc-floating .split-layout__right{width:42%;max-width:none;padding:0;}" +
        "}";

    /** Capacitor shim injected into every page loaded by the floating WebView. */
    private static final String BRIDGE_JS =
        "(function(){\n" +
        "  if (window.__utcFloatingBridge) { return; }\n" +
        "  window.__utcFloatingBridge = true;\n" +
        "  var bridge = window.AndroidFloatingBridge;\n" +
        "  function setAlarm(options) {\n" +
        "    options = options || {};\n" +
        "    return new Promise(function(resolve, reject) {\n" +
        "      try {\n" +
        "        var raw = bridge ? bridge.setAlarm(Number(options.hour), Number(options.minute), String(options.message || 'Upgrade Complete')) : null;\n" +
        "        var result = raw ? JSON.parse(raw) : { success: false, error: 'Native bridge unavailable' };\n" +
        "        if (result && result.success) { resolve(result); } else { reject(new Error((result && result.error) || 'Failed to set alarm')); }\n" +
        "      } catch (e) { reject(e); }\n" +
        "    });\n" +
        "  }\n" +
        "  var AlarmPlugin = { setAlarm: setAlarm };\n" +
        "  var FloatingPlugin = {\n" +
        "    checkOverlayPermission: function() { return Promise.resolve({ granted: true }); },\n" +
        "    requestOverlayPermission: function() { return Promise.resolve(); },\n" +
        "    startFloatingMode: function() { return Promise.resolve({ success: true, alreadyRunning: true }); },\n" +
        "    stopFloatingMode: function() {\n" +
        "      return new Promise(function(resolve) {\n" +
        "        if (bridge && bridge.closeCalculator) { bridge.closeCalculator(); }\n" +
        "        resolve({ success: true });\n" +
        "      });\n" +
        "    }\n" +
        "  };\n" +
        "  window.Capacitor = window.Capacitor || {};\n" +
        "  window.Capacitor.Plugins = window.Capacitor.Plugins || {};\n" +
        "  window.Capacitor.Plugins.AlarmPlugin = AlarmPlugin;\n" +
        "  window.Capacitor.Plugins.FloatingPlugin = FloatingPlugin;\n" +
        "  window.Capacitor.registerPlugin = function(name) {\n" +
        "    if (name === 'AlarmPlugin') { return AlarmPlugin; }\n" +
        "    if (name === 'FloatingPlugin') { return FloatingPlugin; }\n" +
        "    return {};\n" +
        "  };\n" +
        "  window.Capacitor.isNativePlatform = function() { return true; };\n" +
        "  window.Capacitor.getPlatform = function() { return 'android'; };\n" +
        "  window.Capacitor.convertFileSrc = function(src) { return src; };\n" +
        "})();";

    /* ---------------------------------------------------------------------
     * Lifecycle helpers
     * ------------------------------------------------------------------- */

    private void toast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
            // Toast can fail while the app is in the background on some OEM builds.
        }
    }

    private void removeViewSafely(View view) {
        if (view == null || windowManager == null) {
            return;
        }
        try {
            if (view.getParent() != null) {
                windowManager.removeView(view);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to remove overlay view", e);
        }
    }

    private void registerStopReceiver() {
        if (stopReceiver != null) {
            return;
        }
        stopReceiver = new StopReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(
            stopReceiver,
            new IntentFilter(ACTION_STOP)
        );
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        isExpanded = false;
        mainHandler.removeCallbacks(scaleUpdateRunnable);
        scaleUpdateScheduled = false;

        removeViewSafely(bubbleView);
        bubbleView = null;
        bubbleParams = null;

        removeViewSafely(dismissView);
        dismissView = null;
        dismissParams = null;

        removeViewSafely(windowView);
        windowView = null;
        windowParams = null;

        destroyWebView();

        if (stopReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(stopReceiver);
            } catch (Exception ignored) {
                // Already unregistered.
            }
            stopReceiver = null;
        }

        try {
            stopForeground(true);
        } catch (Exception ignored) {
            // Already stopped.
        }

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class StopReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    }
}
