package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;
import app.organicmaps.R;

/**
 * Navigasyon widget - Sonraki donus ve rota bilgisi.
 * TelemetryManager uzerinden gelen bilgileri gosterir.
 */
public class NavigationWidget extends BaseWidget implements TelemetryManager.TelemetryListener {

    private ImageView turnIconView;
    private TextView distanceText;
    private TextView instructionText;
    private TextView etaText;

    private final MwmApplication app;

    public NavigationWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "navigation", "Navigasyon");
        this.app = app;
        this.order = 3; // Hiz/yon'den sonra
    }

    @NonNull
    @Override
    public View createView() {
        // Modern Kart Yapisi
        FrameLayout rootFrame = new FrameLayout(context);
        rootFrame.setBackgroundResource(R.drawable.bg_widget_modern);
        rootFrame.setElevation(4f);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(12, 12, 12, 12);
        container.setLayoutParams(params);

        // Label
        TextView label = new TextView(context);
        label.setText("NAVIGASYON");
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 0, 0, 8);
        container.addView(label);

        // Sonraki donus mesafesi
        distanceText = new TextView(context);
        distanceText.setTextColor(Color.WHITE);
        distanceText.setTextSize(32);
        distanceText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        distanceText.setGravity(Gravity.CENTER);
        distanceText.setText("--");
        container.addView(distanceText);

        // Donus ikonu
        turnIconView = new ImageView(context);
        turnIconView.setLayoutParams(new LinearLayout.LayoutParams(96, 96));
        turnIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        turnIconView.setColorFilter(Color.CYAN); // Neon mavi
        turnIconView.setPadding(0, 8, 0, 8);
        container.addView(turnIconView);

        // Donus talimati
        instructionText = new TextView(context);
        instructionText.setTextColor(Color.WHITE);
        instructionText.setTextSize(16);
        instructionText.setGravity(Gravity.CENTER);
        instructionText.setText("");
        instructionText.setMaxLines(2);
        instructionText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        instructionText.setPadding(0, 0, 0, 12);
        container.addView(instructionText);

        // Kalan sure ve mesafe
        etaText = new TextView(context);
        etaText.setTextColor(Color.parseColor("#AAAAAA"));
        etaText.setTextSize(12);
        etaText.setGravity(Gravity.CENTER);
        etaText.setText("");
        container.addView(etaText);

        rootFrame.addView(container);
        rootView = rootFrame;

        update();
        return rootView;
    }
    @Override
    public void update() {
        // UI Guncellemesi artik onTelemetryUpdated uzerinden geliyor
    }

    @Override
    public void onStart() {
        super.onStart();
        TelemetryManager.getInstance(app).addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        TelemetryManager.getInstance(app).removeListener(this);
    }

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        if (rootView == null) return;
        
        if (nav.isActive) {
            if (distanceText != null) distanceText.post(() -> distanceText.setText(nav.distanceStr));
            if (instructionText != null) instructionText.post(() -> instructionText.setText(nav.instructionStr));
            if (etaText != null) etaText.post(() -> etaText.setText(nav.etaStr));
            if (turnIconView != null) {
                turnIconView.post(() -> {
                    if (nav.turnIconRes != 0) turnIconView.setImageResource(nav.turnIconRes);
                    turnIconView.setVisibility(View.VISIBLE);
                });
            }
        } else {
            showNoNavigation();
        }
    }

    private void showNoNavigation() {
        if (distanceText != null) distanceText.post(() -> distanceText.setText("--"));
        if (instructionText != null) instructionText.post(() -> instructionText.setText("Navigasyon yok"));
        if (etaText != null) etaText.post(() -> etaText.setText(""));
        if (turnIconView != null) turnIconView.post(() -> turnIconView.setVisibility(View.GONE));
    }
}
