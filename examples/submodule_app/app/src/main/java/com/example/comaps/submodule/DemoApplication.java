package com.example.comaps.submodule;

import android.app.Application;
import android.util.Log;
import app.organicmaps.sdk.OrganicMaps;
import app.organicmaps.sdk.location.LocationProviderFactory;

public class DemoApplication extends Application {
    public static volatile OrganicMaps organicMapsInstance;

    public synchronized void initOrganicMaps(LocationProviderFactory factory, Runnable onComplete) {
        if (organicMapsInstance != null) {
            if (onComplete != null)
                onComplete.run();
            return;
        }

        new Thread(() -> {
            try {
                organicMapsInstance = new OrganicMaps(getApplicationContext(), "web", getPackageName(), 1, "0.1",
                        getPackageName() + ".provider", factory);
                organicMapsInstance.init(() -> {
                    Log.i("DemoApplication", "OrganicMaps framework initialized");
                    if (onComplete != null)
                        onComplete.run();
                });
            } catch (Exception e) {
                Log.e("DemoApplication", "Failed to init OrganicMaps", e);
            }
        }).start();
    }
}
