package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.plugins.PluginsHelper;
import app.organicmaps.plugins.odb.VehicleMetricsPlugin;
import net.osmand.shared.obd.OBDDataComputer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.organicmaps.carlauncher.telemetry.TelemetryManager;

/**
 * OBD Dashboard Widget (Dynamic).
 * Displays selected metrics from VehicleMetricsPlugin.
 */
public class OBDWidget extends BaseWidget implements TelemetryManager.TelemetryListener {

    private final MwmApplication app;
    private VehicleMetricsPlugin plugin;
    private final CarLauncherSettings settings;

    // Dynamic Computers & Views
    private final Map<String, OBDDataComputer.OBDComputerWidget> activeComputers = new HashMap<>();
    private final Map<String, TextView> valueViews = new HashMap<>();
    private LinearLayout mainContainer;

    public OBDWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "obd_dashboard", "AraÃ§ Verileri");
        this.app = app;
        this.settings = new CarLauncherSettings(context);
        this.order = 2;
    }

    @Override
    public View createView() {
        // Use the existing XML as a base container, but we will manipulate its children
        View view = LayoutInflater.from(context).inflate(R.layout.widget_obd_dashboard, null);
        mainContainer = (LinearLayout) view;
        
        // Setup Click Listener (Reconnect/Info)
        view.setOnClickListener(v -> {
            boolean connected = (plugin != null && plugin.isConnected());
            if (connected) {
                // Open Dashboard
                try {
                    android.content.Intent intent = new android.content.Intent(context, app.organicmaps.carlauncher.ui.ObdDashboardActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                     Toast.makeText(context, "Dashboard AÃ§Ä±lamadÄ±", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Formatting Reconnect
                if (plugin != null) {
                   plugin.connectToLastConnectedDevice(1);
                   Toast.makeText(context, "OBD BaÄŸlanÄ±yor...", Toast.LENGTH_SHORT).show();
                } else {
                   Toast.makeText(context, "OBD Eklentisi Aktif DeÄŸil", Toast.LENGTH_SHORT).show();
                }
            }
        });

        rootView = view;
        updateFromConfig(); // Build UI and Computers
        return rootView;
    }

    public void updateFromConfig() {
        String config = settings.getWidgetConfig(getId());
        if (config == null) config = "rpm,temp,volt"; // Default
        
        List<String> items = new ArrayList<>(Arrays.asList(config.split(",")));
        if (items.isEmpty()) items.add("rpm");

        // Rebuild Layout
        mainContainer.removeAllViews();
        valueViews.clear();
        activeComputers.clear();

        initComputers(items);
        buildDynamicLayout(items);
        updateUI();
    }

    private void initComputers(List<String> items) {
        plugin = PluginsHelper.getPlugin(VehicleMetricsPlugin.class);
        if (plugin != null && plugin.isActive()) {
            for (String key : items) {
                OBDDataComputer.OBDTypeWidget type = getTypeForKey(key);
                if (type != null) {
                    activeComputers.put(key, OBDDataComputer.INSTANCE.registerWidget(type, 0));
                }
            }
        }
    }

    private OBDDataComputer.OBDTypeWidget getTypeForKey(String key) {
        switch (key) {
            case "rpm": return OBDDataComputer.OBDTypeWidget.RPM;
            case "speed": return OBDDataComputer.OBDTypeWidget.SPEED;
            case "temp": return OBDDataComputer.OBDTypeWidget.TEMPERATURE_COOLANT;
            case "volt": return OBDDataComputer.OBDTypeWidget.BATTERY_VOLTAGE;
            case "load": return OBDDataComputer.OBDTypeWidget.CALCULATED_ENGINE_LOAD;
            case "intake": return OBDDataComputer.OBDTypeWidget.TEMPERATURE_INTAKE;
            default: return null;
        }
    }
    
    private String getLabelForKey(String key) {
        switch (key) {
            case "rpm": return "RPM";
            case "speed": return "HIZ";
            case "temp": return "TEMP";
            case "volt": return "V";
            case "load": return "YÃœK";
            case "intake": return "GÄ°RÄ°Å";
            default: return key.toUpperCase();
        }
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void openConfig(androidx.fragment.app.FragmentManager fragmentManager) {
        app.organicmaps.carlauncher.ui.VehicleDataConfigDialog dialog = new app.organicmaps.carlauncher.ui.VehicleDataConfigDialog(this, settings);
        dialog.show(fragmentManager, "VehicleConfig");
    }

    private void buildDynamicLayout(List<String> items) {
        // Simple Vertical Stack for now, or Split if 2+ items?
        // Let's do:
        // 1 item: Big Center
        // 2 items: Vertical Split
        // 3+ items: Top Big, Bottom Row split
        
        if (items.isEmpty()) return;

        if (items.size() == 1) {
            mainContainer.addView(createItemView(items.get(0), 40, true));
        } else if (items.size() == 2) {
             mainContainer.addView(createItemView(items.get(0), 32, true));
             addDivider();
             mainContainer.addView(createItemView(items.get(1), 32, true));
        } else {
            // First item Big
            mainContainer.addView(createItemView(items.get(0), 32, true));
            addDivider();
            
            // Rest in a horizontal row (max 3 per row?)
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            
            for (int i = 1; i < items.size(); i++) {
                View itemView = createItemView(items.get(i), 16, false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.weight = 1;
                row.addView(itemView, lp);
            }
            mainContainer.addView(row);
        }
    }

    private void addDivider() {
        View v = new View(context);
        v.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1); // 1px
        lp.setMargins(0, 8, 0, 8);
        v.setLayoutParams(lp);
        mainContainer.addView(v);
    }
    
    private View createItemView(String key, float textSizeSp, boolean matchParent) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(4, 4, 4, 4);
        
        TextView val = new TextView(context);
        val.setText("--");
        val.setTextColor(Color.WHITE);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        val.setTypeface(null, android.graphics.Typeface.BOLD);
        val.setGravity(Gravity.CENTER);
        
        TextView label = new TextView(context);
        label.setText(getLabelForKey(key));
        label.setTextColor(0xFFAAAAAA); // Light Gray
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        label.setGravity(Gravity.CENTER);
        
        layout.addView(val);
        layout.addView(label);
        
        valueViews.put(key, val);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                matchParent ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (matchParent) lp.weight = 1;
        layout.setLayoutParams(lp);
        
        return layout;
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
        updateUI();
    }

    private void updateUI() {
        if (plugin == null) {
            plugin = PluginsHelper.getPlugin(VehicleMetricsPlugin.class);
        }
        
        if (plugin == null || !plugin.isActive() || !plugin.isConnected()) {
            for (TextView tv : valueViews.values()) {
                tv.setText("--");
            }
            // Optional: Show status in top view if disconnected
            return;
        }

        for (Map.Entry<String, OBDDataComputer.OBDComputerWidget> entry : activeComputers.entrySet()) {
            String val = plugin.getWidgetValue(entry.getValue());
            TextView tv = valueViews.get(entry.getKey());
            if (tv != null) {
                // Formatting
                if (entry.getKey().equals("temp") || entry.getKey().equals("intake")) val += "Â°C";
                if (entry.getKey().equals("volt")) val += " V";
                if (entry.getKey().equals("load")) val += " %";
                
                tv.setText(val);
            }
        }
    }

    @Override
    public void update() {
    }
}


