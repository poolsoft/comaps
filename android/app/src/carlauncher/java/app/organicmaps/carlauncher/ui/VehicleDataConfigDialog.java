package app.organicmaps.carlauncher.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.carlauncher.widgets.BaseWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VehicleDataConfigDialog extends DialogFragment {

    private final BaseWidget widget;
    private final CarLauncherSettings settings;

    // Available keys must match what OBDWidget expects
    private static final String KEY_RPM = "rpm";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_TEMP = "temp";
    private static final String KEY_VOLT = "volt";
    private static final String KEY_LOAD = "load"; // Engine Load
    private static final String KEY_INTAKE = "intake"; // Intake Temp
    
    // Future: KEY_FUEL, KEY_ADBLUE etc.

    public VehicleDataConfigDialog(BaseWidget widget, CarLauncherSettings settings) {
        this.widget = widget;
        this.settings = settings;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), R.style.OsmandDarkTheme));
        builder.setTitle("GÃ¶rÃ¼ntÃ¼lenecek Veriler");

        ScrollView scrollView = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        // Load current config
        String currentConfig = settings.getWidgetConfig(widget.getId());
        if (currentConfig == null) currentConfig = "rpm,temp,volt"; // Default
        List<String> selectedItems = new ArrayList<>(Arrays.asList(currentConfig.split(",")));

        // Create Checkboxes
        CheckBox cbRpm = createCheckBox("Motor Devri (RPM)", KEY_RPM, selectedItems);
        CheckBox cbSpeed = createCheckBox("HÄ±z (OBD/GPS)", KEY_SPEED, selectedItems);
        CheckBox cbTemp = createCheckBox("Motor Suyu SÄ±caklÄ±ÄŸÄ±", KEY_TEMP, selectedItems);
        CheckBox cbVolt = createCheckBox("AkÃ¼ VoltajÄ±", KEY_VOLT, selectedItems);
        CheckBox cbLoad = createCheckBox("Motor YÃ¼kÃ¼ (%)", KEY_LOAD, selectedItems);
        CheckBox cbIntake = createCheckBox("Hava GiriÅŸ SÄ±caklÄ±ÄŸÄ±", KEY_INTAKE, selectedItems);
        
        layout.addView(cbRpm);
        layout.addView(cbSpeed);
        layout.addView(cbTemp);
        layout.addView(cbVolt);
        layout.addView(cbLoad);
        layout.addView(cbIntake);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            List<String> newItems = new ArrayList<>();
            if (cbRpm.isChecked()) newItems.add(KEY_RPM);
            if (cbSpeed.isChecked()) newItems.add(KEY_SPEED);
            if (cbTemp.isChecked()) newItems.add(KEY_TEMP);
            if (cbVolt.isChecked()) newItems.add(KEY_VOLT);
            if (cbLoad.isChecked()) newItems.add(KEY_LOAD);
            if (cbIntake.isChecked()) newItems.add(KEY_INTAKE);

            String configConfig = TextUtils.join(",", newItems);
            settings.setWidgetConfig(widget.getId(), configConfig);
            
            // Trigger refresh
            if (widget instanceof app.organicmaps.carlauncher.widgets.OBDWidget) {
                 ((app.organicmaps.carlauncher.widgets.OBDWidget) widget).updateFromConfig();
            }
        });

        builder.setNegativeButton("Ä°ptal", null);

        return builder.create();
    }

    private CheckBox createCheckBox(String text, String key, List<String> selected) {
        CheckBox cb = new CheckBox(getContext());
        cb.setText(text);
        cb.setTextColor(0xFFFFFFFF);
        cb.setChecked(selected.contains(key));
        cb.setTag(key);
        // Style?
        return cb;
    }
}
