package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.widgets.view.ModernAnalogClockView;
import app.organicmaps.carlauncher.widgets.weather.WeatherManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * GeliÅŸmiÅŸ Saat Widget'Ä±.
 * S: Dijital Saat + Tarih
 * M: Dijital Saat + Tarih + Hava Durumu
 * L: Analog Saat + Tarih
 */
public class ClockWidget extends BaseWidget implements WeatherManager.WeatherListener {

    // UI Elements
    private FrameLayout rootFrame;
    private View digitalView; // S & M
    private ModernAnalogClockView analogClockView; // L

    // Digital Elements
    private TextView clockText;
    private TextView dateText;
    
    // Weather Elements (M Only)
    private LinearLayout weatherContainer;
    private ImageView weatherIcon;
    private TextView tempText;

    // Helper
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateFormat;
    private Runnable updateRunnable;
    private WeatherManager weatherManager;
    private WeatherManager.WeatherData lastWeatherData;

    public ClockWidget(@NonNull Context context) {
        super(context, "smart_clock", "AkÄ±llÄ± Saat");
        init();
    }

    public ClockWidget(@NonNull Context context, MwmApplication app) {
        this(context);
    }

    private void init() {
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        this.dateFormat = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        this.order = 0;
        this.weatherManager = WeatherManager.getInstance(context);
    }

    @NonNull
    @Override
    public View createView() {
        rootFrame = new FrameLayout(context);
        rootFrame.setBackgroundResource(app.organicmaps.R.drawable.bg_widget_modern);
        rootFrame.setPadding(0, 0, 0, 0);

        // Initial Layout (Default S)
        setupDigitalLayout(rootFrame);

        rootView = rootFrame;
        return rootView;
    }

    // --- Layout Builders ---

    private void setupDigitalLayout(ViewGroup root) {
        root.removeAllViews();
        analogClockView = null; // Clear analog ref

        // Main Container (Horizontal for M, Vertical for S fallback)
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.HORIZONTAL); // Default Horizontal, adjust later
        mainLayout.setGravity(Gravity.CENTER);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mainLayout.setLayoutParams(params);

        // -- LEFT: Time & Date --
        LinearLayout timeContainer = new LinearLayout(context);
        timeContainer.setOrientation(LinearLayout.VERTICAL);
        timeContainer.setGravity(Gravity.CENTER);
        timeContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)); // Weight 1

        clockText = new TextView(context);
        clockText.setTextColor(Color.WHITE);
        clockText.setTextSize(52);
        clockText.setGravity(Gravity.CENTER);
        clockText.setIncludeFontPadding(false);
        try {
            Typeface digitalFont = Typeface.createFromAsset(context.getAssets(), "fonts/curved-seven-segment.ttf");
            clockText.setTypeface(digitalFont);
        } catch (Exception e) {
            clockText.setTypeface(Typeface.DEFAULT_BOLD);
        }
        timeContainer.addView(clockText);

        dateText = new TextView(context);
        dateText.setTextColor(Color.LTGRAY);
        dateText.setTextSize(14);
        dateText.setGravity(Gravity.CENTER);
        timeContainer.addView(dateText);

        mainLayout.addView(timeContainer);

        // -- RIGHT: Weather (Initially GONE) --
        weatherContainer = new LinearLayout(context);
        weatherContainer.setOrientation(LinearLayout.VERTICAL);
        weatherContainer.setGravity(Gravity.CENTER);
        weatherContainer.setVisibility(View.GONE);
        weatherContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f)); // Weight 0.8

        weatherIcon = new ImageView(context);
        int iconSize = dpToPx(48);
        weatherIcon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        weatherContainer.addView(weatherIcon);

        tempText = new TextView(context);
        tempText.setTextColor(Color.WHITE);
        tempText.setTextSize(24);
        tempText.setTypeface(Typeface.DEFAULT_BOLD);
        tempText.setGravity(Gravity.CENTER);
        weatherContainer.addView(tempText);

        mainLayout.addView(weatherContainer);

        root.addView(mainLayout);
        digitalView = mainLayout;
    }

    private void setupAnalogLayout(ViewGroup root) {
        root.removeAllViews();
        digitalView = null;

        // Analog Clock View
        analogClockView = new ModernAnalogClockView(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        int margin = dpToPx(8);
        // Add more space at bottom for Date
        params.setMargins(margin, margin, margin, dpToPx(36)); 
        analogClockView.setLayoutParams(params);
        
        root.addView(analogClockView);

        // Date Overlay for Analog (Centered at bottom area)
        dateText = new TextView(context);
        dateText.setTextColor(Color.WHITE);
        dateText.setTextSize(14);
        dateText.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dateParams.setMargins(0, 0, 0, dpToPx(12)); // Position in the reserved space
        dateText.setLayoutParams(dateParams);
        
        root.addView(dateText);
    }

    // --- Logic ---

    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootFrame == null) return;

        if (newSize == WidgetSize.LARGE) {
            setupAnalogLayout(rootFrame);
        } else {
            setupDigitalLayout(rootFrame);
            // S vs M logic handled in update() or here
            if (newSize == WidgetSize.MEDIUM) {
                // Try to show weather if data exists
                 if (weatherContainer != null) {
                     // Visibility logic handled in updateWeatherUI
                 }
            } else {
                // SMALL -> Hide weather force
                if (weatherContainer != null) weatherContainer.setVisibility(View.GONE);
            }
        }
        update(); // Refresh content
        
        // Trigger weather update if M
        if (newSize == WidgetSize.MEDIUM && weatherManager.getCachedWeather() != null) {
             onWeatherUpdated(weatherManager.getCachedWeather());
        }
    }

    @Override
    public void update() {
        if (analogClockView != null) {
            // Analog Mode: Only update date
             if (dateText != null) {
                 dateText.setText(dateFormat.format(new Date()));
             }
        } else if (clockText != null) {
            // Digital Mode
            clockText.setText(timeFormat.format(new Date()));
            if (dateText != null) {
                dateText.setText(dateFormat.format(new Date()));
            }
        }
    }

    @Override
    public void onWeatherUpdated(WeatherManager.WeatherData data) {
        lastWeatherData = data;
        
        if (size == WidgetSize.MEDIUM && weatherContainer != null && data != null && !data.isStale) {
            rootFrame.post(() -> {
                weatherContainer.setVisibility(View.VISIBLE);
                
                // Temp
                tempText.setText(String.format(Locale.getDefault(), "%.1fÂ°", data.temp));
                
                // Icon
                String iconName = data.getIconName();
                int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
                if (resId != 0) {
                    weatherIcon.setImageResource(resId);
                } else {
                    // Fallback using identifier check failed?
                    // Maybe we are in a library module? No, app module.
                    // Fallback to cloudy found in R? We can't access R.drawable.ic_weather_cloudy safely if not generated R.
                    // We rely on getIdentifier. If 0, hide icon?
                }
            });
        }
    }

    @Override
    public void onWeatherError(String error) {
        // Fallback or ignore
    }

    @Override
    public void onStart() {
        super.onStart();
        weatherManager.addListener(this);
        startUpdating();
        
        // Initial setup check
        if (lastWeatherData == null) {
            lastWeatherData = weatherManager.getCachedWeather();
            if (lastWeatherData != null) onWeatherUpdated(lastWeatherData);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        weatherManager.removeListener(this);
        stopUpdating();
    }

    private void startUpdating() {
        if (updateRunnable == null && rootView != null) {
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    update();
                    if (rootView != null && isStarted()) {
                        rootView.postDelayed(this, 1000);
                    }
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
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
