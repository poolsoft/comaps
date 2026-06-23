package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.organicmaps.R;
import net.osmand.Location;
import app.organicmaps.OsmandApplication;
import app.organicmaps.carlauncher.widgets.view.AnalogSpeedometerView;
import app.organicmaps.utils.FormattedValue;
import app.organicmaps.utils.OsmAndFormatter;
import net.osmand.binary.RouteDataObject;
import app.organicmaps.routing.RoutingHelper;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;

/**
 * Hız widget - S/M/L destegi.
 * S: Hız
 * M: Hız + Limit
 * L: Analog Hız
 */
public class SpeedWidget extends BaseWidget implements TelemetryManager.TelemetryListener {

    private final OsmandApplication app;
    
    // Containers
    private FrameLayout rootFrame;
    private View digitalView;
    private AnalogSpeedometerView analogView;

    // Digital UI
    private TextView speedText;
    private TextView unitText;
    private TextView limitText;
    private LinearLayout limitContainer;

    public SpeedWidget(@NonNull Context context, @NonNull OsmandApplication app) {
        super(context, "speed", "Hiz");
        this.app = app;
        this.order = 1;
    }

    @NonNull
    @Override
    public View createView() {
        rootFrame = new FrameLayout(context);
        rootFrame.setBackgroundResource(app.organicmaps.R.drawable.bg_widget_modern);
        rootFrame.setPadding(0, 0, 0, 0);

        // Default Layout
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

        // --- Limit (Left) ---
        limitContainer = new LinearLayout(context);
        limitContainer.setOrientation(LinearLayout.VERTICAL);
        limitContainer.setGravity(Gravity.CENTER);
        limitContainer.setVisibility(View.GONE);
        limitContainer.setPadding(0, 0, dpToPx(16), 0);

        limitText = new TextView(context);
        limitText.setBackgroundResource(app.organicmaps.R.drawable.bg_speed_limit);
        limitText.setTextColor(Color.BLACK);
        limitText.setTextSize(22);
        limitText.setTypeface(Typeface.DEFAULT_BOLD);
        limitText.setGravity(Gravity.CENTER);
        limitText.setText("--");
        
        int size = dpToPx(48);
        limitContainer.addView(limitText, new LinearLayout.LayoutParams(size, size));
        contentLayout.addView(limitContainer);

        // --- Speed (Right) ---
        LinearLayout speedContainer = new LinearLayout(context);
        speedContainer.setOrientation(LinearLayout.VERTICAL);
        speedContainer.setGravity(Gravity.CENTER);

        // Value + Unit Horizontal
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
        unitText.setPadding(dpToPx(4), 0, 0, dpToPx(12)); // Bottom align offset
        unitText.setText("km/h");

        valueUnitLayout.addView(speedText);
        valueUnitLayout.addView(unitText);
        
        speedContainer.addView(valueUnitLayout);
        contentLayout.addView(speedContainer);

        root.addView(contentLayout);
        digitalView = contentLayout;
        
        // Re-apply size logic if needed
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
                if (speedText != null) speedText.setTextSize(48); // Smaller for compact
            } else {
                // Visibility set by updateMaxSpeed
                 if (speedText != null) speedText.setTextSize(64); // Normal
            }
        }
    }

    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootFrame == null) return;

        if (newSize == WidgetSize.LARGE) {
            setupAnalogLayout(rootFrame);
        } else {
            // If switching from Analog -> Digital, rebuild
            if (digitalView == null) {
                setupDigitalLayout(rootFrame);
            }
            // Logic for S vs M (Limit visibility)
            applyDigitalVisibility();
        }
        
        // Trigger update to refresh data on new views
        TelemetryManager tm = TelemetryManager.getInstance(app);
        onTelemetryUpdated(tm.getLocationState(), tm.getNavigationState(), tm.getObdState());
    }

    @Override
    public void update() {}

    @Override
    public void onTelemetryUpdated(TelemetryManager.LocationState loc, TelemetryManager.NavigationState nav, TelemetryManager.ObdState obd) {
        float currentSpeed = loc.speedKmh / 3.6f; // m/s cinsinden lazim (OsmAndFormatter ve updateMaxSpeed icin)

        // 1. Digital View Update
        if (speedText != null) {
             FormattedValue formatted = OsmAndFormatter.getFormattedSpeedValue(currentSpeed, app);
             final String val = formatted.value;
             final String unit = formatted.unit;
             speedText.post(() -> {
                 speedText.setText(val);
                 if (unitText != null) unitText.setText(unit);
             });
        }

        // 2. Analog View Update
        if (analogView != null) {
             analogView.setSpeed(loc.speedKmh);
        }

        // 3. Limit Update (Orijinal Location objesi gerekir)
        if (loc.rawLocation != null) {
            updateMaxSpeed(loc.rawLocation, currentSpeed);
        }
    }
    
    private void updateMaxSpeed(Location location, float currentSpeed) {
        float maxSpeed = getMaxSpeed(location); // in m/s

        // Digital Logic
        if (limitText != null && limitContainer != null) {
             if (size == WidgetSize.SMALL) {
                 limitContainer.setVisibility(View.GONE);
             } else {
                 if (maxSpeed > 0 && maxSpeed != RouteDataObject.NONE_MAX_SPEED) {
                     limitContainer.setVisibility(View.VISIBLE);
                     FormattedValue formatted = OsmAndFormatter.getFormattedSpeedValue(maxSpeed, app);
                     
                     // Clean string ("50 km/h" -> "50")
                     String limitStr = formatted.value;
                     try { limitStr = limitStr.split(" ")[0]; } catch(Exception e){}
                     
                     limitText.setText(limitStr);
                     
                     // Color Logic (Gradual)
                     // maxSpeed is m/s. currentSpeed is m/s.
                     float diff = currentSpeed - maxSpeed;
                     // Tolerance: e.g. up to 10% or 5km/h
                     // Let's use simplified km/h based logic for visual check
                     float diffKmh = diff * 3.6f;
                     
                     int defaultColor = ContextCompat.getColor(context, R.color.cl_primary);
                     int warningColor = ContextCompat.getColor(context, R.color.cl_accent_orange);
                     int dangerColor = ContextCompat.getColor(context, R.color.cl_danger);
                     
                     if (diffKmh > 5) { // +5 km/h over
                         if (speedText != null) speedText.setTextColor(dangerColor);
                         
                         // Limit Box Danger (Red Circle)
                         android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                         gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                         gd.setColor(Color.WHITE);
                         gd.setStroke(dpToPx(6), dangerColor);
                         limitText.setBackground(gd);
                         limitText.setTextColor(Color.BLACK);
                         
                     } else if (diffKmh > 0) { // 0-5 km/h over
                         if (speedText != null) speedText.setTextColor(warningColor);
                         
                         // Limit Box Warning
                         limitText.setBackgroundResource(R.drawable.bg_speed_limit);
                         limitText.setTextColor(Color.RED); 
                     } else {
                         // Normal
                         if (speedText != null) speedText.setTextColor(defaultColor);
                         limitText.setBackgroundResource(R.drawable.bg_speed_limit);
                         limitText.setTextColor(Color.BLACK);
                     }

                 } else {
                     limitContainer.setVisibility(View.GONE);
                     if (speedText != null) speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
                 }
             }
        }
        
        // Analog Logic
        if (analogView != null) {
            if (maxSpeed > 0 && maxSpeed != RouteDataObject.NONE_MAX_SPEED) {
                analogView.setSpeedLimit(maxSpeed * 3.6f);
            } else {
                analogView.setSpeedLimit(0);
            }
        }
    }

    private float getMaxSpeed(Location location) {
        RoutingHelper routingHelper = app.getRoutingHelper();
        if (routingHelper == null) return 0;
        
        if ((!routingHelper.isFollowingMode()
                || routingHelper.isDeviatedFromRoute()
                || (routingHelper.getCurrentGPXRoute() != null && !routingHelper.isCurrentGPXRouteV2()))) {
            if (app.getLocationProvider() != null) {
                RouteDataObject routeObject = app.getLocationProvider().getLastKnownRouteSegment();
                if (routeObject != null) {
                    boolean direction = routeObject.bearingVsRouteDirection(location);
                    return routeObject.getMaximumSpeed(direction);
                }
            }
        } else {
            return routingHelper.getCurrentMaxSpeed();
        }
        return 0;
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
