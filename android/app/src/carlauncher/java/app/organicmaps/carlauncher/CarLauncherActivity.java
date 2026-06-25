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

public class CarLauncherActivity extends MwmActivity implements CarLauncherInterface, TelemetryManager.TelemetryListener {
    
    private TelemetryManager telemetryManager;
    private CarLayoutManager layoutManager;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_car_launcher;
    }

    @Override
    protected void onSafeCreate(@Nullable Bundle savedInstanceState) {
        super.onSafeCreate(savedInstanceState);
        // setContentView(R.layout.activity_car_launcher); artik gerek yok cunku getLayoutResId uzerinden MwmActivity yukluyor.

        telemetryManager = TelemetryManager.getInstance(this);

        layoutManager = new CarLayoutManager(this);
        layoutManager.applyLayout(true, 1);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.app_dock, new AppDockFragment())
                .replace(R.id.widget_panel, new WidgetPanelFragment())
                .commitAllowingStateLoss();
        }

        // Orijinal MwmActivity arayuz bilesenlerini (arama, favoriler, menu vs) Car Launcher'da gizle (Turkce karakter yok)
        android.view.View mapButtons = findViewById(R.id.map_buttons);
        if (mapButtons != null) mapButtons.setVisibility(android.view.View.GONE);

        android.view.View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setVisibility(android.view.View.GONE);

        android.view.View menu = findViewById(R.id.menu_frame); // Eger ID menu_frame ise (activity_map'te menu id'li include)
        if (menu != null) menu.setVisibility(android.view.View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (telemetryManager != null) telemetryManager.addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (telemetryManager != null) telemetryManager.removeListener(this);
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        // Log.d("CarLauncherTelemetry", "Speed: " + loc.speedKmh + " km/h | Nav: " + nav.distanceStr);
    }

    @Override
    public void openAppDrawer() {}

    @Override
    public void closeAppDrawer() {}

    @Override
    public void openMusicPlayer() {}

    @Override
    public void openWeatherDashboard() {}

    @Override
    public void onLayoutModeToggle() {}

    @Override
    public void onDesktopModeToggle() {}

    @Override
    public void openCarLauncherSettings() {}

    @Override
    public android.view.View getMapView() { return null; }

    @Override
    public void setPanelContent(app.organicmaps.carlauncher.ui.PanelContentManager.PanelContent content) {}

    @Override
    public void openAntennaAlignmentFullscreen() {}

    @Override
    public void openAntennaAlignmentInPanel() {}
}
