package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.osmand.Location;
import app.organicmaps.OsmAndLocationProvider;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.view.AnalogSpeedometerView;
import app.organicmaps.carlauncher.widgets.weather.WeatherManager;
import app.organicmaps.utils.FormattedValue;
import app.organicmaps.utils.OsmAndFormatter;
import net.osmand.binary.RouteDataObject;
import app.organicmaps.routing.RoutingHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * BirleÅŸik Dashboard Widget.
 * Saat + HÄ±z + Limit tek widget'ta.
 *
 * Adaptive Layout:
 * - SMALL (< 200dp genislik): Sadece hÄ±z (bÃ¼yÃ¼k)
 * - MEDIUM (200-350dp): Saat Ã¼st, hÄ±z orta, limit saÄŸ
 * - LARGE (> 350dp): Analog saat + hÄ±z overlay + limit + hava durumu
 */
public class CombinedWidget extends BaseWidget
        implements OsmAndLocationProvider.OsmAndLocationListener,
                   WeatherManager.WeatherListener {

    private final MwmApplication app;
    private FrameLayout rootFrame;

    // UI Components
    private TextView clockText;
    private TextView dateText;
    private TextView speedText;
    private TextView unitText;
    private TextView limitText;
    private LinearLayout limitContainer;
    private AnalogSpeedometerView analogView;
    private LinearLayout weatherContainer;
    private ImageView weatherIcon;
    private TextView tempText;

    // Helpers
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateFormat;
    private Runnable updateRunnable;
    private WeatherManager weatherManager;
    private WeatherManager.WeatherData lastWeatherData;

    public CombinedWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "combined", "Dashboard");
        this.app = app;
        this.order = 0;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        this.weatherManager = WeatherManager.getInstance(context);
    }

    @NonNull
    @Override
    public View createView() {
        rootFrame = new FrameLayout(context);
        rootFrame.setBackgroundResource(R.drawable.bg_widget_modern);
        rootFrame.setPadding(0, 0, 0, 0);

        // Varsayilan layout (MEDIUM)
        setupMediumLayout();

        rootView = rootFrame;
        return rootView;
    }

    // ========== ADAPTIVE LAYOUTS ==========

    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootFrame == null) return;
        rebuildLayout(newSize);
    }

    private void rebuildLayout(WidgetSize newSize) {
        rootFrame.removeAllViews();
        analogView = null;
        clockText = null;
        speedText = null;
        limitText = null;
        limitContainer = null;
        weatherContainer = null;

        switch (newSize) {
            case SMALL:
                setupSmallLayout();
                break;
            case LARGE:
                setupLargeLayout();
                break;
            default:
                setupMediumLayout();
                break;
        }
        update();
    }

    /**
     * SMALL: Sadece hÄ±z, bÃ¼yÃ¼k font
     */
    private void setupSmallLayout() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        speedText = new TextView(context);
        speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
        speedText.setTextSize(56);
        speedText.setGravity(Gravity.CENTER);
        speedText.setText("--");
        speedText.setIncludeFontPadding(false);
        try {
            Typeface font = Typeface.createFromAsset(context.getAssets(), "fonts/Cross Boxed.ttf");
            speedText.setTypeface(font);
        } catch (Exception e) {
            speedText.setTypeface(Typeface.DEFAULT_BOLD);
        }
        root.addView(speedText);

        unitText = new TextView(context);
        unitText.setTextSize(14);
        unitText.setTextColor(ContextCompat.getColor(context, R.color.cl_text_secondary));
        unitText.setGravity(Gravity.CENTER);
        unitText.setText("km/h");
        root.addView(unitText);

        rootFrame.addView(root);
    }

    /**
     * MEDIUM: Saat Ã¼st, hÄ±z orta, limit saÄŸ
     */
    private void setupMediumLayout() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // SATIR 1: Saat
        clockText = new TextView(context);
        clockText.setTextColor(Color.WHITE);
        clockText.setTextSize(36);
        clockText.setGravity(Gravity.CENTER);
        clockText.setIncludeFontPadding(false);
        try {
            Typeface font = Typeface.createFromAsset(context.getAssets(), "fonts/curved-seven-segment.ttf");
            clockText.setTypeface(font);
        } catch (Exception e) {
            clockText.setTypeface(Typeface.DEFAULT_BOLD);
        }
        root.addView(clockText);

        dateText = new TextView(context);
        dateText.setTextColor(Color.LTGRAY);
        dateText.setTextSize(12);
        dateText.setGravity(Gravity.CENTER);
        root.addView(dateText);

        // SATIR 2: HÄ±z + Limit (Horizontal)
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);

        // Limit (sol)
        limitContainer = new LinearLayout(context);
        limitContainer.setOrientation(LinearLayout.VERTICAL);
        limitContainer.setGravity(Gravity.CENTER);
        limitContainer.setVisibility(View.GONE);
        limitContainer.setPadding(0, 0, dpToPx(12), 0);

        limitText = new TextView(context);
        limitText.setBackgroundResource(R.drawable.bg_speed_limit);
        limitText.setTextColor(Color.BLACK);
        limitText.setTextSize(18);
        limitText.setTypeface(Typeface.DEFAULT_BOLD);
        limitText.setGravity(Gravity.CENTER);
        limitText.setText("--");
        int ls = dpToPx(40);
        limitContainer.addView(limitText, new LinearLayout.LayoutParams(ls, ls));
        row2.addView(limitContainer);

        // HÄ±z (saÄŸ)
        LinearLayout speedCol = new LinearLayout(context);
        speedCol.setOrientation(LinearLayout.HORIZONTAL);
        speedCol.setGravity(Gravity.BOTTOM | Gravity.CENTER);

        speedText = new TextView(context);
        speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
        speedText.setTextSize(48);
        speedText.setGravity(Gravity.CENTER);
        speedText.setText("--");
        speedText.setIncludeFontPadding(false);
        try {
            Typeface font = Typeface.createFromAsset(context.getAssets(), "fonts/Cross Boxed.ttf");
            speedText.setTypeface(font);
        } catch (Exception e) {
            speedText.setTypeface(Typeface.DEFAULT_BOLD);
        }
        speedCol.addView(speedText);

        unitText = new TextView(context);
        unitText.setTextSize(12);
        unitText.setTextColor(ContextCompat.getColor(context, R.color.cl_text_secondary));
        unitText.setPadding(dpToPx(2), 0, 0, dpToPx(8));
        unitText.setText("km/h");
        speedCol.addView(unitText);

        row2.addView(speedCol);
        root.addView(row2);

        // SATIR 3: Hava durumu (opsiyonel)
        weatherContainer = new LinearLayout(context);
        weatherContainer.setOrientation(LinearLayout.HORIZONTAL);
        weatherContainer.setGravity(Gravity.CENTER);
        weatherContainer.setVisibility(View.GONE);

        weatherIcon = new ImageView(context);
        int ws = dpToPx(24);
        weatherIcon.setLayoutParams(new LinearLayout.LayoutParams(ws, ws));
        weatherContainer.addView(weatherIcon);

        tempText = new TextView(context);
        tempText.setTextColor(Color.WHITE);
        tempText.setTextSize(14);
        tempText.setPadding(dpToPx(4), 0, 0, 0);
        weatherContainer.addView(tempText);

        root.addView(weatherContainer);
        rootFrame.addView(root);
    }

    /**
     * LARGE: Analog saat + hÄ±z overlay + limit
     */
    private void setupLargeLayout() {
        analogView = new AnalogSpeedometerView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int margin = dpToPx(4);
        params.setMargins(margin, margin, margin, margin);
        analogView.setLayoutParams(params);
        rootFrame.addView(analogView);

        // Date overlay (alt)
        dateText = new TextView(context);
        dateText.setTextColor(Color.WHITE);
        dateText.setTextSize(12);
        dateText.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dp.setMargins(0, 0, 0, dpToPx(8));
        dateText.setLayoutParams(dp);
        rootFrame.addView(dateText);
    }

    // ========== UPDATE ==========

    @Override
    public void update() {
        if (clockText != null) {
            clockText.setText(timeFormat.format(new Date()));
        }
        if (dateText != null && analogView == null) {
            dateText.setText(dateFormat.format(new Date()));
        } else if (dateText != null) {
            dateText.setText(dateFormat.format(new Date()));
        }
    }

    @Override
    public void updateLocation(Location location) {
        if (location == null) return;
        float currentSpeed = location.hasSpeed() ? location.getSpeed() : 0;

        // Speed text
        if (speedText != null) {
            FormattedValue fv = OsmAndFormatter.getFormattedSpeedValue(currentSpeed, app);
            speedText.setText(fv.value);
            if (unitText != null) unitText.setText(fv.unit);
        }

        // Analog
        if (analogView != null) {
            analogView.setSpeed(currentSpeed * 3.6f);
        }

        // Limit
        updateLimit(location, currentSpeed);
    }

    private void updateLimit(Location location, float currentSpeed) {
        float maxSpeed = getMaxSpeed(location);
        if (limitText == null || limitContainer == null) return;

        if (maxSpeed > 0 && maxSpeed != RouteDataObject.NONE_MAX_SPEED) {
            limitContainer.setVisibility(View.VISIBLE);
            FormattedValue fv = OsmAndFormatter.getFormattedSpeedValue(maxSpeed, app);
            String limitStr = fv.value.split(" ")[0];
            limitText.setText(limitStr);

            float diffKmh = (currentSpeed - maxSpeed) * 3.6f;
            int defaultColor = ContextCompat.getColor(context, R.color.cl_primary);
            int warnColor = ContextCompat.getColor(context, R.color.cl_accent_orange);
            int dangerColor = ContextCompat.getColor(context, R.color.cl_danger);

            if (diffKmh > 5) {
                if (speedText != null) speedText.setTextColor(dangerColor);
                android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                gd.setColor(Color.WHITE);
                gd.setStroke(dpToPx(4), dangerColor);
                limitText.setBackground(gd);
                limitText.setTextColor(Color.BLACK);
            } else if (diffKmh > 0) {
                if (speedText != null) speedText.setTextColor(warnColor);
                limitText.setBackgroundResource(R.drawable.bg_speed_limit);
                limitText.setTextColor(Color.RED);
            } else {
                if (speedText != null) speedText.setTextColor(defaultColor);
                limitText.setBackgroundResource(R.drawable.bg_speed_limit);
                limitText.setTextColor(Color.BLACK);
            }
        } else {
            limitContainer.setVisibility(View.GONE);
            if (speedText != null) speedText.setTextColor(ContextCompat.getColor(context, R.color.cl_primary));
        }

        if (analogView != null) {
            analogView.setSpeedLimit(maxSpeed > 0 ? maxSpeed * 3.6f : 0);
        }
    }

    private float getMaxSpeed(Location location) {
        RoutingHelper routingHelper = app.getRoutingHelper();
        if (routingHelper == null) return 0;
        if ((!routingHelper.isFollowingMode() || routingHelper.isDeviatedFromRoute()
                || (routingHelper.getCurrentGPXRoute() != null && !routingHelper.isCurrentGPXRouteV2()))) {
            if (app.getLocationProvider() != null) {
                RouteDataObject routeObject = app.getLocationProvider().getLastKnownRouteSegment();
                if (routeObject != null) {
                    return routeObject.getMaximumSpeed(routeObject.bearingVsRouteDirection(location));
                }
            }
        } else {
            return routingHelper.getCurrentMaxSpeed();
        }
        return 0;
    }

    // ========== WEATHER ==========

    @Override
    public void onWeatherUpdated(WeatherManager.WeatherData data) {
        lastWeatherData = data;
        if (size == WidgetSize.MEDIUM && weatherContainer != null && data != null && !data.isStale) {
            rootFrame.post(() -> {
                weatherContainer.setVisibility(View.VISIBLE);
                tempText.setText(String.format(Locale.getDefault(), "%.0fÂ°", data.temp));
                String iconName = data.getIconName();
                int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
                if (resId != 0) weatherIcon.setImageResource(resId);
            });
        }
    }

    @Override
    public void onWeatherError(String error) {}

    // ========== LIFECYCLE ==========

    @Override
    public void onStart() {
        super.onStart();
        weatherManager.addListener(this);
        app.getLocationProvider().addLocationListener(this);
        if (lastWeatherData == null) {
            lastWeatherData = weatherManager.getCachedWeather();
            if (lastWeatherData != null) onWeatherUpdated(lastWeatherData);
        }
        startUpdating();
    }

    @Override
    public void onStop() {
        super.onStop();
        weatherManager.removeListener(this);
        app.getLocationProvider().removeLocationListener(this);
        stopUpdating();
    }

    private void startUpdating() {
        if (updateRunnable == null && rootView != null) {
            updateRunnable = () -> {
                update();
                if (rootView != null && isStarted()) {
                    rootView.postDelayed(updateRunnable, 1000);
                }
            };
            rootView.post(updateRunnable);
        }
    }

    private void stopUpdating() {
        if (updateRunnable != null && rootView != null) {
            rootView.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
