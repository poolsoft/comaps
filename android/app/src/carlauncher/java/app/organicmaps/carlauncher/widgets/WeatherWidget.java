package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.widgets.weather.WeatherManager;

import java.util.Locale;

/**
 * Hava Durumu Wiget'i.
 * WeatherManager'dan veri alir ve gosterir.
 */
public class WeatherWidget extends BaseWidget implements WeatherManager.WeatherListener {

    private TextView tvLocation;
    private TextView tvTemp;
    private TextView tvDesc;
    private ImageView ivIcon;
    private ProgressBar progressBar;
    
    private final WeatherManager weatherManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WeatherWidget(@NonNull Context context, MwmApplication app) {
        super(context, "weather", "Hava Durumu");
        this.weatherManager = WeatherManager.getInstance(context);
        this.order = 10; // Default order towards end
    }

    @Override
    public View createView() {
        // Parent belirtilmediÄŸi iÃ§in layout params manuel ayarlanmalÄ± veya 
        // view eklendiÄŸi yerde ayarlanacaÄŸÄ± varsayÄ±lmalÄ±dÄ±r.
        View view = LayoutInflater.from(context).inflate(app.organicmaps.R.layout.widget_weather, null);
        
        // LayoutParams dÃ¼zeltmesi (Ä°htiyaca gÃ¶re deÄŸiÅŸtirin, genelde gereklidir)
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT));

        tvLocation = view.findViewById(app.organicmaps.R.id.weather_location);
        tvTemp = view.findViewById(app.organicmaps.R.id.weather_temp);
        tvDesc = view.findViewById(app.organicmaps.R.id.weather_desc);
        ivIcon = view.findViewById(app.organicmaps.R.id.weather_icon);
        progressBar = view.findViewById(app.organicmaps.R.id.weather_loading);

        rootView = view;

        // Listener'Ä± bir deÄŸiÅŸkene atayÄ±p tekrar kullanmak daha temizdir
        View.OnClickListener openDashboardListener = v -> {
            app.organicmaps.carlauncher.CarLauncherInterface callback = null;
            Context ctx = context;
            
            while (ctx instanceof android.content.ContextWrapper) {
                // 1. Context'in kendisi Interface mi?
                if (ctx instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    callback = (app.organicmaps.carlauncher.CarLauncherInterface) ctx;
                    break;
                }
                
                // 2. Context Activity mi? (Activity ise daha derine inme)
                if (ctx instanceof android.app.Activity) {
                    if (ctx instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                        callback = (app.organicmaps.carlauncher.CarLauncherInterface) ctx;
                    }
                    break; // Activity bulunduysa loop bitmeli
                }
                
                // 3. Bir alt context'e in
                ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
                
                // Null check (GÃ¼venlik iÃ§in)
                if (ctx == null) break;
            }
            
            if (callback != null) {
                callback.openWeatherDashboard();
            } else {
                android.util.Log.e("WeatherWidget", "Context, CarLauncherInterface'i uygulamÄ±yor veya bulunamadÄ±: " + context.getClass().getName());
            }
        };

        // Listener'Ä± hem ikona hem de kÃ¶k gÃ¶rÃ¼nÃ¼me ata
        if (ivIcon != null) {
            ivIcon.setOnClickListener(openDashboardListener);
        }
        if (rootView != null) {
            rootView.setOnClickListener(openDashboardListener);
        }
        
        // Null check eklenmeli
        if (weatherManager != null) {
            updateUI(weatherManager.getCachedWeather());
        }
        
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        weatherManager.addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        weatherManager.removeListener(this);
    }

    @Override
    public void onWeatherUpdated(WeatherManager.WeatherData data) {
         mainHandler.post(() -> updateUI(data));
    }

    @Override
    public void onWeatherError(String error) {
        mainHandler.post(() -> {
            if (tvDesc != null) tvDesc.setText(error);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tvLocation = null;
        tvTemp = null;
        tvDesc = null;
        ivIcon = null;
        progressBar = null;
    }

    @Override
    public void update() {
        // Redundant if listener works, but can force refresh UI
        updateUI(weatherManager.getCachedWeather());
    }
    
    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootView == null) return;
        
        // Adjust UI based on size
        if (newSize == WidgetSize.SMALL) {
            // Compact Mode
            if (tvDesc != null) tvDesc.setVisibility(View.GONE);
            if (progressBar != null) progressBar.setVisibility(View.GONE); // Hide loading in small to save space?
            
            // Icon smaller
            if (ivIcon != null) {
                ivIcon.setVisibility(View.VISIBLE);
                // Layout params could be adjusted here if needed, 
                // but simpler to rely on layout constraints or GONE handling
            }
            
            if (tvLocation != null) tvLocation.setVisibility(View.GONE); // Hide location in small
        } else {
            // Normal Mode
            if (tvDesc != null) tvDesc.setVisibility(View.VISIBLE);
            if (tvLocation != null) tvLocation.setVisibility(View.VISIBLE);
            // Loading bar handled by data state
        }
        
        // Refresh data display
        updateUI(weatherManager.getCachedWeather());
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void openConfig(androidx.fragment.app.FragmentManager fragmentManager) {
        app.organicmaps.carlauncher.ui.WeatherConfigDialog dialog = new app.organicmaps.carlauncher.ui.WeatherConfigDialog(this);
        dialog.show(fragmentManager, "WeatherConfig");
    }

    private void updateUI(WeatherManager.WeatherData data) {
        if (rootView == null) return;
        
        if (data == null) {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (tvTemp != null) tvTemp.setText("--");
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.GONE);

        if (tvTemp != null) {
            tvTemp.setText(String.format(Locale.US, "%.0fÂ°", data.temp));
        }
        
        if (tvDesc != null) {
            tvDesc.setText(getWeatherDescription(data.weatherCode));
        }

        if (ivIcon != null) {
            int iconRes = getIconResource(data.getIconName());
            ivIcon.setImageResource(iconRes);
        }
        
        if (tvLocation != null) {
             tvLocation.setText("Konum");
        }
        
        // Enforcement for specific sizes if layout reset properties
        if (size == WidgetSize.SMALL) {
             if (tvDesc != null) tvDesc.setVisibility(View.GONE);
             if (tvLocation != null) tvLocation.setVisibility(View.GONE);
        }
    }
    
    private String getWeatherDescription(int code) {
        if (code == 0) return "AÃ§Ä±k";
        if (code >= 1 && code <= 3) return "ParÃ§alÄ± Bulutlu";
        if (code >= 45 && code <= 48) return "Sisli";
        if (code >= 51 && code <= 67) return "YaÄŸmurlu";
        if (code >= 71 && code <= 77) return "KarlÄ±";
        if (code >= 80 && code <= 82) return "SaÄŸanak";
        if (code >= 95) return "FÄ±rtÄ±na";
        return "Bilinmiyor";
    }

    private int getIconResource(String iconName) {
        int resId = context.getResources().getIdentifier(iconName, "drawable", context.getPackageName());
        if (resId == 0) {
            if (iconName.contains("clear")) return app.organicmaps.R.drawable.ic_action_sun; 
            if (iconName.contains("cloud")) return app.organicmaps.R.drawable.ic_action_cloud;
            return app.organicmaps.R.drawable.ic_action_umbrella;
        }
        return resId;
    }
}
