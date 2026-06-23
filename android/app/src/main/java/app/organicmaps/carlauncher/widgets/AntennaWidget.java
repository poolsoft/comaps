package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.antenna.AntennaManager;
import app.organicmaps.carlauncher.antenna.AntennaPointPickerDialog;

import java.util.Locale;

/**
 * Anten Hizalama Widget'i.
 * - Kaynak/Hedef nokta secimi: AlertDialog tabanli picker
 * - Harita katmani toggle: goz ikonu ile ac/kapat
 * - Swap: iki nokta secilince aktif
 */
public class AntennaWidget extends BaseWidget implements AntennaManager.AntennaListener {

    private TextView textSource, textTarget;
    private TextView valDistance, valAzimuth, valElevation;
    private View statsContainer;
    private ImageButton btnLayerToggle;

    private final AntennaManager manager;
    private boolean layerVisible = false; // baslangicta kapali

    public AntennaWidget(@NonNull Context context, @NonNull MwmApplication app) {
        super(context, "antenna", "Anten");
        this.manager = AntennaManager.getInstance(context);
        this.order = 4;
    }

    @NonNull
    @Override
    public View createView() {
        View view = LayoutInflater.from(context)
                .inflate(app.organicmaps.R.layout.widget_antenna_modern, null);

        // View baglama
        textSource = view.findViewById(app.organicmaps.R.id.text_source);
        textTarget = view.findViewById(app.organicmaps.R.id.text_target);
        valDistance = view.findViewById(app.organicmaps.R.id.val_distance);
        valAzimuth = view.findViewById(app.organicmaps.R.id.val_azimuth);
        valElevation = view.findViewById(app.organicmaps.R.id.val_elevation);
        statsContainer = view.findViewById(app.organicmaps.R.id.stats_container);
        btnLayerToggle = view.findViewById(app.organicmaps.R.id.btn_layer_toggle);
        ImageButton btnSwap = view.findViewById(app.organicmaps.R.id.btn_swap);

        // --- Layer toggle (goz ikonu) ---
        btnLayerToggle.setOnClickListener(v -> {
            layerVisible = !layerVisible;
            manager.setLayerVisible(layerVisible);
            updateLayerToggleUI();
        });

        // --- Swap butonu ---
        btnSwap.setOnClickListener(v -> {
            if (manager.getSource() != null && manager.getTarget() != null) {
                manager.swapPoints();
            } else {
                Toast.makeText(context,
                        "Once kaynak ve hedef nokta secin.", Toast.LENGTH_SHORT).show();
            }
        });

        // --- Kaynak butonu ---
        view.findViewById(app.organicmaps.R.id.btn_set_source)
                .setOnClickListener(v -> AntennaPointPickerDialog.show(
                        getContext(), manager, true, null));

        // --- Hedef butonu ---
        view.findViewById(app.organicmaps.R.id.btn_set_target)
                .setOnClickListener(v -> AntennaPointPickerDialog.show(
                        getContext(), manager, false, null));

        // --- Hizalama ekrani ---
        view.findViewById(app.organicmaps.R.id.btn_align).setOnClickListener(v -> {
            if (manager.getSource() != null && manager.getTarget() != null) {
                try {
                    Intent intent = new Intent(context,
                            app.organicmaps.carlauncher.antenna.AntennaAlignmentActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, "Hizalama ekrani baslatilirken hata.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "Lutfen once kaynak ve hedef secin.", Toast.LENGTH_SHORT).show();
            }
        });

        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        rootView = view;
        updateUI();
        updateLayerToggleUI();
        return rootView;
    }

    /** Harita layer toggle ikonunu guncelle */
    private void updateLayerToggleUI() {
        if (btnLayerToggle == null) return;
        // Aktifse mavi, degilse gri
        int tint = layerVisible ? 0xFF4FC3F7 : 0xFF555555;
        btnLayerToggle.setColorFilter(tint);
    }

    @Override
    public void onStart() {
        super.onStart();
        manager.setListener(this);
        // Layer baslangicta kapali — kullanici acsin
        manager.setLayerVisible(layerVisible);
        updateUI();
    }

    @Override
    public void onStop() {
        super.onStop();
        manager.setListener(null);
        manager.setLayerVisible(false); // Widget kapaninca layer gizle
    }

    @Override
    public void update() {
        updateUI();
    }

    @Override
    public void onAntennaPointsChanged() {
        if (rootView != null) {
            rootView.post(this::updateUI);
        }
    }

    private void updateUI() {
        if (rootView == null) return;

        AntennaManager.AntennaPoint source = manager.getSource();
        AntennaManager.AntennaPoint target = manager.getTarget();

        // Swap butonu durumu
        ImageButton swapBtn = rootView.findViewById(app.organicmaps.R.id.btn_swap);
        if (swapBtn != null) {
            boolean both = source != null && target != null;
            swapBtn.setEnabled(both);
            swapBtn.setAlpha(both ? 1.0f : 0.3f);
            swapBtn.setColorFilter(both ? 0xFF4CAF50 : 0xFF555555);
        }

        // Kaynak etiketi
        if (source != null) {
            String name = (source.name != null && !source.name.isEmpty())
                    ? source.name
                    : String.format(Locale.US, "%.4f, %.4f", source.lat, source.lon);
            textSource.setText(name);
            textSource.setTextColor(0xFF4FC3F7);
        } else {
            textSource.setText("Sec...");
            textSource.setTextColor(0xFF888888);
        }

        // Hedef etiketi
        if (target != null) {
            String name = (target.name != null && !target.name.isEmpty())
                    ? target.name
                    : String.format(Locale.US, "%.4f, %.4f", target.lat, target.lon);
            textTarget.setText(name);
            textTarget.setTextColor(0xFFFFD700);
        } else {
            textTarget.setText("Sec...");
            textTarget.setTextColor(0xFF888888);
        }

        // Hesaplama degerleri
        if (source != null && target != null) {
            statsContainer.setVisibility(View.VISIBLE);

            double distMeters = manager.getDistanceMeters();
            valDistance.setText(distMeters >= 1000
                    ? String.format(Locale.US, "%.2f km", distMeters / 1000)
                    : String.format(Locale.US, "%.0f m", distMeters));

            double az = manager.getAzimuthSourceToTarget();
            if (az < 0) az += 360;
            valAzimuth.setText(String.format(Locale.US, "%.1f°", az));

            double elev = manager.getElevationSourceToTarget();
            valElevation.setText(String.format(Locale.US, "%.1f°", elev));
        } else {
            statsContainer.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSizeChanged(WidgetSize newSize) {
        if (rootView == null) return;
        
        float titleSize = (newSize == WidgetSize.LARGE) ? 18 : 14;
        float valueSize = (newSize == WidgetSize.LARGE) ? 22 : 18;

        if (textSource != null) textSource.setTextSize(titleSize);
        if (textTarget != null) textTarget.setTextSize(titleSize);
        if (valDistance != null) valDistance.setTextSize(valueSize);
        if (valAzimuth != null) valAzimuth.setTextSize(valueSize);
        if (valElevation != null) valElevation.setTextSize(valueSize);
    }
}
