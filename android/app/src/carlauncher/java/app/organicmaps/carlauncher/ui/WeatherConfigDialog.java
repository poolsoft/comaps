package app.organicmaps.carlauncher.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import app.organicmaps.R;
import app.organicmaps.carlauncher.widgets.WeatherWidget;
import app.organicmaps.carlauncher.widgets.weather.WeatherManager;

public class WeatherConfigDialog extends DialogFragment {

    private final WeatherWidget widget;
    
    public WeatherConfigDialog(WeatherWidget widget) {
        this.widget = widget;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(getContext(), android.R.style.Theme_DeviceDefault_Dialog));
        builder.setTitle("Hava Durumu Ayarlari");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);
        
        // 1. Update Interval Label
        android.widget.TextView lblInterval = new android.widget.TextView(getContext());
        lblInterval.setText("Guncelleme Sikligi");
        lblInterval.setTextColor(0xFFFFFFFF);
        lblInterval.setPadding(0, 0, 0, 16);
        layout.addView(lblInterval);
        
        // 2. Interval Spinner
        Spinner spinner = new Spinner(getContext());
        String[] intervals = {"15 Dakika", "30 Dakika", "1 Saat", "3 Saat"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, intervals);
        spinner.setAdapter(adapter);
        
        int currentInterval = WeatherManager.getInstance(getContext()).getUpdateIntervalMinutes();
        int selIndex = 1; // Default 30
        if (currentInterval == 15) selIndex = 0;
        else if (currentInterval == 30) selIndex = 1;
        else if (currentInterval == 60) selIndex = 2;
        else if (currentInterval == 180) selIndex = 3;
        spinner.setSelection(selIndex);
        
        layout.addView(spinner);
        
        // 3. Force Update Button
        Button btnUpdate = new Button(getContext());
        btnUpdate.setText("Simdi Guncelle");
        btnUpdate.setOnClickListener(v -> {
            WeatherManager.getInstance(getContext()).forceRefresh();
            Toast.makeText(getContext(), "Guncelleme istegi gonderildi.", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 40, 0, 0);
        btnUpdate.setLayoutParams(lp);
        layout.addView(btnUpdate);

        builder.setView(layout);

        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            int pos = spinner.getSelectedItemPosition();
            int min = 30;
            switch (pos) {
                case 0: min = 15; break;
                case 1: min = 30; break;
                case 2: min = 60; break;
                case 3: min = 180; break;
            }
            WeatherManager.getInstance(getContext()).setUpdateIntervalMinutes(min);
        });

        builder.setNegativeButton("Iptal", null);

        return builder.create();
    }
}

