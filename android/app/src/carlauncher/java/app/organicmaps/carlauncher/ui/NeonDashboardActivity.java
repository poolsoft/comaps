package app.organicmaps.carlauncher.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;
import app.organicmaps.carlauncher.widgets.view.FuturisticSpeedometerView;
import app.organicmaps.R;

public class NeonDashboardActivity extends Activity implements TelemetryManager.TelemetryListener {

    private FuturisticSpeedometerView speedometerView;
    private TelemetryManager telemetryManager;
    private MwmApplication app;

    // Lokasyon (Sol/Sag) panelleri silindi

    // Navigasyon (Ust)
    private LinearLayout navContainer;
    private ImageView navIcon;
    private TextView navDistance;
    private TextView navInstruction;
    private TextView navEta;

    // OBD (Alt)
    private LinearLayout obdContainer;
    private TextView obdRpm;
    private TextView obdTemp;
    private TextView obdVolt;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_neon_dashboard);

        speedometerView = findViewById(R.id.futuristic_speed);
        ImageButton btnClose = findViewById(R.id.btn_close);
        
        // Metric textviews removed

        navContainer = findViewById(R.id.nav_container);
        navIcon = findViewById(R.id.nav_icon);
        navDistance = findViewById(R.id.nav_distance);
        navInstruction = findViewById(R.id.nav_instruction);
        navEta = findViewById(R.id.nav_eta);

        obdContainer = findViewById(R.id.obd_container);
        obdRpm = findViewById(R.id.obd_rpm);
        obdTemp = findViewById(R.id.obd_temp);
        obdVolt = findViewById(R.id.obd_volt);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        app = (MwmApplication) getApplication();
        telemetryManager = TelemetryManager.getInstance(app);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (telemetryManager != null) {
            telemetryManager.addListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (telemetryManager != null) {
            telemetryManager.removeListener(this);
        }
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, 
                                   TelemetryManager.NavigationState nav, 
                                   TelemetryManager.ObdState obd) {
        
        // 1. HIZ
        if (speedometerView != null) {
            speedometerView.setSpeed(loc.speedKmh);
        }

        // 2. NAVIGASYON
        if (nav.isActive) {
            if (navContainer != null) navContainer.setVisibility(View.VISIBLE);
            if (navIcon != null && nav.turnIconRes != 0) navIcon.setImageResource(nav.turnIconRes);
            if (navDistance != null) navDistance.setText(nav.distanceStr);
            if (navInstruction != null) navInstruction.setText(nav.instructionStr);
            if (navEta != null) navEta.setText(nav.etaStr);
            
        } else {
            if (navContainer != null) navContainer.setVisibility(View.GONE);
        }

        // 3. OBD
        if (obdContainer != null) obdContainer.setVisibility(View.VISIBLE);
        if (obdRpm != null) obdRpm.setText(obd.rpm);
        if (obdTemp != null) obdTemp.setText(obd.temp);
        if (obdVolt != null) obdVolt.setText(obd.volt);
    }

}
