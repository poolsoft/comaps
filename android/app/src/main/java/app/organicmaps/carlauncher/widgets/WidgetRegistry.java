package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import app.organicmaps.MwmApplication;
import java.util.ArrayList;
import java.util.List;

/**
 * Dinamik Widget KayÃ„Â±t Sistemi.
 * Yeni widget eklemek icin buraya register etmek yeterlidir.
 * UI (WidgetPanelFragment) listeyi buradan otomatik ceker.
 */
public class WidgetRegistry {

    // Sabit Widget Tipleri
    public static final String TYPE_COMBINED = "combined";
    public static final String TYPE_SPEED = "speed";
    public static final String TYPE_MUSIC = "music";
    public static final String TYPE_NAVIGATION = "navigation";
    public static final String TYPE_COMPASS = "compass";
    public static final String TYPE_OBD = "obd";
    public static final String TYPE_CLOCK = "clock";
    public static final String TYPE_CLOCK_CLASSIC = "classic";
    public static final String TYPE_WEATHER = "weather";

    // Widget Yaratma Arayuzu (Lambda icin)
    public interface WidgetCreator {
        BaseWidget create(Context context, MwmApplication app);
    }

    // Widget Tanim Bilgisi
    public static class WidgetEntry {
        public final String typeId;
        public final String displayName;
        public final WidgetCreator creator;

        public WidgetEntry(String typeId, String displayName, WidgetCreator creator) {
            this.typeId = typeId;
            this.displayName = displayName;
            this.creator = creator;
        }
    }

    private static final List<WidgetEntry> availableWidgets = new ArrayList<>();

    // Statik blok ile temel widget'lari kaydediyoruz.
    static {
        register(TYPE_COMBINED, "Dashboard (Saat+HÃ„Â±z)", CombinedWidget::new);
        register(TYPE_SPEED, "HÃ„Â±z GÃƒÂ¶stergesi", SpeedWidget::new);
        register(TYPE_MUSIC, "MÃƒÂ¼zik Ãƒâ€¡alar", MusicWidget::new);
        register(TYPE_NAVIGATION, "Navigasyon", NavigationWidget::new);
        register(TYPE_COMPASS, "Pusula", DirectionWidget::new);
        register(TYPE_CLOCK, "Dijital Saat (M3)", Material3ClockWidget::new);
        register(TYPE_CLOCK_CLASSIC, "Klasik Saat", ClockWidget::new);
        register(TYPE_WEATHER, "Hava Durumu", WeatherWidget::new);
    }

    /**
     * Yeni bir widget tipi kaydet.
     */
    public static void register(String typeId, String displayName, WidgetCreator creator) {
        availableWidgets.add(new WidgetEntry(typeId, displayName, creator));
    }

    /**
     * Kayitli tum widget tiplerini getir.
     */
    public static List<WidgetEntry> getAvailableWidgets() {
        return new ArrayList<>(availableWidgets);
    }

    /**
     * ID'ye gore widget olustur.
     */
    public static BaseWidget createWidget(Context context, MwmApplication app, String typeId) {
        for (WidgetEntry entry : availableWidgets) {
            if (entry.typeId.equals(typeId)) {
                return entry.creator.create(context, app);
            }
        }
        return null;
    }

    /**
     * Yeni (benzersiz) bir widget ornegi olusturur.
     * ID'si: type_timestamp formatinda olur.
     */
    public static BaseWidget createUniqueWidget(Context context, MwmApplication app, String typeId) {
        BaseWidget widget = createWidget(context, app, typeId);
        if (widget != null) {
            String uniqueId = typeId + "_" + System.currentTimeMillis();
            widget.setId(uniqueId);
        }
        return widget;
    }
}
