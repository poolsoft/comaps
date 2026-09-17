package app.organicmaps.carlauncher.widgets.map;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Harita uzeri widget yerlesim modeli (OsmAnd tarzi).
 * Her widget ozel kimligiyle (instance id veya tip id) bir harita paneline
 * (TOP/LEFT/RIGHT/BOTTOM) yerlesir; harita panelinde COMPACT veya WIDE
 * gorunur. Yerlesim kalici olarak saklanir; harita panelleri yan panel
 * (WidgetPanelFragment) bagimsizdir.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public final class MapWidgetPlacementStore
{
  public enum Panel { TOP, LEFT, RIGHT, BOTTOM, NONE }

  public enum Mode { COMPACT, WIDE }

  public static final class Placement
  {
    public final Panel panel;
    public final Mode mode;
    public final int order;

    public Placement(@NonNull Panel panel, @NonNull Mode mode, int order)
    {
      this.panel = panel;
      this.mode = mode;
      this.order = order;
    }
  }

  private static final String PREFS_NAME = "map_widgets_placement";
  private static final String KEY_ENABLED = "overlay_enabled";
  private static final String KEY_CONFIGURED = "overlay_defaults_applied";
  // Key: widget key (instance id veya tip id) -> "panel|mode|order"
  private static final String KEY_PREFIX = "place_";

  private final SharedPreferences prefs;

  private static MapWidgetPlacementStore instance;

  private MapWidgetPlacementStore(@NonNull Context context)
  {
    prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }

  @NonNull
  public static synchronized MapWidgetPlacementStore getInstance(@NonNull Context context)
  {
    if (instance == null)
      instance = new MapWidgetPlacementStore(context);
    return instance;
  }

  public boolean isOverlayEnabled()
  {
    return prefs.getBoolean(KEY_ENABLED, true);
  }

  public void setOverlayEnabled(boolean enabled)
  {
    prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  @Nullable
  public Placement getPlacement(@NonNull String widgetKey)
  {
    String raw = prefs.getString(KEY_PREFIX + widgetKey, null);
    if (raw == null)
      return null;
    String[] parts = raw.split("\\|");
    if (parts.length != 3)
      return null;
    try
    {
      return new Placement(Panel.valueOf(parts[0]), Mode.valueOf(parts[1]), Integer.parseInt(parts[2]));
    }
    catch (IllegalArgumentException e)
    {
      return null;
    }
  }

  public void setPlacement(@NonNull String widgetKey, @NonNull Panel panel, @NonNull Mode mode, int order)
  {
    prefs.edit().putString(KEY_PREFIX + widgetKey, panel.name() + "|" + mode.name() + "|" + order).apply();
  }

  public void removePlacement(@NonNull String widgetKey)
  {
    prefs.edit().remove(KEY_PREFIX + widgetKey).apply();
  }

  /**
   * Ilk kurulum: hicbir yerlesim kaydi yoksa OsmAnd benzeri makul bir
   * varsayilan uretir (hiz -> sag, saat -> sol, navigasyon -> alt).
   * Kayit varsa hicbir sey yapmaz; her acilista guvenle cagrilabilir.
   */
  public void ensureDefaults(@NonNull java.util.List<String> widgetKeysByIdPrefix)
  {
    if (isConfigured())
      return;
    int order = 0;
    for (String key : widgetKeysByIdPrefix)
    {
      MapWidgetPlacementStore.Panel panel;
      if (key.startsWith("speed"))
        panel = MapWidgetPlacementStore.Panel.RIGHT;
      else if (key.startsWith("clock") || key.startsWith("classic"))
        panel = MapWidgetPlacementStore.Panel.LEFT;
      else if (key.startsWith("navigation"))
        panel = MapWidgetPlacementStore.Panel.BOTTOM;
      else
        panel = MapWidgetPlacementStore.Panel.NONE;
      if (panel != MapWidgetPlacementStore.Panel.NONE)
        setPlacement(key, panel, MapWidgetPlacementStore.Mode.COMPACT, order++);
    }
    prefs.edit().putBoolean(KEY_CONFIGURED, true).apply();
  }

  private boolean isConfigured()
  {
    return prefs.getBoolean(KEY_CONFIGURED, false) || !getAllPlacements().isEmpty();
  }

  @NonNull
  public Map<String, Placement> getAllPlacements()
  {
    Map<String, Placement> result = new HashMap<>();
    for (Map.Entry<String, ?> entry : prefs.getAll().entrySet())
    {
      if (!entry.getKey().startsWith(KEY_PREFIX))
        continue;
      String key = entry.getKey().substring(KEY_PREFIX.length());
      String raw = String.valueOf(entry.getValue());
      String[] parts = raw.split("\\|");
      if (parts.length != 3)
        continue;
      try
      {
        result.put(key, new Placement(Panel.valueOf(parts[0]), Mode.valueOf(parts[1]), Integer.parseInt(parts[2])));
      }
      catch (IllegalArgumentException ignored) {}
    }
    return result;
  }
}
