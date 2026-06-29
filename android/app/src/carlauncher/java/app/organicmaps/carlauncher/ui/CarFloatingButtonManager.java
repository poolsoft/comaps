package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherSettings;

/**
 * Ekranda suzulen tasinabilir yardimci buton (Floating Button) yoneticisi.
 * Uygulama arka plandayken on plana ceker, on plandayken ise acilir menu gosterir.
 */
public class CarFloatingButtonManager {

    private static CarFloatingButtonManager instance;
    private final Context context;
    private final WindowManager windowManager;
    
    private FrameLayout floatingView;
    private WindowManager.LayoutParams params;
    private boolean isAdded = false;
    private boolean isAppInForeground = false;
    private android.widget.TextView speedText;
    private GradientDrawable buttonBg;

    private android.location.LocationManager locationManager;
    private android.location.LocationListener locationListener;
    private float nativeGpsSpeed = -1f;

    // Surukleme durumlari
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;
    private boolean isDragging = false;
    private long touchStartTime;

    // Jest ve Uzun Basim Durumlari (TÃƒÂ¼rkÃƒÂ§e karakter yok)
    private final android.os.Handler gestureHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable longClickRunnable;
    private boolean isLongClickTriggered = false;

    // Custom Menu Overlay
    private FrameLayout menuOverlayView;
    private WindowManager.LayoutParams menuParams;

    private boolean isFullScreenMap = false;
    private boolean isInPipMode = false;

    private final android.content.BroadcastReceiver assistantReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("net.osmand.carlauncher.ACTION_SHOW_ASSISTANT_MENU".equals(action)) {
                showOverlayMenuFromDock();
            } else if ("net.osmand.carlauncher.ACTION_LAYOUT_TOGGLE".equals(action)) {
                // Layout degistiginde buton gosterim durumunu guncelle (Turkce karakter yok)
                gestureHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateButtonState();
                    }
                }, 150);
            }
        }
    };

    private CarFloatingButtonManager(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);

        // AlÃ„Â±cÃ„Â± kaydÃ„Â± (TÃƒÂ¼rkÃƒÂ§e karakter yok)
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction("net.osmand.carlauncher.ACTION_SHOW_ASSISTANT_MENU");
        filter.addAction("net.osmand.carlauncher.ACTION_LAYOUT_TOGGLE");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.context.registerReceiver(assistantReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            this.context.registerReceiver(assistantReceiver, filter);
        }

        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this.context)
                .registerReceiver(assistantReceiver, filter);
    }

    public static synchronized CarFloatingButtonManager getInstance(Context context) {
        if (instance == null) {
            instance = new CarFloatingButtonManager(context);
        }
        return instance;
    }

    public void setAppInForeground(boolean foreground) {
        this.isAppInForeground = foreground;
        updateButtonState();
    }

    public void setInPipMode(boolean pip) {
        this.isInPipMode = pip;
        updateButtonState();
    }

    public void setFullScreenMap(boolean fullScreen) {
        this.isFullScreenMap = fullScreen;
        updateButtonState();
    }

    public void updateButtonState() {
        CarLauncherSettings settings = new CarLauncherSettings(context);
        boolean enabled = settings.isFloatingButtonEnabled();

        if (enabled && !isInPipMode) {
            Intent intent = new Intent(context, CarFloatingButtonService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } else {
            Intent intent = new Intent(context, CarFloatingButtonService.class);
            intent.setAction("STOP_SERVICE");
            context.startService(intent);
        }
    }

    public void showButton() {
        if (isAdded) return;

        // Overlay izni kontrolu (Android M ve uzeri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                return; // Ã„Â°zin yoksa sessizce cik
            }
        }

        // Arka plan konum izni uyarisi ve istegi (Android 10 ve uzeri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Arka planda hiz gosterebilmek icin Ayarlar'dan Konum iznini 'Her Zaman' olarak ayarlayiniz.", Toast.LENGTH_LONG).show();
                
                // Ayarlar ekranina yonlendir
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                android.net.Uri uri = android.net.Uri.fromParts("package", context.getPackageName(), null);
                intent.setData(uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }

        try {
            createFloatingView();

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            CarLauncherSettings settings = new CarLauncherSettings(context);
            int size = dpToPx(settings.getFloatingButtonSize());
            params = new WindowManager.LayoutParams(
                    size,
                    size,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            // Ekranin sag orta kisminda baslat
            params.gravity = Gravity.TOP | Gravity.START;
            
            boolean isLandscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            String prefix = isLandscape ? "land_" : "port_";
            android.content.SharedPreferences prefs = context.getSharedPreferences("floating_button_prefs", Context.MODE_PRIVATE);
            
            if (prefs.getBoolean(prefix + "saved", false)) {
                params.x = prefs.getInt(prefix + "x", 0);
                params.y = prefs.getInt(prefix + "y", 0);
            } else {
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                
                if (isLandscape) {
                    // Yatay modda sol altta (Dock'un hemen ustu veya Harita kosesi)
                    params.x = dpToPx(20);
                    params.y = screenHeight - dpToPx(140);
                } else {
                    // Dikey modda sag ortalar
                    params.x = screenWidth - dpToPx(90);
                    params.y = screenHeight / 2 - dpToPx(26);
                }
            }

            floatingView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            touchStartTime = System.currentTimeMillis();
                            isDragging = false;
                            isLongClickTriggered = false;

                            // 500ms basili tutuldugunda uzun basim tetiklenir (parmak kaldirmayi beklemez)
                            longClickRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (!isDragging) {
                                        isLongClickTriggered = true;
                                        onButtonLongClicked();
                                    }
                                }
                            };
                            gestureHandler.postDelayed(longClickRunnable, 500);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = event.getRawX() - initialTouchX;
                            float deltaY = event.getRawY() - initialTouchY;
                            if (Math.abs(deltaX) > 15 || Math.abs(deltaY) > 15) {
                                isDragging = true;
                                gestureHandler.removeCallbacks(longClickRunnable);
                            }
                            params.x = (int) (initialX + deltaX);
                            params.y = (int) (initialY + deltaY);
                            
                            // Ekran sinirlarindan tasmasini onle
                            if (params.x < 0) params.x = 0;
                            if (params.y < 0) params.y = 0;

                            if (isAdded) {
                                windowManager.updateViewLayout(floatingView, params);
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                            gestureHandler.removeCallbacks(longClickRunnable);
                            if (isDragging) {
                                saveButtonPosition(params.x, params.y);
                            } else {
                                if (!isLongClickTriggered) {
                                    onButtonClicked();
                                }
                            }
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(floatingView, params);
            isAdded = true;
            
            app.organicmaps.MwmApplication mwmapp = (app.organicmaps.MwmApplication) context.getApplicationContext();
            app.organicmaps.carlauncher.telemetry.TelemetryManager.getInstance(mwmapp).addListener(telemetryListener);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void hideButton() {
        hideCustomOverlayMenu();
        if (!isAdded) return;
        try {
            windowManager.removeView(floatingView);
            isAdded = false;
            floatingView = null;
            
            app.organicmaps.MwmApplication mwmapp = (app.organicmaps.MwmApplication) context.getApplicationContext();
            app.organicmaps.carlauncher.telemetry.TelemetryManager.getInstance(mwmapp).removeListener(telemetryListener);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createFloatingView() {
        floatingView = new FrameLayout(context);
        int width = dpToPx(86); // 3 rakam (ÃƒÂ¶rn. 120) ve km/h yazÃ„Â±sÃ„Â± iÃƒÂ§in bÃƒÂ¼yÃƒÂ¼tÃƒÂ¼ldÃƒÂ¼
        int height = dpToPx(86);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        floatingView.setLayoutParams(lp);

        // Arka plan: Premium koyu daire ve mavi kenarlik
        buttonBg = new GradientDrawable();
        buttonBg.setShape(GradientDrawable.OVAL);
        buttonBg.setColor(0xEE181824); // Yari transparan premium koyu lacivert
        buttonBg.setStroke(dpToPx(3), 0xFF3D63FF); // Modern mavi kenarlik
        floatingView.setBackground(buttonBg);

        // Ã„Â°kon yerine hiz yazisi (Turkce karakter yok)
        speedText = new android.widget.TextView(context);
        speedText.setTextColor(0xFFFFFFFF);
        speedText.setTextSize(28); // 3 rakam sÃ„Â±Ã„S¸acak font boyutu
        speedText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        speedText.setGravity(Gravity.CENTER);
        
        // Spannable text placeholders
        android.text.SpannableString span = new android.text.SpannableString("--\nkm/h");
        span.setSpan(new android.text.style.RelativeSizeSpan(0.4f), 2, span.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        speedText.setText(span);
        
        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        textLp.gravity = Gravity.CENTER;
        floatingView.addView(speedText, textLp);
    }

    public void setNativeGpsSpeed(float speedKmh) {
        this.nativeGpsSpeed = speedKmh;
        updateSpeedUI(speedKmh, 0f);
    }

    private void updateSpeedUI(float speedKmh, float maxSpeed) {
        if (floatingView == null || speedText == null || buttonBg == null) return;
        
        float currentSpeed = speedKmh / 3.6f;
        int speedKmhInt = Math.round(speedKmh);
        
        String speedStr = String.valueOf(speedKmhInt);
        android.text.SpannableString span = new android.text.SpannableString(speedStr + "\nkm/h");
        span.setSpan(new android.text.style.RelativeSizeSpan(0.4f), speedStr.length(), span.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        speedText.setText(span);

        if (maxSpeed > 0) {
            float diff = currentSpeed - maxSpeed;
            float diffKmh = diff * 3.6f;
            
            if (diffKmh > 5) {
                buttonBg.setStroke(dpToPx(3), 0xFFFF0000); // Kirmizi border
                speedText.setTextColor(0xFFFF0000);
            } else if (diffKmh > 0) {
                buttonBg.setStroke(dpToPx(3), 0xFFFFA500); // Turuncu border
                speedText.setTextColor(0xFFFFA500);
            } else {
                buttonBg.setStroke(dpToPx(2), 0xFF3D63FF); // Normal Mavi
                speedText.setTextColor(0xFFFFFFFF);
            }
        } else {
            buttonBg.setStroke(dpToPx(2), 0xFF3D63FF);
            speedText.setTextColor(0xFFFFFFFF);
        }
    }

    private app.organicmaps.carlauncher.telemetry.TelemetryManager.TelemetryListener telemetryListener = new app.organicmaps.carlauncher.telemetry.TelemetryManager.TelemetryListener() {
        @Override
        public void onTelemetryUpdated(app.organicmaps.carlauncher.telemetry.TelemetryManager.LocationState loc, app.organicmaps.carlauncher.telemetry.TelemetryManager.NavigationState nav, app.organicmaps.carlauncher.telemetry.TelemetryManager.ObdState obd) {
            float speedKmh = (nativeGpsSpeed >= 0) ? nativeGpsSpeed : loc.speedKmh;
            app.organicmaps.MwmApplication app = (app.organicmaps.MwmApplication) context.getApplicationContext();
            float maxSpeed = getMaxSpeed(app, loc.rawLocation);
            updateSpeedUI(speedKmh, maxSpeed);
        }
    };

    private float getMaxSpeed(app.organicmaps.MwmApplication mApplication, android.location.Location location) {
        return 0;
    }

    private void onButtonClicked() {
        if (!isAppInForeground) {
            bringAppToForeground();
        } else {
            // On planda tek tiklandiginda: Desktop modu toggle yapar (Kisa Basim)
            Intent intent = new Intent("net.osmand.carlauncher.ACTION_DESKTOP_TOGGLE");
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
            
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(new Intent("net.osmand.carlauncher.ACTION_DESKTOP_TOGGLE"));
        }
    }

    private void onButtonLongClicked() {
        if (!isAppInForeground) {
            bringAppToForeground();
        } else {
            // On planda uzun basildiginda: Custom popup menu ac
            showOverlayMenu();
        }
    }

    private void bringAppToForeground() {
        Intent intent = new Intent(context, app.organicmaps.carlauncher.CarLauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private void showOverlayMenu() {
        if (floatingView == null) return;
        
        if (menuOverlayView != null) {
            hideCustomOverlayMenu();
            return;
        }

        // Custom FrameLayout menu karti
        menuOverlayView = new FrameLayout(context);
        
        // Premium koyu tema: #111115 arka plan, #3D63FF neon mavi cerceve
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xF4111115); // Koyu premium renk
        bg.setStroke(dpToPx(1), 0xFF3D63FF); // Modern mavi kenarlik
        bg.setCornerRadius(dpToPx(12));
        menuOverlayView.setBackground(bg);
        
        int padding = dpToPx(8);
        menuOverlayView.setPadding(padding, padding, padding, padding);

        android.widget.LinearLayout content = new android.widget.LinearLayout(context);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        menuOverlayView.addView(content, contentLp);

        // MenÃƒÂ¼ elemanlarÃ„Â± (TÃƒÂ¼rkÃƒÂ§e karakter yok!)
        addMenuItem(content, "Gorunumu Degistir", "net.osmand.carlauncher.ACTION_LAYOUT_TOGGLE");
        addMenuItem(content, "Masaustu Modu (Desktop)", "net.osmand.carlauncher.ACTION_DESKTOP_TOGGLE");
        addMenuItem(content, "Car Launcher Ayarlari", "net.osmand.carlauncher.ACTION_OPEN_SETTINGS");

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        menuParams = new WindowManager.LayoutParams(
                dpToPx(240),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.TOP | Gravity.START;

        // Butonun yaninda konumlandir
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        if (params.x > screenWidth / 2) {
            // Buton sagda ise: menuyu sola ac
            menuParams.x = params.x - dpToPx(250);
        } else {
            // Buton solda ise: menuyu saga ac
            menuParams.x = params.x + dpToPx(60);
        }
        menuParams.y = params.y;

        // Disari dokunuldugunda kapanmasi icin touch listener
        menuOverlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    hideCustomOverlayMenu();
                    return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(menuOverlayView, menuParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showOverlayMenuFromDock() {
        if (menuOverlayView != null) {
            hideCustomOverlayMenu();
            return;
        }

        // Custom FrameLayout menu karti
        menuOverlayView = new FrameLayout(context);
        
        // Premium koyu tema: #111115 arka plan, #3D63FF neon mavi cerceve
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xF4111115); // Koyu premium renk
        bg.setStroke(dpToPx(1), 0xFF3D63FF); // Modern mavi kenarlik
        bg.setCornerRadius(dpToPx(12));
        menuOverlayView.setBackground(bg);
        
        int padding = dpToPx(8);
        menuOverlayView.setPadding(padding, padding, padding, padding);

        android.widget.LinearLayout content = new android.widget.LinearLayout(context);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        menuOverlayView.addView(content, contentLp);

        // MenÃƒÂ¼ elemanlarÃ„Â± (TÃƒÂ¼rkÃƒÂ§e karakter yok!)
        addMenuItem(content, "Gorunumu Degistir", "net.osmand.carlauncher.ACTION_LAYOUT_TOGGLE");
        addMenuItem(content, "Masaustu Modu (Desktop)", "net.osmand.carlauncher.ACTION_DESKTOP_TOGGLE");
        addMenuItem(content, "Car Launcher Ayarlari", "net.osmand.carlauncher.ACTION_OPEN_SETTINGS");

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        menuParams = new WindowManager.LayoutParams(
                dpToPx(240),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        
        // Dock sag altta oldugu icin menuyu de sag altta konumlandiriyoruz
        menuParams.gravity = Gravity.BOTTOM | Gravity.END;
        menuParams.x = dpToPx(20);
        menuParams.y = dpToPx(70);

        // Disari dokunuldugunda kapanmasi icin touch listener
        menuOverlayView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                    hideCustomOverlayMenu();
                    return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(menuOverlayView, menuParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addMenuItem(android.widget.LinearLayout container, String title, final String action) {
        final android.widget.TextView tv = new android.widget.TextView(context);
        tv.setText(title);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        tv.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        
        final GradientDrawable itemBg = new GradientDrawable();
        itemBg.setShape(GradientDrawable.RECTANGLE);
        itemBg.setCornerRadius(dpToPx(8));
        itemBg.setColor(0x00000000);
        tv.setBackground(itemBg);

        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        itemBg.setColor(0x223D63FF); // Parmak basildiginda mavi hover rengi
                        tv.setBackground(itemBg);
                        break;
                    case MotionEvent.ACTION_UP:
                        itemBg.setColor(0x00000000);
                        tv.setBackground(itemBg);
                        
                        // Broadcast yayini gonder
                        Intent intent = new Intent(action);
                        intent.setPackage(context.getPackageName());
                        context.sendBroadcast(intent);
                        
                        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                                .sendBroadcast(new Intent(action));

                        hideCustomOverlayMenu();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        itemBg.setColor(0x00000000);
                        tv.setBackground(itemBg);
                        break;
                }
                return true;
            }
        });

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, dpToPx(2), 0, dpToPx(2));
        container.addView(tv, lp);
    }

    private void hideCustomOverlayMenu() {
        if (menuOverlayView != null) {
            try {
                windowManager.removeView(menuOverlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            menuOverlayView = null;
        }
    }

    private void saveButtonPosition(int x, int y) {
        boolean isLandscape = context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        String prefix = isLandscape ? "land_" : "port_";
        context.getSharedPreferences("floating_button_prefs", Context.MODE_PRIVATE)
                .edit()
                .putInt(prefix + "x", x)
                .putInt(prefix + "y", y)
                .putBoolean(prefix + "saved", true)
                .apply();
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
