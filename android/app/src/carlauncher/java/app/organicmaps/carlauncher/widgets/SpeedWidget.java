package app.organicmaps.carlauncher.widgets;

import app.organicmaps.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.organicmaps.R;
import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.widgets.view.AnalogSpeedometerView;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;
import app.organicmaps.sdk.settings.UnitLocale;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;

/**
 * Hiz gostergesi widget'i - S/M/L destegi sunar.
 * S: Sadece dijital hiz
 * M: Dijital hiz ve Hiz Limiti
 * L: Analog hiz gostergesi
 */
public class SpeedWidget extends BaseWidget implements TelemetryManager.TelemetryListener {

    private final MwmApplication app;
    
    // Containers
    private FrameLayout rootFrame;
    private View digitalView;
    private AnalogSpeedometerView analogView;

    // Digital UI
    private TextView speedText;
    private TextView unitText;
    private TextView limitText;
    private LinearLayout limitContainer;

    public SpeedWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "speed", context.getString(R.string.car_widget_speed));
        this.app = app;
        this.order = 1;
    }

    @NonNull
    @Override
    public View createView() {
        rootFrame = new FrameLayout(context);
        rootFrame.setBackgroundResource(R.drawable.bg_widget_modern);
        rootFrame.setPadding(0, 0, 0, 0);

        // Varsayilan olarak dijital arayuz kurulur
        setupDigitalLayout(rootFrame);

        rootView = rootFrame;
        return rootView;
    }

    private void setupDigitalLayout(ViewGroup root) {
        root.removeAllViews();
        analogView = null;

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.HORIZONTAL);
        contentLayout.setGravity(Gravity.CENTER);
        contentLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // --- Limit (Sol Taraf) ---
        limitContainer = new LinearLayout(context);
        limitContainer.setOrientation(LinearLayout.VERTICAL);
        limitContainer.setGravity(Gravity.CENTER);
        limitContainer.setVisibility(View.GONE);
        limitContainer.setPadding(0, 0, dpToPx(16), 0);

        limitText = new TextView(context);
        limitText.setBackgroundResource(R.drawable.bg_speed_limit);
        limitText.setTextColor(Color.BLACK);
        limitText.setTextSize(22);
        limitText.setTypeface(Typeface.DEFAULT_BOLD);
        limitText.setGravity(Gravity.CENTER);
        limitText.setText("--");
        
        int sizePx = dpToPx(48);
        limitContainer.addView(limitText, new LinearLayout.LayoutParams(sizePx, sizePx));
        contentLayout.addView(limitContainer);

        // --- Hiz (Sag Taraf) ---
        LinearLayout speedContainer = new LinearLayout(context);
        speedContainer.setOrientation(LinearLayout.VERTICAL);
        speedContainer.setGravity(Gravity.CENTER);

        // Hiz Degeri ve Birim Yatay Yerlesim
        LinearLayout valueUnitLayout = new LinearLayout(context);
        valueUnitLayout.setOrientation(LinearLayout.HORIZONTAL);
        valueUnitLayout.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        speedText = new TextView(context);
        speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
        speedText.setTextSize(64);
        speedText.setGravity(Gravity.CENTER);
        speedText.setText("--");
        speedText.setIncludeFontPadding(false);
        try {
            Typeface digitalFont = Typeface.createFromAsset(context.getAssets(), "fonts/Cross Boxed.ttf");
            speedText.setTypeface(digitalFont);
        } catch (Exception e) {
            speedText.setTypeface(Typeface.DEFAULT_BOLD);
        }
        
        unitText = new TextView(context);
        unitText.setTextSize(14);
        unitText.setTextColor(ContextCompat.getColor(context, R.color.cl_text_secondary));
        unitText.setPadding(dpToPx(4), 0, 0, dpToPx(12)); 
        unitText.setText("km/h");

        valueUnitLayout.addView(speedText);
        valueUnitLayout.addView(unitText);
        
        speedContainer.addView(valueUnitLayout);
        contentLayout.addView(speedContainer);

        root.addView(contentLayout);
        digitalView = contentLayout;
        
        applyDigitalVisibility();
    }

    private void setupAnalogLayout(ViewGroup root) {
        root.removeAllViews();
        digitalView = null;
        limitContainer = null;
        limitText = null;
        speedText = null;

        analogView = new AnalogSpeedometerView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int margin = dpToPx(8);
        params.setMargins(margin, margin, margin, margin);
        analogView.setLayoutParams(params);

        root.addView(analogView);
    }

    private void applyDigitalVisibility() {
        if (limitContainer != null) {
            if (size == WidgetSize.SMALL) {
                limitContainer.setVisibility(View.GONE);
                if (speedText != null) speedText.setTextSize(48); 
            } else {
                 if (speedText != null) speedText.setTextSize(64); 
            }
        }
    }

    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootFrame == null) return;

        if (newSize == WidgetSize.LARGE) {
            setupAnalogLayout(rootFrame);
        } else {
            if (digitalView == null) {
                setupDigitalLayout(rootFrame);
            }
            applyDigitalVisibility();
        }
        
        // Verileri aninda guncelle
        TelemetryManager tm = TelemetryManager.getInstance(app);
        onTelemetryUpdated(tm.getLocationState(), tm.getNavigationState(), tm.getObdState());
    }

    @Override
    public void update() {}

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        // 1. Hiz Birimi ve Degeri Hesaplama (Metric vs Imperial)
        boolean isImperial = UnitLocale.getUnits() == UnitLocale.UNITS_FOOT;
        final float speedVal;
        final String unitStr;
        
        if (isImperial) {
            speedVal = (loc.speedKmh / 3.6f) * 2.23694f; // m/s -> mph
            unitStr = "mph";
        } else {
            speedVal = loc.speedKmh; // km/h
            unitStr = "km/h";
        }

        final int displaySpeed = Math.round(speedVal);

        // 2. Dijital Arayuz Guncelleme
        if (speedText != null) {
            speedText.post(() -> {
                if (speedText != null) {
                    speedText.setText(displaySpeed > 0 ? String.valueOf(displaySpeed) : "0");
                }
                if (unitText != null) {
                    unitText.setText(unitStr);
                }
            });
        }

        // 3. Analog Arayuz Guncelleme
        if (analogView != null) {
            analogView.post(() -> {
                if (analogView != null) {
                    analogView.setSpeed(speedVal);
                }
            });
        }

        // 4. Hiz Limiti Guncelleme (Navigation Info uzerinden)
        updateMaxSpeed(speedVal, isImperial);
    }
    
    private void updateMaxSpeed(float currentSpeedVal, boolean isImperial) {
        RoutingController routingController = RoutingController.get();
        double limitMps = -1;
        
        if (routingController != null && routingController.isNavigating()) {
            RoutingInfo info = routingController.getCachedRoutingInfo();
            if (info != null) {
                limitMps = info.speedLimitMps;
            }
        }

        final double finalLimitMps = limitMps;

        // Dijital Limit Guncelleme
        if (limitText != null && limitContainer != null) {
            if (size == WidgetSize.SMALL) {
                limitContainer.setVisibility(View.GONE);
            } else {
                if (finalLimitMps > 0) {
                    double limitVal = isImperial ? finalLimitMps * 2.23694 : finalLimitMps * 3.6;
                    final int displayLimit = (int) Math.round(limitVal);
                    
                    limitText.post(() -> {
                        if (limitContainer != null) limitContainer.setVisibility(View.VISIBLE);
                        if (limitText != null) limitText.setText(String.valueOf(displayLimit));
                        
                        // Limit Asimi Kontrolu
                        float diff = currentSpeedVal - displayLimit;
                        int defaultColor = ContextCompat.getColor(context, R.color.cl_primary);
                        int warningColor = ContextCompat.getColor(context, R.color.cl_accent_orange);
                        int dangerColor = ContextCompat.getColor(context, R.color.cl_danger);
                        
                        if (diff > 5) { // 5 birim uzeri asim (kirmizi)
                            if (speedText != null) speedText.setTextColor(dangerColor);
                            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                            gd.setColor(Color.WHITE);
                            gd.setStroke(dpToPx(6), dangerColor);
                            limitText.setBackground(gd);
                            limitText.setTextColor(Color.BLACK);
                        } else if (diff > 0) { // Tolerans dahilinde asim (turuncu)
                            if (speedText != null) speedText.setTextColor(warningColor);
                            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                            gd.setColor(Color.WHITE);
                            gd.setStroke(dpToPx(6), warningColor);
                            limitText.setBackground(gd);
                            limitText.setTextColor(Color.BLACK);
                        } else {
                            // Normal durum (mavi/varsayilan)
                            if (speedText != null) speedText.setTextColor(defaultColor);
                            limitText.setBackgroundResource(R.drawable.bg_speed_limit);
                            limitText.setTextColor(Color.BLACK);
                        }
                    });
                } else {
                    limitText.post(() -> {
                        if (limitContainer != null) limitContainer.setVisibility(View.GONE);
                        if (speedText != null) speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
                    });
                }
            }
        }
        
        // Analog Limit Guncelleme
        if (analogView != null) {
            analogView.post(() -> {
                if (analogView != null) {
                    if (finalLimitMps > 0) {
                        double limitVal = isImperial ? finalLimitMps * 2.23694 : finalLimitMps * 3.6;
                        analogView.setSpeedLimit((float) limitVal);
                    } else {
                        analogView.setSpeedLimit(0);
                    }
                }
            });
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        if (app != null) TelemetryManager.getInstance(app).addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (app != null) TelemetryManager.getInstance(app).removeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        rootFrame = null;
        digitalView = null;
        analogView = null;
        speedText = null;
        unitText = null;
        limitText = null;
        limitContainer = null;
    }
}
