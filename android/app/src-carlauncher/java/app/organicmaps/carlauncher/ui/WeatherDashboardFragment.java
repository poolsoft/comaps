package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.weather.WeatherManager;
import app.organicmaps.carlauncher.widgets.view.TrendGraphView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeatherDashboardFragment extends Fragment implements WeatherManager.WeatherListener {

    private TextView tvLocation, tvCurrentTemp, tvCondition, tvApparent, tvWind, tvRain, tvVis;
    private ImageView imgIcon;
    private RecyclerView rvHourly, rvDaily;
    private TrendGraphView trendGraph;
    
    private HourlyAdapter hourlyAdapter;
    private DailyAdapter dailyAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weather_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        tvLocation = view.findViewById(R.id.tv_location);
        tvCurrentTemp = view.findViewById(R.id.tv_current_temp);
        tvCondition = view.findViewById(R.id.tv_current_condition);
        tvApparent = view.findViewById(R.id.tv_apparent_temp);
        tvWind = view.findViewById(R.id.tv_wind);
        tvRain = view.findViewById(R.id.tv_rain);
        tvVis = view.findViewById(R.id.tv_visibility);
        imgIcon = view.findViewById(R.id.img_current_icon);
        
        ImageButton btnClose = view.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                        .setPanelContent(app.organicmaps.carlauncher.ui.PanelContentManager.PanelContent.WIDGETS);
                } else if (getParentFragmentManager() != null) {
                    getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                }
            });
        }

        // Setup RecyclerViews
        rvHourly = view.findViewById(R.id.rv_hourly);
        rvHourly.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        hourlyAdapter = new HourlyAdapter();
        rvHourly.setAdapter(hourlyAdapter);

        rvDaily = view.findViewById(R.id.rv_daily);
        // Default Vertical, but Landscape XML might set it specifically or we check orientation
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        rvDaily.setLayoutManager(new LinearLayoutManager(getContext(), isLandscape ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL, false));
        dailyAdapter = new DailyAdapter();
        rvDaily.setAdapter(dailyAdapter);

        // Graph (Landscape only)
        trendGraph = view.findViewById(R.id.trend_graph);

        // Load Data
        WeatherManager wm = WeatherManager.getInstance(getContext());
        wm.addListener(this);
        // Trigger refresh if needed or just use cache
        wm.forceRefresh(); 
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        WeatherManager.getInstance(getContext()).removeListener(this);
    }

    @Override
    public void onWeatherUpdated(WeatherManager.WeatherData data) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> updateUI(data));
    }

    @Override
    public void onWeatherError(String error) {
        // Handle error (Toast?)
    }

    private void updateUI(WeatherManager.WeatherData data) {
        if (data == null) return;

        // Header
        tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.0f°", data.temp));
        tvCondition.setText(getConditionText(data.weatherCode));
        tvApparent.setText(String.format(Locale.getDefault(), "Feels %.0f°", data.apparentTemp));
        
        tvWind.setText(String.format(Locale.getDefault(), "%.1f km/h", data.windSpeed));
        tvRain.setText(String.format(Locale.getDefault(), "%d%%", data.rainProbToday)); // Need to make sure rainProbToday is populated correctly in Manager
        // Check Visibility exists
        // tvVis.setText... Logic needed in Layout XML for visibility if not present

        int resId = getResources().getIdentifier(data.getIconName(), "drawable", getContext().getPackageName());
        if (resId != 0) imgIcon.setImageResource(resId);

        // Location (Placeholder or from MwmActivity)
        // Ideally WeatherManager should pass location name or we get it from Gps
        tvLocation.setText("Current Location"); 

        // Adapters
        if (data.hourlyForecasts != null) {
            hourlyAdapter.setItems(data.hourlyForecasts);
            
            // Graph Data
            if (trendGraph != null) {
                List<Double> temps = new ArrayList<>();
                for (WeatherManager.HourlyForecast h : data.hourlyForecasts) {
                    temps.add(h.temp);
                }
                trendGraph.setData(temps);
            }
        }
        
        if (data.dailyForecasts != null) {
            dailyAdapter.setItems(data.dailyForecasts);
        }
    }

    private String getConditionText(int code) {
        // Simplified mapping
        if (code == 0) return "Clear Sky";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Foggy";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Unknown";
    }

    // --- Adapters ---

    private class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.ViewHolder> {
        private List<WeatherManager.HourlyForecast> items = new ArrayList<>();

        public void setItems(List<WeatherManager.HourlyForecast> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_hourly, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WeatherManager.HourlyForecast item = items.get(position);
            // Parse Time ISO8601 "2023-10-10T12:00" -> "12:00"
            try {
                String time = item.time.substring(item.time.indexOf("T") + 1);
                holder.tvTime.setText(time);
            } catch (Exception e) {
                holder.tvTime.setText(item.time);
            }
            
            holder.tvTemp.setText(String.format(Locale.getDefault(), "%.0f°", item.temp));
            
            // Icon
            WeatherManager.WeatherData dummy = new WeatherManager.WeatherData();
            String iconName = dummy.getIconName(item.weatherCode);
            int resId = getResources().getIdentifier(iconName, "drawable", getContext().getPackageName());
            if (resId != 0) holder.imgIcon.setImageResource(resId);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTime, tvTemp;
            ImageView imgIcon;

            ViewHolder(View itemView) {
                super(itemView);
                tvTime = itemView.findViewById(R.id.time);
                tvTemp = itemView.findViewById(R.id.temp);
                imgIcon = itemView.findViewById(R.id.icon);
            }
        }
    }

    private class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {
        private List<WeatherManager.DailyForecast> items = new ArrayList<>();

        public void setItems(List<WeatherManager.DailyForecast> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weather_daily, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WeatherManager.DailyForecast item = items.get(position);
            
            // Parse Date "2023-10-10" -> "Mon"
            try {
                SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                Date date = inFormat.parse(item.date);
                SimpleDateFormat outFormat = new SimpleDateFormat("EEE", Locale.getDefault()); // Mon, Tue
                holder.tvDay.setText(outFormat.format(date));
            } catch (Exception e) {
                holder.tvDay.setText(item.date);
            }

            holder.tvMax.setText(String.format(Locale.getDefault(), "%.0f°", item.maxTemp));
            holder.tvMin.setText(String.format(Locale.getDefault(), "%.0f°", item.minTemp));
            holder.tvRain.setText(item.rainProb + "%");
            if (item.rainProb < 20) holder.tvRain.setVisibility(View.INVISIBLE);
            else holder.tvRain.setVisibility(View.VISIBLE);

            WeatherManager.WeatherData dummy = new WeatherManager.WeatherData();
            String iconName = dummy.getIconName(item.weatherCode);
            int resId = getResources().getIdentifier(iconName, "drawable", getContext().getPackageName());
            if (resId != 0) holder.imgIcon.setImageResource(resId);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvDay, tvMax, tvMin, tvRain;
            ImageView imgIcon;

            ViewHolder(View itemView) {
                super(itemView);
                tvDay = itemView.findViewById(R.id.day);
                tvMax = itemView.findViewById(R.id.max_temp);
                tvMin = itemView.findViewById(R.id.min_temp);
                tvRain = itemView.findViewById(R.id.rain_prob);
                imgIcon = itemView.findViewById(R.id.icon);
            }
        }
    }
}
