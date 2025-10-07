package com.example.comaps.submodule;

import javax.naming.Context;

import androidx.annotation.NonNull;
import app.organicmaps.sdk.location.AndroidNativeProvider;
import app.organicmaps.sdk.location.BaseLocationProvider;
import app.organicmaps.sdk.location.LocationProviderFactory;

public class AndroidLocationProviderFactory implements LocationProviderFactory {
    @Override
    public boolean isGoogleLocationAvailable(@NonNull Context context) {
        return false;
    }

    @Override
    public BaseLocationProvider getProvider(@NonNull Context context, @NonNull BaseLocationProvider.Listener listener) {
        return new AndroidNativeProvider(context, listener);
    }
}
