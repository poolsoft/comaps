package app.organicmaps.carlauncher.api;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.sdk.routing.RoutingInfo;

import java.util.List;

/** Flavor-local boundary between Car Launcher features and the host map application. */
public interface CarMapApi {
    final class FavoritePlace {
        public final long categoryId;
        public final long bookmarkId;
        @NonNull public final String categoryName;
        @NonNull public final String name;
        public final double latitude;
        public final double longitude;

        public FavoritePlace(long categoryId, long bookmarkId, @NonNull String categoryName,
                             @NonNull String name, double latitude, double longitude) {
            this.categoryId = categoryId;
            this.bookmarkId = bookmarkId;
            this.categoryName = categoryName;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    boolean isCoreReady();

    @Nullable Location getLastKnownLocation();

    @NonNull String resolveAddress(double latitude, double longitude);

    @NonNull String resolveNearbyStreet(double latitude, double longitude, float bearingDegrees);

    boolean isNavigating();

    @Nullable RoutingInfo getNavigationInfo();

    @NonNull List<FavoritePlace> getFavoritePlaces();
}

