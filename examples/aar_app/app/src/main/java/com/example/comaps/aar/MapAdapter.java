package com.example.comaps.aar;

import java.util.Map;

import android.net.Uri;
import android.util.Log;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.bookmarks.data.FeatureId;
import app.organicmaps.sdk.bookmarks.data.MapObject;
import app.organicmaps.sdk.routing.RoutingController;

public class MapAdapter {
    private static final String TAG = "MapAdapter";

    public static void setCenter(double lat, double lon, int zoom) {
        Framework.nativeSetViewportCenter(lat, lon, zoom);
    }

    public static void addMarker(double lat, double lon, String title) {
        try {
            String url = "mapswithme://map?ll=" + lat + "," + lon + "&n=" + Uri.encode(title == null ? "" : title);
            int type = Framework.nativeParseAndSetApiUrl(url);
            if (type == app.organicmaps.sdk.api.RequestType.MAP)
                Map.executeMapApiRequest();
        } catch (Exception e) {
            Log.e(TAG, "addMarker failed", e);
        }
    }

    public static void startRouting(double srcLat, double srcLon, double dstLat, double dstLon) {
        try {
            MapObject start = MapObject.createMapObject(FeatureId.EMPTY, MapObject.API_POINT, "Start", "", srcLat,
                    srcLon);
            MapObject end = MapObject.createMapObject(FeatureId.EMPTY, MapObject.API_POINT, "Dest", "", dstLat, dstLon);
            RoutingController.get().prepare(start, end);
            RoutingController.get().build();
        } catch (Exception e) {
            Log.e(TAG, "startRouting failed", e);
        }
    }
}
