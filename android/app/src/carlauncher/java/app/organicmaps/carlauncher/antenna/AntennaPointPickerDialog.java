package app.organicmaps.carlauncher.antenna;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.annotation.NonNull;

import net.osmand.Location;
import net.osmand.data.FavouritePoint;
import net.osmand.data.LatLon;
import app.organicmaps.MwmApplication;
import app.organicmaps.myplaces.favorites.FavouritesHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anten noktasi secim menÃ¼sÃ¼ â€” AlertDialog tabanli, FragmentManager gerektirmez.
 * show() static metodu ile dogrudan context Ã¼zerinden cagrilir.
 */
public class AntennaPointPickerDialog {

    // Harita secimi talep oldugunda bildirim icin
    public interface OnMapPickRequestListener {
        void onMapPickRequested(boolean isSource);
    }

    /**
     * Nokta secim dialogunu ac.
     * @param context  Herhangi bir Context (Activity/Service)
     * @param manager  AntennaManager singleton
     * @param isSource true=Kaynak, false=Hedef
     * @param mapListener Haritadan sec secilince cagirilir (null olabilir)
     */
    public static void show(Context context,
                            AntennaManager manager,
                            boolean isSource,
                            OnMapPickRequestListener mapListener) {

        boolean bothSet = manager.getSource() != null && manager.getTarget() != null;

        // Secenekleri hazirla
        List<String> options = new ArrayList<>();
        if (!bothSet) {
            options.add("GPS Konumumu Kullan");
        }
        options.add("Favorilerden Sec");
        options.add("Haritadan Sec");
        options.add("Koordinat Gir");
        options.add("Noktayi Temizle");

        String title = isSource ? "Kaynak Nokta Sec" : "Hedef Nokta Sec";
        String[] items = options.toArray(new String[0]);

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    String selected = options.get(which);
                    switch (selected) {
                        case "GPS Konumumu Kullan":
                            handleGps(context, manager, isSource);
                            break;
                        case "Favorilerden Sec":
                            handleFavorites(context, manager, isSource);
                            break;
                        case "Haritadan Sec":
                            handleMap(context, manager, isSource, mapListener);
                            break;
                        case "Koordinat Gir":
                            handleCoordinate(context, manager, isSource);
                            break;
                        case "Noktayi Temizle":
                            handleClear(context, manager, isSource);
                            break;
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    // --- GPS ---

    private static void handleGps(Context context, AntennaManager manager, boolean isSource) {
        try {
            MwmApplication app = (MwmApplication) context.getApplicationContext();
            if (app.getLocationProvider() == null) {
                Toast.makeText(context, "Konum servisi hazir degil.", Toast.LENGTH_SHORT).show();
                return;
            }
            
            net.osmand.Location loc = app.getLocationProvider().getLastKnownLocation();
            if (loc != null) {
                LatLon latLon = new LatLon(loc.getLatitude(), loc.getLongitude());
                double altitude = loc.getAltitude();
                if (isSource) {
                    manager.setSource(latLon, "GPS Konumum", altitude);
                } else {
                    manager.setTarget(latLon, "GPS Konumum", altitude);
                }
                Toast.makeText(context,
                        (isSource ? "Kaynak" : "Hedef") + " GPS konumuna ayarlandi.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "GPS sinyali yok. Bekleyiniz.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "GPS alinirken hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // --- Favoriler ---

    private static void handleFavorites(Context context, AntennaManager manager, boolean isSource) {
        try {
            MwmApplication app = (MwmApplication) context.getApplicationContext();
            FavouritesHelper favHelper = app.getFavoritesHelper();
            List<FavouritePoint> favPoints = favHelper.getFavouritePoints();

            if (favPoints == null || favPoints.isEmpty()) {
                Toast.makeText(context, "Kayitli favori bulunamadi.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<FavouritePoint> list = new ArrayList<>(favPoints);
            String[] names = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                FavouritePoint p = list.get(i);
                String group = p.getCategory() != null && !p.getCategory().isEmpty()
                        ? "[" + p.getCategory() + "] " : "";
                names[i] = group + p.getName();
            }

            new AlertDialog.Builder(context)
                    .setTitle("Favori Sec")
                    .setItems(names, (dialog2, which) -> {
                        FavouritePoint p = list.get(which);
                        LatLon latLon = new LatLon(p.getLatitude(), p.getLongitude());
                        double alt = p.getAltitude();
                        if (isSource) {
                            manager.setSource(latLon, p.getName(), alt);
                        } else {
                            manager.setTarget(latLon, p.getName(), alt);
                        }
                        Toast.makeText(context,
                                (isSource ? "Kaynak" : "Hedef") + ": " + p.getName(),
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Iptal", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(context, "Favoriler yuklenirken hata: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // --- Haritadan Sec ---

    private static void handleMap(Context context, AntennaManager manager,
                                  boolean isSource, OnMapPickRequestListener listener) {
        manager.setPickingMode(isSource ? AntennaManager.PICK_SOURCE : AntennaManager.PICK_TARGET);
        manager.setLayerVisible(true);
        if (listener != null) {
            listener.onMapPickRequested(isSource);
        }
        Toast.makeText(context,
                "Haritaya tiklayin â€” " + (isSource ? "Kaynak" : "Hedef") + " nokta ayarlanacak.",
                Toast.LENGTH_LONG).show();
    }

    // --- Koordinat Gir ---

    private static void handleCoordinate(Context context, AntennaManager manager, boolean isSource) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText etLat = new EditText(context);
        etLat.setHint("Enlem (orn: 41.01234)");
        etLat.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);

        EditText etLon = new EditText(context);
        etLon.setHint("Boylam (orn: 28.97654)");
        etLon.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);

        EditText etAlt = new EditText(context);
        etAlt.setHint("Yukseklik m (opsiyonel)");
        etAlt.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);

        layout.addView(etLat);
        layout.addView(etLon);
        layout.addView(etAlt);

        new AlertDialog.Builder(context)
                .setTitle(isSource ? "Kaynak Koordinat" : "Hedef Koordinat")
                .setView(layout)
                .setPositiveButton("Tamam", (dialog, which) -> {
                    try {
                        double lat = Double.parseDouble(etLat.getText().toString().trim());
                        double lon = Double.parseDouble(etLon.getText().toString().trim());
                        String altStr = etAlt.getText().toString().trim();
                        double alt = altStr.isEmpty() ? 0 : Double.parseDouble(altStr);

                        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                            Toast.makeText(context, "Gecersiz koordinat araligÄ±.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        LatLon latLon = new LatLon(lat, lon);
                        String name = String.format(Locale.US, "%.5f, %.5f", lat, lon);
                        if (isSource) {
                            manager.setSource(latLon, name, alt);
                        } else {
                            manager.setTarget(latLon, name, alt);
                        }
                        Toast.makeText(context,
                                (isSource ? "Kaynak" : "Hedef") + " koordinat ayarlandi.",
                                Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Gecersiz sayi formati.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    // --- Temizle ---

    private static void handleClear(Context context, AntennaManager manager, boolean isSource) {
        new AlertDialog.Builder(context)
                .setTitle("Noktayi Temizle")
                .setMessage((isSource ? "Kaynak" : "Hedef") + " nokta silinecek. Emin misiniz?")
                .setPositiveButton("Evet", (d, w) -> {
                    if (isSource) {
                        manager.clearSource();
                    } else {
                        manager.clearTarget();
                    }
                    Toast.makeText(context,
                            (isSource ? "Kaynak" : "Hedef") + " nokta temizlendi.",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Iptal", null)
                .show();
    }
}
