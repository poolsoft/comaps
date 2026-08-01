package app.organicmaps.carlauncher.api;

import android.content.Context;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.MwmApplication;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.BookmarkCategory;
import app.organicmaps.sdk.bookmarks.data.BookmarkInfo;
import app.organicmaps.sdk.bookmarks.data.BookmarkManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.log.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** CoMaps implementation of the small map surface required by Car Launcher. */
public final class CoMapsCarMapApi implements CarMapApi {
    private static final String TAG = CoMapsCarMapApi.class.getSimpleName();
    private static final double STREET_PROBE_METERS = 15.0;
    private static volatile CoMapsCarMapApi instance;

    @NonNull private final MwmApplication application;

    private CoMapsCarMapApi(@NonNull Context context) {
        application = MwmApplication.from(context);
    }

    @NonNull
    public static CoMapsCarMapApi getInstance(@NonNull Context context) {
        CoMapsCarMapApi local = instance;
        if (local == null) {
            synchronized (CoMapsCarMapApi.class) {
                local = instance;
                if (local == null) instance = local = new CoMapsCarMapApi(context.getApplicationContext());
            }
        }
        return local;
    }

    @Override
    public boolean isCoreReady() {
        return application.getOrganicMaps().arePlatformAndCoreInitialized();
    }

    @Nullable
    @Override
    public Location getLastKnownLocation() {
        return application.getLocationHelper().getSavedLocation();
    }

    @NonNull
    @Override
    public String resolveAddress(double latitude, double longitude) {
        if (!isCoreReady()) return "";
        try {
            String address = Framework.nativeGetAddress(latitude, longitude);
            return address == null ? "" : address.trim();
        } catch (RuntimeException error) {
            Logger.w(TAG, "Reverse geocoding failed", error);
            return "";
        }
    }

    @NonNull
    @Override
    public String resolveNearbyStreet(double latitude, double longitude, float bearingDegrees) {
        String direct = streetPart(resolveAddress(latitude, longitude));
        if (!direct.isEmpty()) return direct;

        // Probe forward first, then both sides and backwards. This is more useful while driving
        // than an unordered four-direction search and still remains cheap enough for location updates.
        double[] bearings = {bearingDegrees, bearingDegrees + 90.0, bearingDegrees - 90.0, bearingDegrees + 180.0};
        for (double bearing : bearings) {
            double radians = Math.toRadians(bearing);
            double latOffset = Math.cos(radians) * STREET_PROBE_METERS / 111111.0;
            double cosLatitude = Math.max(0.01, Math.abs(Math.cos(Math.toRadians(latitude))));
            double lonOffset = Math.sin(radians) * STREET_PROBE_METERS / (111111.0 * cosLatitude);
            String street = streetPart(resolveAddress(latitude + latOffset, longitude + lonOffset));
            if (!street.isEmpty()) return street;
        }
        return "";
    }

    @NonNull
    private static String streetPart(@Nullable String address) {
        if (address == null || address.isEmpty()) return "";
        int separator = address.indexOf(',');
        return (separator >= 0 ? address.substring(0, separator) : address).trim();
    }

    @Override
    public boolean isNavigating() {
        RoutingController controller = RoutingController.get();
        return controller != null && controller.isNavigating();
    }

    @Nullable
    @Override
    public RoutingInfo getNavigationInfo() {
        RoutingController controller = RoutingController.get();
        return controller != null && controller.isNavigating() ? controller.getCachedRoutingInfo() : null;
    }

    @NonNull
    @Override
    public List<FavoritePlace> getFavoritePlaces() {
        if (!isCoreReady()) return Collections.emptyList();
        try {
            BookmarkManager manager = BookmarkManager.INSTANCE;
            List<FavoritePlace> result = new ArrayList<>();
            for (BookmarkCategory category : manager.getCategories()) {
                for (int index = 0; index < category.getBookmarksCount(); index++) {
                    long bookmarkId = manager.getBookmarkIdByPosition(category.getId(), index);
                    BookmarkInfo info = manager.getBookmarkInfo(bookmarkId);
                    if (info == null) continue;
                    result.add(new FavoritePlace(category.getId(), bookmarkId, category.getName(),
                            manager.getBookmarkName(bookmarkId), info.getLat(), info.getLon()));
                }
            }
            return result;
        } catch (RuntimeException error) {
            Logger.w(TAG, "Bookmark enumeration failed", error);
            return Collections.emptyList();
        }
    }
}
