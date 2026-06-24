package app.organicmaps.carlauncher.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import app.organicmaps.R;

public class ObdDashboardActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_obd_dashboard);
        
        // Fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                
        // Back Button Logic (if we add one, or use system back)
        
        setupGauges();
    }
    
    private void setupGauges() {
        app.organicmaps.carlauncher.widgets.view.GaugeView rpm = findViewById(app.organicmaps.R.id.gauge_rpm);
        app.organicmaps.carlauncher.widgets.view.GaugeView speed = findViewById(app.organicmaps.R.id.gauge_speed);
        app.organicmaps.carlauncher.widgets.view.GaugeView temp = findViewById(app.organicmaps.R.id.gauge_temp);
        
        if (rpm != null) rpm.setConfig(0, 8000, "RPM");
        if (speed != null) speed.setConfig(0, 240, "km/h");
        if (temp != null) temp.setConfig(0, 120, "Temp Â°C");
        
        // Demo Loop (since accessing Plugin directly here might need context/init check)
        // Ideally we should use VehicleMetricsPlugin.getInstance() if available.
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(new Runnable() {
            int tick = 0;
            @Override
            public void run() {
                // Mock Sine Wave for Demo
                if (rpm != null) rpm.setValue((float) (4000 + 3000 * Math.sin(tick * 0.05)));
                if (speed != null) speed.setValue((float) (100 + 80 * Math.sin(tick * 0.02)));
                if (temp != null) temp.setValue((float) (90 + 5 * Math.sin(tick * 0.01)));
                
                tick++;
                handler.postDelayed(this, 50);
            }
        });
    }
}
