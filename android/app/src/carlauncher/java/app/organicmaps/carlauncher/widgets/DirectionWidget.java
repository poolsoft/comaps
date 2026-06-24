package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;

/**
 * Yon widget - Pusula yonunu gosterir.
 */
public class DirectionWidget extends BaseWidget implements TelemetryManager.TelemetryListener {

    private TextView labelText;
    private TextView directionText;
    private final MwmApplication app;

    public DirectionWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "compass", "Yon");
        this.app = app;
        this.order = 2;
    }

    @NonNull
    @Override
    public View createView() {
        // Modern Kart Yapisi
        FrameLayout rootFrame = new FrameLayout(context);
        // rootFrame.setPadding(16, 16, 16, 16); // Removed padding
        // rootFrame.setBackgroundResource(app.organicmaps.R.drawable.bg_widget_card);
        // // Removed frame

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        // params.setMargins(16, 16, 16, 16); // Removed margins
        container.setLayoutParams(params);

        // Ikon (Pusula)
        android.widget.ImageView iconView = new android.widget.ImageView(context);
        iconView.setImageResource(android.R.drawable.ic_menu_compass);
        iconView.setColorFilter(android.graphics.Color.RED); // Pusula kirmizi
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(64, 64);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = 8;
        container.addView(iconView, iconParams);

        // Label
        labelText = new TextView(context);
        labelText.setText("YON");
        labelText.setTextColor(android.graphics.Color.LTGRAY);
        labelText.setTextSize(12);
        labelText.setGravity(Gravity.CENTER);
        container.addView(labelText);

        // Yon Metni
        directionText = new TextView(context);
        directionText.setTextColor(android.graphics.Color.WHITE);
        directionText.setTextSize(24);
        directionText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        directionText.setGravity(Gravity.CENTER);
        directionText.setText("--");
        container.addView(directionText);

        rootFrame.addView(container);
        rootView = rootFrame;
        return rootView;
    }

    @Override
    public void update() {
        // Konum guncellemesi ile otomatik cagrilir
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        if (directionText != null) {
            int bearing = (int) loc.bearing;
            String direction = getDirectionString(bearing);
            String text = direction + " " + bearing + "Â°";
            directionText.post(() -> directionText.setText(text));
        }
    }

    /**
     * Bearing degerini yon stringine cevir.
     */
    private String getDirectionString(int bearing) {
        if (bearing >= 337.5 || bearing < 22.5)
            return "K"; // Kuzey
        if (bearing >= 22.5 && bearing < 67.5)
            return "KD"; // Kuzeydogu
        if (bearing >= 67.5 && bearing < 112.5)
            return "D"; // Dogu
        if (bearing >= 112.5 && bearing < 157.5)
            return "GD"; // Guneydogu
        if (bearing >= 157.5 && bearing < 202.5)
            return "G"; // Guney
        if (bearing >= 202.5 && bearing < 247.5)
            return "GB"; // Guneybati
        if (bearing >= 247.5 && bearing < 292.5)
            return "B"; // Bati
        if (bearing >= 292.5 && bearing < 337.5)
            return "KB"; // Kuzeybati
        return "";
    }

    @Override
    public void onStart() {
        super.onStart();
        if (app != null) {
            TelemetryManager.getInstance(app).addListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (app != null) {
            TelemetryManager.getInstance(app).removeListener(this);
        }
    }
}
