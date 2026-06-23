package app.organicmaps.carlauncher.antenna;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import app.organicmaps.R;

import java.util.Locale;

public class AntennaAlignmentFragment extends Fragment implements SensorEventListener, AntennaManager.AntennaListener {

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometer;
    private Sensor magnetometer;

    private boolean hasRotationVector = false;

    private AlignmentView alignmentView;
    private TextView textTargetInfo;
    private TextView textCurrentAzimuth;
    private TextView textCurrentPitch;

    private float targetAzimuth = 0;
    private float targetPitch = 0;

    private float currentAzimuth = 0;
    private float currentPitch = 0;

    private static final float ALPHA = 0.1f; // Smoothing factor
    private float[] rMat = new float[9];
    private float[] orientation = new float[3];

    // Fallback for sensors
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean lastAccelerometerSet = false;
    private boolean lastMagnetometerSet = false;

    private AntennaManager manager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_antenna_alignment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        manager = AntennaManager.getInstance(requireContext());

        // Bind views
        alignmentView = view.findViewById(R.id.alignment_view);
        textTargetInfo = view.findViewById(R.id.text_target_info);
        textCurrentAzimuth = view.findViewById(R.id.text_current_azimuth);
        textCurrentPitch = view.findViewById(R.id.text_current_pitch);

        // Geri butonu: Sadece pusulayi kapat (Turkce karakter yok)
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        // Kapat butonu: Hem pusulayi hem de sag paneli kapat (Turkce karakter yok)
        View btnClose = view.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                // Sag paneli kapatmasi icin MapActivity'ye broadcast gonder
                LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(new Intent("net.osmand.carlauncher.CLOSE_ANTENNA_PANEL"));
                
                if (getActivity() != null) {
                    getActivity().finish();
                }
            });
        }

        // Swap butonu: Kaynak ve hedef yerini degistir (Turkce karakter yok)
        View btnSwap = view.findViewById(R.id.btn_swap);
        if (btnSwap != null) {
            btnSwap.setOnClickListener(v -> {
                if (manager != null) {
                    manager.swapPoints();
                    updateUI();
                }
            });
        }

        // Sensorleri hazirla (Turkce karakter yok)
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVectorSensor != null) {
            hasRotationVector = true;
        } else {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }

        updateUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        manager.setListener(this);
        if (hasRotationVector) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            if (magnetometer != null)
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        manager.setListener(null);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rMat, event.values);
            calculateOrientation();
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            lastAccelerometerSet = true;
            if (lastMagnetometerSet) {
                SensorManager.getRotationMatrix(rMat, null, lastAccelerometer, lastMagnetometer);
                calculateOrientation();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            lastMagnetometerSet = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onAntennaPointsChanged() {
        if (getView() != null) {
            getView().post(this::updateUI);
        }
    }

    private void calculateOrientation() {
        SensorManager.getOrientation(rMat, orientation);

        // Azimuth: orientation[0] (-PI to PI)
        float az = (float) Math.toDegrees(orientation[0]);
        if (az < 0)
            az += 360;

        // Pitch: orientation[1] (-PI/2 to PI/2)
        float pitch = (float) Math.toDegrees(orientation[1]);

        float declination = 0;
        az += declination;
        if (az >= 360)
            az -= 360;

        // Smooth
        currentAzimuth = lowPass(az, currentAzimuth);
        currentPitch = lowPass(pitch, currentPitch);

        updateCompassUI();
    }

    private void updateCompassUI() {
        if (textCurrentAzimuth != null) {
            textCurrentAzimuth.setText(String.format(Locale.US, "Yon: %.1f°", currentAzimuth));
        }
        if (textCurrentPitch != null) {
            textCurrentPitch.setText(String.format(Locale.US, "Egim: %.1f°", currentPitch));
        }
        if (alignmentView != null) {
            alignmentView.setCurrent(currentAzimuth, currentPitch);
        }
    }

    private void updateUI() {
        AntennaManager.AntennaPoint source = manager.getSource();
        AntennaManager.AntennaPoint target = manager.getTarget();

        // Hedef verilerini hesapla ve guncelle (Turkce karakter yok)
        if (source != null && target != null) {
            targetAzimuth = (float) manager.getAzimuthSourceToTarget();
            if (targetAzimuth < 0)
                targetAzimuth += 360;

            targetPitch = (float) manager.getElevationSourceToTarget();

            if (textTargetInfo != null) {
                double distMeters = manager.getDistanceMeters();
                String distStr = distMeters >= 1000
                        ? String.format(Locale.US, "%.2f km", distMeters / 1000)
                        : String.format(Locale.US, "%.0f m", distMeters);

                textTargetInfo.setText(String.format(Locale.US,
                        "KAYNAK: %s -> HEDEF: %s\nMesafe: %s | Azimut %.1f° | Egim %.1f°",
                        source.name, target.name, distStr, targetAzimuth, targetPitch));
            }

            if (alignmentView != null) {
                alignmentView.setTarget(targetAzimuth, targetPitch);
            }
        } else {
            if (textTargetInfo != null) {
                textTargetInfo.setText("Kaynak ve hedef nokta secilmemis.");
            }
        }
    }

    private float lowPass(float input, float output) {
        if (Math.abs(input - output) > 180) {
            if (input > output)
                output += 360;
            else
                input += 360;
        }
        float res = output + ALPHA * (input - output);
        if (res >= 360)
            res -= 360;
        return res;
    }
}
