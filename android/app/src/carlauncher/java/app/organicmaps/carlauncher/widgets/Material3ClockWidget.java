package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Material 3 Design Clock Widget.
 * Horizontal 4-Digit Layout with Custom Font.
 */
public class Material3ClockWidget extends BaseWidget {

    private TextView tvHourFirst, tvHourSecond;
    private TextView tvMinFirst, tvMinSecond;
    private TextView tvDate;
    private android.graphics.Typeface clockTypeface;

    private final SimpleDateFormat hourFormat;
    private final SimpleDateFormat minuteFormat;
    private final SimpleDateFormat dateFormat;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    public Material3ClockWidget(@NonNull Context context) {
        super(context, "clock", context.getString(R.string.car_widget_digital_clock));
        this.hourFormat = new SimpleDateFormat("HH", Locale.getDefault());
        this.minuteFormat = new SimpleDateFormat("mm", Locale.getDefault());
         // E.g. "Pazartesi, 30 AralÄ±k"
        this.dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
        this.order = 0; // Top position
    }

    public Material3ClockWidget(@NonNull Context context, MwmApplication app) {
        this(context);
    }

    @Override
    public View createView() {
        View view = LayoutInflater.from(context).inflate(R.layout.widget_clock_material3, null);

        tvHourFirst = view.findViewById(R.id.hour_digit_1);
        tvHourSecond = view.findViewById(R.id.hour_digit_2);
        tvMinFirst = view.findViewById(R.id.min_digit_1);
        tvMinSecond = view.findViewById(R.id.min_digit_2);
        tvDate = view.findViewById(R.id.clock_date_text);

        try {
            clockTypeface = android.graphics.Typeface.createFromAsset(context.getAssets(),
                    "fonts/Cross Boxed.ttf");

            TextView[] textViews = {tvHourFirst, tvHourSecond, tvMinFirst, tvMinSecond};
            for (TextView tv : textViews) {
                if (tv != null) {
                    tv.setTypeface(clockTypeface);
                    androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            tv, 20, 100, 2, android.util.TypedValue.COMPLEX_UNIT_SP);
                }
            }
        } catch (Exception e) {
        }

        rootView = view;
        updateUI();
        return rootView;
    }
    
    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (tvDate == null) return;
        
        // Show date in ALL sizes, but adjust size
        tvDate.setVisibility(View.VISIBLE);
        
        if (newSize == WidgetSize.SMALL) {
            tvDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12); // Smaller for compact
            tvDate.setPadding(0, 0, 0, 4); // Reduce padding
        } else {
            tvDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16); // Normal/Large
            tvDate.setPadding(0, 0, 0, 8);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        startTimer();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopTimer();
    }

    @Override
    public void update() {
        updateUI();
    }

    private void updateUI() {
        if (tvHourFirst == null) return;

        Date now = new Date();
        String hours = hourFormat.format(now);
        String minutes = minuteFormat.format(now);

        if (hours.length() >= 2) {
            tvHourFirst.setText(String.valueOf(hours.charAt(0)));
            tvHourSecond.setText(String.valueOf(hours.charAt(1)));
        }
        if (minutes.length() >= 2) {
            tvMinFirst.setText(String.valueOf(minutes.charAt(0)));
            tvMinSecond.setText(String.valueOf(minutes.charAt(1)));
        }
        
        if (tvDate != null) {
            tvDate.setText(dateFormat.format(now));
        }
    }

    private void startTimer() {
        stopTimer();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                long now = System.currentTimeMillis();
                long delay = 60000 - (now % 60000);
                handler.postDelayed(this, delay);
            }
        };
        handler.post(updateRunnable);
    }

    private void stopTimer() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }
}
