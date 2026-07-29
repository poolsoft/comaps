package app.organicmaps.carlauncher;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Nullable;
import app.organicmaps.MwmActivity;
import app.organicmaps.R;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;

import android.widget.TextView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.graphics.Bitmap;
import app.organicmaps.carlauncher.music.MusicManager;

import app.organicmaps.carlauncher.ui.CarLayoutManager;
import app.organicmaps.carlauncher.ui.AppDockFragment;
import app.organicmaps.carlauncher.ui.WidgetPanelFragment;
import app.organicmaps.carlauncher.ui.PanelContentManager;
import app.organicmaps.carlauncher.performance.LauncherStartupProfile;

import androidx.fragment.app.Fragment;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.transition.Transition;
import android.view.View;
import android.view.Window;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.content.res.Configuration;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class CarLauncherActivity extends MwmActivity implements CarLauncherInterface, TelemetryManager.TelemetryListener, AppDockFragment.OnAppDockListener {
    
    private TelemetryManager telemetryManager;
    private CarLayoutManager layoutManager;
    private PanelContentManager panelContentManager;

    private androidx.constraintlayout.widget.ConstraintLayout rootLayout;
    private app.organicmaps.carlauncher.ui.ExactFrameLayout mapContainer;
    private android.widget.FrameLayout widgetPanel;
    private android.widget.ImageButton widgetHandle;
    private android.view.View appDock;
    private android.view.View appDrawerContainer;

    private android.view.View originalStreetFrame;
    private android.widget.TextView originalStreetText;
    private boolean isStreetFrameReparented = false;

    private boolean isWidgetPanelOpen = true;
    private boolean isDesktopMode = false;
    private boolean isTransitioning = false;
    private int layoutMode = 0; // 0 = Normal, 1 = No Widgets, 2 = Full Screen
    private int previousLayoutMode = -1;
    
    private static PanelContentManager.PanelContent lastPanelContent = null;
    private LauncherStartupProfile startupProfile;
    private boolean panelContentLoadedInProcess;
    private View.OnLayoutChangeListener configurationLayoutListener;
    private final Runnable configurationLayoutFallback =
            this::finishConfigurationLayoutUpdate;

    private final BroadcastReceiver desktopToggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String desktopAction = getPackageName() + ".ACTION_DESKTOP_TOGGLE";
            if (desktopAction.equals(intent.getAction())) {
                onDesktopModeToggle();
            }
        }
    };

    private final BroadcastReceiver notificationPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String permissionAction = getPackageName() + ".REQUEST_NOTIFICATION_PERMISSION";
            if (permissionAction.equals(intent.getAction())) {
                new android.app.AlertDialog.Builder(CarLauncherActivity.this)
                    .setTitle(R.string.notification_permission_required_title)
                    .setMessage(R.string.notification_permission_required_message)
                    .setPositiveButton(R.string.go_to_settings, (dialog, which) -> {
                        Intent settingsIntent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                        startActivity(settingsIntent);
                    })
                    .setNegativeButton(R.string.car_cancel, null)
                    .show();
            }
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        Log.i("CarLauncherLifecycle", "onNewIntent called. Action: " + (intent != null ? intent.getAction() : "null") 
            + ", isTaskRoot: " + isTaskRoot() + ", TaskId: " + getTaskId() 
            + ", Flags: " + (intent != null ? intent.getFlags() : 0));
        
    }

    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        startupProfile = new LauncherStartupProfile(this);
        super.onSafeCreate(savedInstanceState);

        // MwmActivity once kendi activity_map agacini ve tum controller'larini
        // normal sekilde kurar. Hazir harita View'ini Car Launcher kabuguna
        // tasiyarak core siniflarda layout hook'u gerektirmeyiz.
        android.view.ViewGroup contentRoot = findViewById(android.R.id.content);
        View initializedMapRoot =
                contentRoot != null && contentRoot.getChildCount() > 0
                        ? contentRoot.getChildAt(0) : null;
        if (initializedMapRoot != null) {
            contentRoot.removeView(initializedMapRoot);
        }
        setContentView(R.layout.activity_car_launcher);
        app.organicmaps.carlauncher.ui.ExactFrameLayout launcherMapContainer =
                findViewById(R.id.car_map_container);
        if (initializedMapRoot != null && launcherMapContainer != null) {
            launcherMapContainer.addView(initializedMapRoot,
                    new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        }
        
        Log.i("CarLauncherLifecycle", "onSafeCreate called. savedInstanceState=" + (savedInstanceState != null) 
            + ", isTaskRoot=" + isTaskRoot() + ", TaskId=" + getTaskId());
        
        CarLauncherSettings carPrefs = new CarLauncherSettings(this);
        String orientationMode = carPrefs.getScreenOrientation();
        int requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if ("portrait".equals(orientationMode)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if ("sensor".equals(orientationMode)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
        setRequestedOrientation(requestedOrientation);

        CarCrashLogger.init(this);
        
        telemetryManager = TelemetryManager.getInstance(this);

        rootLayout = findViewById(R.id.root_layout);
        mapContainer = findViewById(R.id.car_map_container);
        widgetPanel = findViewById(R.id.widget_panel);
        widgetHandle = findViewById(R.id.widget_handle);
        appDock = findViewById(R.id.app_dock);
        appDrawerContainer = findViewById(R.id.app_drawer_container);

        if (widgetPanel != null) {
            widgetPanel.setBackgroundResource(R.drawable.bg_panel_rounded);
            widgetPanel.setClipToOutline(true);
        }
        if (mapContainer != null) {
            mapContainer.setBackgroundResource(R.drawable.bg_card_rounded_dark);
            mapContainer.setClipToOutline(true);
        }

        panelContentManager = new PanelContentManager(getSupportFragmentManager(), R.id.widget_panel);
        panelContentManager.setOnFullScreenStateChangeListener(isFullScreen -> {
            if (layoutManager != null) {
                layoutManager.setContentFullScreen(isFullScreen);
            }
            applyWidgetPanelState();
            if (mapContainer != null) {
                mapContainer.setInterceptTouch(isFullScreen, () -> closeAppDrawer());
            }
        });

        layoutManager = new CarLayoutManager(this);
        applyWidgetPanelState();

        if (savedInstanceState == null) {
            if (appDock != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.app_dock, new AppDockFragment(), "app_dock")
                    .commitAllowingStateLoss();
            }
            if (widgetPanel != null && panelContentManager != null
                    && !startupProfile.isLowRam()) {
                PanelContentManager.PanelContent contentToRestore = 
                    (lastPanelContent != null) ? lastPanelContent : PanelContentManager.PanelContent.WIDGETS;
                panelContentManager.setContent(contentToRestore);
                panelContentLoadedInProcess = true;
            }
        }

        if (widgetHandle != null) {
            widgetHandle.bringToFront();
            widgetHandle.setImageResource(R.drawable.ic_more_vert);
            widgetHandle.setColorFilter(0xCCFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
            widgetHandle.setVisibility(View.VISIBLE);
            
            widgetHandle.setOnTouchListener(new View.OnTouchListener() {
                private float initialTouchX;
                private float initialTouchY;
                private float initialPanelPercent;
                private boolean portraitDrag;
                private boolean isDragging = false;
                private static final int TOUCH_SLOP = 10;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            portraitDrag = getResources().getConfiguration().orientation
                                    == Configuration.ORIENTATION_PORTRAIT;
                            CarLauncherSettings dragSettings =
                                    new CarLauncherSettings(CarLauncherActivity.this);
                            initialPanelPercent = portraitDrag
                                    ? dragSettings.getWidgetPanelHeightPortrait()
                                    : dragSettings.getWidgetPanelWidthPercent();
                            float renderedPercent =
                                    layoutManager.getRenderedSmallPanelFraction();
                            if (renderedPercent > 0f) {
                                initialPanelPercent = renderedPercent;
                            }
                            isDragging = false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - initialTouchX;
                            float dy = event.getRawY() - initialTouchY;
                            if (!isDragging && (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP)) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                updateCarWidgetPanelSize(dx, dy, initialPanelPercent);
                            }
                            break;
                    }
                    return true;
                }
            });
        }
        
        // Dinamik olarak haritanin veya sistemin status bar'i acmasini engelle (MwmActivity ile uyumlu izole cozum)
        // NOT: Burada setOnApplyWindowInsetsListener kullanmak UI thread'de sonsuz donguye sebep oluyordu.
        // Android zaten BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE ile bunu yonetir.
        // Gerekli baslangic ayarlari applyStatusBarVisibility() ile asagida ve onResume'da yapilmaktadir.
        
        applyStatusBarVisibility();
        if (rootLayout != null) {
            rootLayout.post(startupProfile::markUiReady);
        }
    }

    private void updateCarWidgetPanelSize(float deltaX, float deltaY,
                                          float initialPercent) {
        if (layoutManager == null) return;
        boolean isPortrait = getResources().getConfiguration().orientation 
                == Configuration.ORIENTATION_PORTRAIT;
        CarLauncherSettings carSettings = new CarLauncherSettings(this);
        if (isPortrait) {
            int availableHeight = layoutManager.getAvailablePanelHeight();
            if (availableHeight <= 0) return;
            boolean smallViewOnTop = layoutManager.isContentFullScreen();
            float direction = smallViewOnTop ? 1f : -1f;
            float rawPercent =
                    initialPercent + direction * (deltaY / availableHeight);
            float percent = Math.max(0.15f, Math.min(0.65f, rawPercent));
            carSettings.setWidgetPanelHeightPortrait(percent);
        } else {
            int availableWidth = layoutManager.getAvailablePanelWidth();
            if (availableWidth <= 0) return;
            String widgetPos = carSettings.getWidgetPanelPosition();
            boolean smallViewOnLeft = "left".equals(widgetPos);
            if (layoutManager.isContentFullScreen()) {
                smallViewOnLeft = !smallViewOnLeft;
            }
            float direction = smallViewOnLeft ? 1f : -1f;
            float rawPercent =
                    initialPercent + direction * (deltaX / availableWidth);
            float percent = Math.max(0.15f, Math.min(0.65f, rawPercent));
            carSettings.setWidgetPanelWidthPercent(percent);
        }
        applyWidgetPanelState(false);
    }

    private void applyWidgetPanelState() {
        applyWidgetPanelState(true);
    }

    private void applyWidgetPanelState(boolean animate) {
        if (layoutManager != null) {
            if (animate && rootLayout != null && rootLayout.isAttachedToWindow()) {
                isTransitioning = true;
                rootLayout.postDelayed(() -> isTransitioning = false, 500);

                AutoTransition transition = new AutoTransition();
                transition.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionStart(Transition transition) {}
                    @Override
                    public void onTransitionEnd(Transition transition) {
                        isTransitioning = false;
                    }
                    @Override
                    public void onTransitionCancel(Transition transition) {
                        isTransitioning = false;
                    }
                    @Override
                    public void onTransitionPause(Transition transition) {}
                    @Override
                    public void onTransitionResume(Transition transition) {}
                });
                TransitionManager.beginDelayedTransition(rootLayout, transition);
            } else {
                isTransitioning = false;
            }
            layoutManager.applyLayout(isWidgetPanelOpen, layoutMode);
        }
    }

    private void updateLayoutMode() {
        applyWidgetPanelState();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("app_dock");
        if (fragment instanceof AppDockFragment) {
            ((AppDockFragment) fragment).updateLayoutIcon(layoutMode);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i("CarLauncherLifecycle", "onStart called.");
    }

    @Override
    protected void onResume() {
        Log.i("CarLauncherLifecycle", "onResume called. isFinishing=" + isFinishing());
        // Cold start senaryosu: Core hazir olmadiginda SplashActivity'ye yonlendirme yapilir.
        // Eger core hazir degilse veya activity zaten kapaniyorsa (isFinishing), 
        // setRequestedOrientation gibi lifecycle transaction'ı tetikleyen cagrilar
        // MIUI/HyperOS'ta ClassCastException firlatarak app'in siyah ekranda (arka planda canli) kalmasina sebep olur.
        if (isFinishing() || !app.organicmaps.MwmApplication.from(this).getOrganicMaps().arePlatformAndCoreInitialized()) {
            Log.d("CarLauncherActivity", "onResume: core not initialized or finishing, skipping to prevent transaction crash");
            try {
                super.onResume(); // Zaten kapaniyor/SplashActivity'ye yonlendirildi. Super icindeki NPE'leri yutuyoruz.
            } catch (Exception e) {
                Log.w("CarLauncherActivity", "Ignored NPE from MwmActivity.onResume during finish: " + e.getMessage());
            }
            return;
        }
        super.onResume();
        // Kaydedilen ekran yonunu uygula (yatay, dikey veya otomatik sensor)
        CarLauncherSettings carPrefs = new CarLauncherSettings(this);
        String orientationMode = carPrefs.getScreenOrientation();
        int requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if ("portrait".equals(orientationMode)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if ("sensor".equals(orientationMode)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
        setRequestedOrientation(requestedOrientation);

        if (telemetryManager != null) telemetryManager.addListener(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(desktopToggleReceiver, 
            new IntentFilter(getPackageName() + ".ACTION_DESKTOP_TOGGLE"));
            
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationPermissionReceiver, 
            new IntentFilter(getPackageName() + ".REQUEST_NOTIFICATION_PERMISSION"));
        
        applyStatusBarVisibility();
        app.organicmaps.carlauncher.ui.CarFloatingButtonManager.getInstance(this).setAppInForeground(true);
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyStatusBarVisibility();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("CarLauncherLifecycle", "onPause called.");
        if (telemetryManager != null) telemetryManager.removeListener(this);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(desktopToggleReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationPermissionReceiver);

        app.organicmaps.carlauncher.ui.CarFloatingButtonManager.getInstance(this).setAppInForeground(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("CarLauncherLifecycle", "onStop called.");
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        if (loc != null) {
            updateFreeDrivingStreetDisplay(loc.streetName);
        }
    }

    private void tryReparentStreetFrame() {
        if (isStreetFrameReparented) return;

        final android.view.View streetFrame = findViewById(R.id.street_frame);
        final android.widget.TextView streetText = findViewById(R.id.street);
        final android.view.ViewGroup mapContainer = findViewById(R.id.car_map_container);

        Log.d("CoMapsStreetReparent", "tryReparentStreetFrame: streetFrame=" + (streetFrame != null) 
              + ", streetText=" + (streetText != null) + ", mapContainer=" + (mapContainer != null));

        if (streetFrame != null && streetText != null && mapContainer != null) {
            android.view.ViewGroup parent = (android.view.ViewGroup) streetFrame.getParent();
            Log.d("CoMapsStreetReparent", "tryReparentStreetFrame: parent=" + (parent != null));
            if (parent != null) {
                // Parent'indan sok
                parent.removeView(streetFrame);

                // ExactFrameLayout (FrameLayout) parametrelerini hazirla
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                );
                lp.gravity = android.view.Gravity.TOP;

                // Kenar bosluklarini (margin) set et
                int marginStart = getResources().getDimensionPixelSize(R.dimen.margin_base);
                int marginEnd = getResources().getDimensionPixelSize(R.dimen.margin_base);
                int marginTop = getResources().getDimensionPixelSize(R.dimen.margin_base);
                lp.setMargins(marginStart, marginTop, marginEnd, 0);

                // mapContainer icine ekle
                mapContainer.addView(streetFrame, lp);

                originalStreetFrame = streetFrame;
                originalStreetText = streetText;
                isStreetFrameReparented = true;

                Log.d("CoMapsStreetReparent", "tryReparentStreetFrame: Reparenting SUCCESSFUL!");
                // Ilk etapta gizli baslasin
                originalStreetFrame.setVisibility(android.view.View.GONE);
            }
        }
    }

    private void updateFreeDrivingStreetDisplay(String streetName) {
        // Reparent etmeyi dene
        tryReparentStreetFrame();

        Log.d("CoMapsStreetReparent", "updateFreeDrivingStreetDisplay: isStreetFrameReparented=" + isStreetFrameReparented 
              + ", inputStreetName='" + streetName + "'");

        if (!isStreetFrameReparented || originalStreetFrame == null || originalStreetText == null) {
            return;
        }

        // Rota takibi aktifse orijinal navigasyon paneli yonetsin, biz dokunmayalim
        if (app.organicmaps.sdk.routing.RoutingController.get().isNavigating()) {
            Log.d("CoMapsStreetReparent", "updateFreeDrivingStreetDisplay: Is navigating = true. Skipping.");
            return;
        }

        if (streetName != null && !streetName.isEmpty()) {
            originalStreetFrame.setVisibility(android.view.View.VISIBLE);
            originalStreetText.setText(streetName);
        } else {
            originalStreetFrame.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public void onAppDrawerOpen() {
        openAppDrawer();
    }

    @Override
    public void openAppDrawer() {
        if (panelContentManager != null && panelContentManager.getCurrentContent() == PanelContentManager.PanelContent.APP_DRAWER) {
            closeAppDrawer();
        } else {
            setPanelContent(PanelContentManager.PanelContent.APP_DRAWER);
        }
    }

    @Override
    public void closeAppDrawer() {
        setPanelContent(PanelContentManager.PanelContent.WIDGETS);
    }

    @Override
    public void openMusicPlayer() {
        setPanelContent(PanelContentManager.PanelContent.MUSIC);
    }

    @Override
    public void openWeatherDashboard() {
        setPanelContent(PanelContentManager.PanelContent.WEATHER);
    }

    @Override
    public void onLayoutModeToggle() {
        if (isTransitioning) return;
        layoutMode = (layoutMode == 0) ? 2 : 0;
        isWidgetPanelOpen = (layoutMode == 0);
        updateLayoutMode();
    }

    @Override
    public void onDesktopModeToggle() {
        if (isTransitioning) return;
        if (panelContentManager != null) {
            PanelContentManager.PanelContent current = panelContentManager.getCurrentContent();
            if (current != PanelContentManager.PanelContent.WIDGETS && 
                current != PanelContentManager.PanelContent.DESKTOP) {
                panelContentManager.setContent(isDesktopMode ? 
                    PanelContentManager.PanelContent.DESKTOP : 
                    PanelContentManager.PanelContent.WIDGETS);
                return;
            }
        }

        if (!isDesktopMode && layoutMode == 0) {
            layoutMode = 2;
            isWidgetPanelOpen = false;
            updateLayoutMode();
        } else if (!isDesktopMode && layoutMode == 2) {
            layoutMode = 0;
            isWidgetPanelOpen = true;
            updateLayoutMode();
            setDesktopMode(true);
        } else {
            setDesktopMode(false);
            layoutMode = 0;
            isWidgetPanelOpen = true;
            updateLayoutMode();
        }
    }

    public void setDesktopMode(boolean active) {
        if (this.isDesktopMode == active) return;
        this.isDesktopMode = active;

        if (active) {
            if (panelContentManager != null) {
                panelContentManager.setContent(PanelContentManager.PanelContent.DESKTOP);
            }
        } else {
            if (panelContentManager != null) {
                panelContentManager.setContent(PanelContentManager.PanelContent.WIDGETS);
            }
        }
        applyWidgetPanelState();
        
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("app_dock");
        if (fragment instanceof AppDockFragment) {
            ((AppDockFragment) fragment).updateDesktopModeState(active);
        }
    }

    public boolean isDesktopMode() {
        return isDesktopMode;
    }

    @Override
    public int getLayoutMode() {
        return layoutMode;
    }

    @Override
    public boolean isWidgetPanelOpen() {
        return isWidgetPanelOpen;
    }

    @Override
    public PanelContentManager getPanelContentManager() {
        return panelContentManager;
    }

    @Override
    public void applyNightDimMode() {
        View overlay = findViewById(R.id.night_dim_overlay);
        if (overlay == null) {
            return;
        }
        CarLauncherSettings settings = new CarLauncherSettings(this);
        String mode = settings.getNightDimMode();
        boolean dim;
        if ("night".equals(mode)) {
            dim = true;
        } else if ("day".equals(mode)) {
            dim = false;
        } else if ("auto".equals(mode)) {
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            dim = hour < 6 || hour >= 18;
        } else {
            dim = app.organicmaps.sdk.util.Config.UiTheme.isNight(
                    app.organicmaps.sdk.util.Config.UiTheme.getCurrent());
        }
        overlay.setVisibility(dim ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCarLauncherSettings() {
        if (panelContentManager != null) {
            if (panelContentManager.getCurrentContent() == PanelContentManager.PanelContent.SETTINGS) {
                setPanelContent(PanelContentManager.PanelContent.WIDGETS);
            } else {
                setPanelContent(PanelContentManager.PanelContent.SETTINGS);
            }
        }
    }

    @Override
    public View getMapView() { 
        return findViewById(R.id.map); 
    }

    @Override
    public void setPanelContent(PanelContentManager.PanelContent content) {
        if (isTransitioning) return;
        if (panelContentManager != null) {
            panelContentLoadedInProcess = true;
            if (content != PanelContentManager.PanelContent.DESKTOP && isDesktopMode) {
                isDesktopMode = false;
                applyWidgetPanelState();
                Fragment fragment = getSupportFragmentManager().findFragmentByTag("app_dock");
                if (fragment instanceof AppDockFragment) {
                    ((AppDockFragment) fragment).updateDesktopModeState(false);
                }
            }

            if (content != PanelContentManager.PanelContent.WIDGETS && content != PanelContentManager.PanelContent.DESKTOP) {
                if (layoutMode != 0) {
                    previousLayoutMode = layoutMode;
                    layoutMode = 0;
                    isWidgetPanelOpen = true;
                    updateLayoutMode();
                }
            } else {
                if (previousLayoutMode != -1) {
                    layoutMode = previousLayoutMode;
                    isWidgetPanelOpen = (layoutMode == 0);
                    previousLayoutMode = -1;
                    updateLayoutMode();
                }
            }

            panelContentManager.setContent(content);
            lastPanelContent = content;
        }
    }

    @Override
    public void onBackPressed() {
        if (panelContentManager != null && panelContentManager.getCurrentContent() != PanelContentManager.PanelContent.WIDGETS) {
            closeAppDrawer();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void openAntennaAlignmentFullscreen() {
        // Antenna hizalama ekrani henuz entegre edilmediyse bos kalabilir
    }

    @Override
    public void openAntennaAlignmentInPanel() {
        // Panel entegrasyonu henuz yapilmadiysa bos kalabilir
    }

    @Override
    public void applyStatusBarVisibility() {
        CarLauncherSettings settings = new CarLauncherSettings(this);
        boolean showStatusBar = settings.isStatusBarVisible();
        
        final Window window = getWindow();
        if (window == null) return;
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            if (showStatusBar) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            } else {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            }
        } else {
            final View decorView = window.getDecorView();
            androidx.core.view.WindowInsetsControllerCompat wic = androidx.core.view.WindowCompat.getInsetsController(window, decorView);
            if (wic != null) {
                if (showStatusBar) {
                    wic.show(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                } else {
                    wic.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                    wic.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyStatusBarVisibility();
        scheduleConfigurationLayoutUpdate(newConfig);
    }

    private void scheduleConfigurationLayoutUpdate(Configuration configuration) {
        if (rootLayout == null) {
            finishConfigurationLayoutUpdate();
            return;
        }
        rootLayout.removeCallbacks(configurationLayoutFallback);
        if (configurationLayoutListener != null) {
            rootLayout.removeOnLayoutChangeListener(configurationLayoutListener);
        }
        final boolean expectPortrait =
                configuration.orientation == Configuration.ORIENTATION_PORTRAIT;
        configurationLayoutListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            if (width > 0 && height > 0
                    && (height >= width) == expectPortrait) {
                finishConfigurationLayoutUpdate();
            }
        };
        rootLayout.addOnLayoutChangeListener(configurationLayoutListener);
        rootLayout.requestLayout();
        rootLayout.postDelayed(configurationLayoutFallback, 250L);
    }

    private void finishConfigurationLayoutUpdate() {
        if (rootLayout != null) {
            rootLayout.removeCallbacks(configurationLayoutFallback);
            if (configurationLayoutListener != null) {
                rootLayout.removeOnLayoutChangeListener(configurationLayoutListener);
                configurationLayoutListener = null;
            }
        }
        checkAndRefreshDockFragmentIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (rootLayout != null) {
            rootLayout.removeCallbacks(configurationLayoutFallback);
            if (configurationLayoutListener != null) {
                rootLayout.removeOnLayoutChangeListener(configurationLayoutListener);
                configurationLayoutListener = null;
            }
        }
        super.onDestroy();
    }

    @Override
    public void checkAndRefreshDockFragmentIfNeeded() {
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag("app_dock");
        if (fragment instanceof AppDockFragment
                && ((AppDockFragment) fragment).needsLayoutUpdate()) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.app_dock, new AppDockFragment(), "app_dock")
                .commitAllowingStateLoss();
        } else if (fragment instanceof AppDockFragment) {
            ((AppDockFragment) fragment).refreshLayout();
        }
        applyWidgetPanelState(false);
    }
}
