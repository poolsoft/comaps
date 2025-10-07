package com.example.comaps.submodule;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.routing.RoutingController;

import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.DownloadResourcesLegacyActivity;
import androidx.annotation.NonNull;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Request fine location permission if not granted, initialize OrganicMaps
        // accordingly
        DemoApplication app = (DemoApplication) getApplication();
        Runnable onComplete = () -> runOnUiThread(() -> {
            if (savedInstanceState == null) {
                ExampleMapFragment frag = new ExampleMapFragment();
                FragmentTransaction t = getSupportFragmentManager().beginTransaction();
                t.replace(R.id.container, frag);
                t.commit();
            }
            // Demo actions after init
            MapAdapter.setCenter(52.5200, 13.4050, 14);
            MapAdapter.addMarker(52.5200, 13.4050, "Berlin");
        });

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            app.initOrganicMaps(new AndroidLocationProviderFactory(), onComplete);
        } else {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 1001);
            // Initialize with dummy provider so map can still be used; still create
            // fragment onComplete
            app.initOrganicMaps(new DummyLocationProviderFactory(), onComplete);
        }
        // Fragment will be created after OrganicMaps initialization completes to ensure
        // the SDK LocationHelper is ready.

        // Wire UI controls
        Button btnCenter = findViewById(R.id.btn_center);
        Button btnMarker = findViewById(R.id.btn_marker);
        Button btnRoute = findViewById(R.id.btn_route);
        Button btnDownload = findViewById(R.id.btn_download);
        ProgressBar downloadProgress = findViewById(R.id.download_progress);
        TextView tvDownloadStatus = findViewById(R.id.tv_download_status);
        ProgressBar routingProgress = findViewById(R.id.routing_progress);
        TextView tvRoutingStatus = findViewById(R.id.tv_routing_status);

        btnCenter.setOnClickListener(v -> {
            if (!Map.isEngineCreated()) {
                Toast.makeText(this, "Engine not ready", Toast.LENGTH_SHORT).show();
                return;
            }
            MapAdapter.setCenter(52.5200, 13.4050, 14);
        });
        btnMarker.setOnClickListener(v -> {
            if (!Map.isEngineCreated()) {
                Toast.makeText(this, "Engine not ready", Toast.LENGTH_SHORT).show();
                return;
            }
            MapAdapter.addMarker(52.5200, 13.4050, "Berlin");
        });
        btnRoute.setOnClickListener(v -> {
            if (!Map.isEngineCreated()) {
                Toast.makeText(this, "Engine not ready", Toast.LENGTH_SHORT).show();
                return;
            }
            // Example: from Berlin to Potsdam
            MapAdapter.startRouting(52.5200, 13.4050, 52.3989, 13.0657);
        });
        btnDownload.setOnClickListener(v -> {
            tvDownloadStatus.setText("Preparing...");
            int bytes = DownloadResourcesLegacyActivity.nativeGetBytesToDownload();
            if (bytes <= 0) {
                tvDownloadStatus.setText("No files to download or error");
                return;
            }
            downloadProgress.setMax(bytes);
            downloadProgress.setProgress(0);
            tvDownloadStatus.setText("Downloading...");
            new Thread(() -> {
                int res = DownloadResourcesLegacyActivity.nativeStartNextFileDownload(
                        new DownloadResourcesLegacyActivity.Listener() {
                            @Override
                            public void onProgress(int bytesDownloaded) {
                                runOnUiThread(() -> downloadProgress.setProgress(bytesDownloaded));
                            }

                            @Override
                            public void onFinish(int errorCode) {
                                runOnUiThread(() -> tvDownloadStatus.setText("Finished: " + errorCode));
                            }
                        });
                if (res == DownloadResourcesLegacyActivity.ERR_NO_MORE_FILES)
                    runOnUiThread(() -> tvDownloadStatus.setText("No more files"));
            }).start();
        });

        // Register simple RoutingController container to receive build progress
        RoutingController.get().attach(new RoutingController.Container() {
            @Override
            public void updateBuildProgress(int progress, Router router) {
                runOnUiThread(() -> {
                    routingProgress.setProgress(progress);
                    tvRoutingStatus.setText("Routing build: " + progress + "%");
                });
            }

            @Override
            public void onBuiltRoute() {
                runOnUiThread(() -> tvRoutingStatus.setText("Route built"));
            }

            @Override
            public void onCommonBuildError(int lastResultCode, @NonNull String[] lastMissingMaps) {
                runOnUiThread(() -> {
                    tvRoutingStatus.setText("Route error: " + lastResultCode);
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            DemoApplication app = (DemoApplication) getApplication();
            app.initOrganicMaps(new AndroidLocationProviderFactory(), () -> runOnUiThread(() -> {
                if (getSupportFragmentManager().findFragmentById(R.id.container) == null) {
                    ExampleMapFragment frag = new ExampleMapFragment();
                    FragmentTransaction t = getSupportFragmentManager().beginTransaction();
                    t.replace(R.id.container, frag);
                    t.commit();
                }
                MapAdapter.setCenter(52.5200, 13.4050, 14);
                MapAdapter.addMarker(52.5200, 13.4050, "Berlin");
            }));
        }
    }
}
