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

    private boolean isWidgetPanelOpen = true;
    private boolean isDesktopMode = false;
    private boolean isTransitioning = false;
    private int layoutMode = 0; // 0 = Normal, 1 = No Widgets, 2 = Full Screen
    private int previousLayoutMode = -1;
    
    private static PanelContentManager.PanelContent lastPanelContent = null;

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
    protected int getLayoutResId() {
        return R.layout.activity_car_launcher;
    }

    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        super.onSafeCreate(savedInstanceState);
        
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
            if (widgetPanel != null && panelContentManager != null) {
                PanelContentManager.PanelContent contentToRestore = 
                    (lastPanelContent != null) ? lastPanelContent : PanelContentManager.PanelContent.WIDGETS;
                panelContentManager.setContent(contentToRestore);
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
                private boolean isDragging = false;
                private static final int TOUCH_SLOP = 10;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            isDragging = false;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - initialTouchX;
                            float dy = event.getRawY() - initialTouchY;
                            if (!isDragging && (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP)) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                updateCarWidgetPanelSize(event.getRawX(), event.getRawY());
                            }
                            break;
                    }
                    return true;
                }
            });
        }
        
        // Dinamik olarak haritanin veya sistemin status bar'i acmasini engelle (MwmActivity ile uyumlu izole cozum)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                CarLauncherSettings s = new CarLauncherSettings(CarLauncherActivity.this);
                if (!s.isStatusBarVisible()) {
                    boolean isVisible = insets.isVisible(android.view.WindowInsets.Type.statusBars());
                    if (isVisible) {
                        applyStatusBarVisibility();
                    }
                }
                return v.onApplyWindowInsets(insets);
            });
        } else {
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
                CarLauncherSettings s = new CarLauncherSettings(CarLauncherActivity.this);
                if (!s.isStatusBarVisible()) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        applyStatusBarVisibility();
                    }
                }
            });
        }
        
        applyStatusBarVisibility();
    }

    private void updateCarWidgetPanelSize(float rawX, float rawY) {
        if (layoutManager == null) return;
        boolean isPortrait = getResources().getConfiguration().orientation 
                == Configuration.ORIENTATION_PORTRAIT;
        CarLauncherSettings carSettings = new CarLauncherSettings(this);
        if (isPortrait) {
            int screenHeight = getResources().getDisplayMetrics().heightPixels;
            if (screenHeight <= 0) return;
            float rawPercent = (screenHeight - rawY) / (float) screenHeight;
            float percent = Math.max(0.25f, Math.min(0.50f, rawPercent));
            carSettings.setWidgetPanelHeightPortrait(percent);
        } else {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            if (screenWidth <= 0) return;
            String widgetPos = carSettings.getWidgetPanelPosition();
            boolean isLeft = "left".equals(widgetPos);
            float rawPercent;
            if (isLeft) {
                rawPercent = rawX / (float) screenWidth;
            } else {
                rawPercent = (screenWidth - rawX) / (float) screenWidth;
            }
            float percent = Math.max(0.25f, Math.min(0.50f, rawPercent));
            carSettings.setWidgetPanelWidthPercent(percent);
        }
        applyWidgetPanelState();
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
    protected void onResume() {
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
        if (telemetryManager != null) telemetryManager.removeListener(this);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(desktopToggleReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationPermissionReceiver);

        app.organicmaps.carlauncher.ui.CarFloatingButtonManager.getInstance(this).setAppInForeground(false);
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        if (loc != null) {
            updateFreeDrivingStreetDisplay(loc.streetName);
        }
    }

    private void updateFreeDrivingStreetDisplay(String streetName) {
        // Rota takibi aktifse orijinal navigasyon paneli yonetsin, biz dokunmayalim
        if (app.organicmaps.sdk.routing.RoutingController.get().isNavigating()) {
            return;
        }

        final android.view.View navFrame = findViewById(R.id.navigation_frame);
        final android.view.View streetFrame = findViewById(R.id.street_frame);
        final android.widget.TextView streetText = findViewById(R.id.street);
        final android.view.View nextTurnContainer = findViewById(R.id.nav_next_turn_container);
        final android.view.View lanesView = findViewById(R.id.lanes);
        final android.view.View currentSpeedView = findViewById(R.id.nav_current_speed);
        final android.view.View speedLimitView = findViewById(R.id.nav_speed_limit);
        final android.view.View navBottomSheetCoordinator = findViewById(R.id.nav_bottom_sheet_coordinator);

        if (navFrame == null || streetFrame == null || streetText == null) {
            return;
        }

        if (streetName != null && !streetName.isEmpty()) {
            // Gereksiz tum navigasyon kontrollerini serbest suruste gizle
            if (nextTurnContainer != null) nextTurnContainer.setVisibility(View.GONE);
            if (lanesView != null) lanesView.setVisibility(View.GONE);
            if (currentSpeedView != null) currentSpeedView.setVisibility(View.GONE);
            if (speedLimitView != null) speedLimitView.setVisibility(View.GONE);
            if (navBottomSheetCoordinator != null) navBottomSheetCoordinator.setVisibility(View.GONE);

            // Sadece street barini ve ana nav_frame'i goster
            navFrame.setVisibility(View.VISIBLE);
            streetFrame.setVisibility(View.VISIBLE);
            streetText.setText(streetName);
        } else {
            // Sokak ismi yoksa gizle
            navFrame.setVisibility(View.GONE);
            streetFrame.setVisibility(View.GONE);
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
    }

    @Override
    public void checkAndRefreshDockFragmentIfNeeded() {
        applyWidgetPanelState(false); // Konum degisiminde animasyonu pas gec (Kacinilmaz anlik titremeleri ve TransitionManager buglarini onler)
        
        if (appDock != null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.app_dock, new AppDockFragment(), "app_dock")
                .commitAllowingStateLoss();
        }
    }
}