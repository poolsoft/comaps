package com.example.comaps.aar;

import javax.naming.Context;

import androidx.annotation.NonNull;
import app.organicmaps.sdk.location.BaseLocationProvider;
import app.organicmaps.sdk.location.LocationProviderFactory;

public class DummyLocationProviderFactory implements LocationProviderFactory {
    @Override
    public boolean isGoogleLocationAvailable(@NonNull Context context) {
        return false;
    }

    @Override
    public BaseLocationProvider getProvider(@NonNull Context context, @NonNull BaseLocationProvider.Listener listener) {
        return new BaseLocationProvider(listener) {
            @Override
            protected void start(long interval) {
            }

            @Override
            protected void stop() {
            }
        };
    }
}
