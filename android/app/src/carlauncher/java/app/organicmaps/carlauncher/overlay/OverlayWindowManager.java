package app.organicmaps.carlauncher.overlay;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;



/**
 * Overlay window yoneticisi.
 * Uygulamalari harita uzerinde floating window olarak acar.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class OverlayWindowManager {

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

    public OverlayWindowManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Overlay window goster.
     */
    public void showOverlay(String packageName) {
        if (isShowing) {
            hideOverlay();
        }

        // 1. Izin Kontrolu (BadTokenException engellemek icin - Turkce karakter yok)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Yuzen pencereler icin 'Diger uygulamalarin uzerinde goster' izni gereklidir.", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    // fallback
                }
                return;
            }
        }

        // 2. Normal uygulamalari serbest pencere (Freeform) olarak baslatmayi dene (Turkce karakter yok)
        boolean isSpecialApp = app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(packageName) || 
                               packageName.equals("com.google.android.youtube") || 
                               packageName.equals("com.google.android.youtube.tv") || 
                               packageName.equals("com.spotify.music") || 
                               packageName.equals("com.google.android.apps.maps");

        if (!isSpecialApp) {
            launchAppFreeform(packageName);
            return;
        }

        // 3. Yuzen pencereyi olustur
        overlayView = createOverlayView(packageName);

        // Window parametreleri (Gorsele onem verildi - Turkce karakter yok)
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dpToPx(420), // Genislik (Premium genislik)
                dpToPx(320), // Yukseklik
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dpToPx(24);
        params.y = dpToPx(120);

        windowManager.addView(overlayView, params);
        isShowing = true;

        // Suruklenebilir yap
        makeDraggable(overlayView, params);
    }

    /**
     * Normal uygulamayi serbest bicimli pencere modunda baslatmayi dener.
     */
    private void launchAppFreeform(String packageName) {
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Ekranda yuzen serbest pencere koordinat ve boyutlari
                Rect bounds = new Rect(dpToPx(80), dpToPx(120), dpToPx(580), dpToPx(500));
                options.setLaunchBounds(bounds);
            }
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                context.startActivity(intent, options.toBundle());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Overlay view olustur (Glassmorphic premium tasarim - Turkce karakter yok).
     */
    private View createOverlayView(String packageName) {
        // En dis container
        FrameLayout container = new FrameLayout(context);
        
        // Premium Yari Transparan Arka Plan ve Stroke (Glassmorphism)
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(16));
        bg.setColor(0xEE14141C); // Yari transparan premium koyu renk
        bg.setStroke(dpToPx(2), 0xFF3D63FF); // Modern mavi kenarlik
        container.setBackground(bg);
        
        int padding = dpToPx(8);
        container.setPadding(padding, padding, padding, padding);

        // Icerik alani (WebView, Compass veya App info karti buraya gelir)
        FrameLayout contentFrame = new FrameLayout(context);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT);
        contentLp.topMargin = dpToPx(36); // Header boslugu
        contentFrame.setLayoutParams(contentLp);

        // --- Icerik yukleme mantigi ---
        if (packageName.equals("internal://antenna")) {
            // 1. Dahili Anten Hizalama (Yuzen compass - Turkce karakter yok)
            // AlignmentView alignmentView = new AlignmentView(context, null);
            /* alignmentView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Canli verileri dinle
            // AntennaManager manager = AntennaManager.getInstance(context);
            float targetAz = (float) manager.getAzimuthSourceToTarget();
            if (targetAz < 0) targetAz += 360;
            float targetPitch = (float) manager.getElevationSourceToTarget();
            alignmentView.setTarget(targetAz, targetPitch);
            
            contentFrame.addView(alignmentView); */
        } else if (packageName.equals("com.google.android.youtube") || packageName.equals("com.google.android.youtube.tv")) {
            // 2. Youtube WebView (Mobil site emulasyonu)
            contentFrame.addView(createWebView("https://m.youtube.com"));
        } else if (packageName.equals("com.spotify.music")) {
            // 3. Spotify WebView (Mobil web player)
            contentFrame.addView(createWebView("https://open.spotify.com"));
        } else if (packageName.equals("com.google.android.apps.maps")) {
            // 4. Google Maps WebView
            contentFrame.addView(createWebView("https://maps.google.com"));
        } else {
            // 5. Standart Uygulamalar icin Bilgi Karti ve Tam Ekran butonu (Turkce karakter yok)
            LinearLayout infoCard = new LinearLayout(context);
            infoCard.setOrientation(LinearLayout.VERTICAL);
            infoCard.setGravity(Gravity.CENTER);
            infoCard.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT));

            TextView textTitle = new TextView(context);
            textTitle.setText("Yuzen Uygulama");
            textTitle.setTextColor(Color.WHITE);
            textTitle.setTextSize(16);
            textTitle.setTypeface(Typeface.DEFAULT_BOLD);
            infoCard.addView(textTitle);

            TextView textDesc = new TextView(context);
            textDesc.setText("Uygulama yuzen pencerede baslatildi.\nDesteklenmiyorsa tam ekran acmak icin tiklayin.");
            textDesc.setTextColor(0xFF8E8E93);
            textDesc.setTextSize(11);
            textDesc.setGravity(Gravity.CENTER);
            textDesc.setPadding(0, dpToPx(8), 0, dpToPx(16));
            infoCard.addView(textDesc);

            TextView btnFullScreen = new TextView(context);
            btnFullScreen.setText("TAM EKRAN AC");
            btnFullScreen.setTextColor(Color.WHITE);
            btnFullScreen.setTextSize(12);
            btnFullScreen.setTypeface(Typeface.DEFAULT_BOLD);
            btnFullScreen.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setCornerRadius(dpToPx(8));
            btnBg.setColor(0xFF3D63FF); // Premium mavi buton
            btnFullScreen.setBackground(btnBg);
            
            btnFullScreen.setOnClickListener(v -> {
                hideOverlay();
                try {
                    Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            infoCard.addView(btnFullScreen);

            contentFrame.addView(infoCard);
        }

        container.addView(contentFrame);

        // Header Surukleme Tutamaci (Yuzen bar - Turkce karakter yok)
        View dragHandle = new View(context);
        FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(dpToPx(60), dpToPx(6));
        handleLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dpToPx(10);
        dragHandle.setLayoutParams(handleLp);
        
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dpToPx(3));
        handleBg.setColor(0x55FFFFFF); // Transparan gri tutamac
        dragHandle.setBackground(handleBg);
        container.addView(dragHandle);

        // Kapat Butonu (X)
        ImageButton closeButton = new ImageButton(context);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setBackground(null);
        closeButton.setColorFilter(Color.WHITE);
        closeButton.setPadding(0, 0, 0, 0);

        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dpToPx(32), dpToPx(32));
        closeParams.gravity = Gravity.TOP | Gravity.END;
        closeParams.topMargin = dpToPx(4);
        closeParams.rightMargin = dpToPx(4);
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> hideOverlay());

        container.addView(closeButton);

        return container;
    }

    /**
     * Yuzen pencere icinde WebView olusturur (Premium mobil site emulasyonu).
     */
    private WebView createWebView(String url) {
        WebView webView = new WebView(context);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Premium mobil surumu zorlamak icin User Agent'i mobil tarayici olarak ayarla (Turkce karakter yok)
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.loadUrl(url);
        return webView;
    }

    /**
     * Overlay gizle.
     */
    public void hideOverlay() {
        if (isShowing && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                // ignore
            }
            overlayView = null;
            isShowing = false;
        }
    }

    /**
     * Suruklenebilir yap.
     */
    private void makeDraggable(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // Ekranin sagindan (Gravity.END) baz aldigimiz icin suruklemeyi ona gore hesapla
                        params.x = initialX + (int) (initialTouchX - event.getRawX());
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(view, params);
                        } catch (Exception e) {
                            // ignore
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Overlay gosteriliyor mu?
     */
    public boolean isShowing() {
        return isShowing;
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
