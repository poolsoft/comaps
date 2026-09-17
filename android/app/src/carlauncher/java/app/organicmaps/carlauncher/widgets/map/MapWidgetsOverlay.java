package app.organicmaps.carlauncher.widgets.map;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.CarLauncherActivity;
import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;
import app.organicmaps.carlauncher.widgets.WidgetRegistry;

/**
 * Haritanin uzerine binen OsmAnd tarzi widget overlay'i.
 * Dort kenarda dikey/yatay kolonlar (panel) vardir.
 * WidgetRegistry'deki widget'lar bagimsiz instance olarak olusturulup buraya eklenir,
 * boylece yan panel (WorkspaceCellLayout) ile hicbir View cakismasi yasanmaz.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public final class MapWidgetsOverlay extends FrameLayout
{
  private static final int[] PANEL_ORDER = {
      MapWidgetPlacementStore.Panel.TOP.ordinal(),
      MapWidgetPlacementStore.Panel.LEFT.ordinal(),
      MapWidgetPlacementStore.Panel.RIGHT.ordinal(),
      MapWidgetPlacementStore.Panel.BOTTOM.ordinal()
  };

  private final MapWidgetPlacementStore store;
  private final LinearLayout[] panelColumns = new LinearLayout[4];
  /** Harita panelinde gosterilen bagimsiz widget ornekleri (typeId -> BaseWidget) */
  private final Map<String, BaseWidget> attachedWidgets = new LinkedHashMap<>();
  private boolean mRefreshing;

  public MapWidgetsOverlay(@NonNull Context context, @NonNull MapWidgetPlacementStore store)
  {
    super(context);
    this.store = store;
    setClickable(false);
    setFocusable(false);
    buildPanels();

    setOnLongClickListener(v -> {
      if (getContext() instanceof CarLauncherActivity)
      {
        ((CarLauncherActivity) getContext()).showMapWidgetPlacementDialog();
        return true;
      }
      return false;
    });
  }

  private void buildPanels()
  {
    for (int ordinal : PANEL_ORDER)
    {
      LinearLayout column = new LinearLayout(getContext());
      // Sol ve sag paneller dikey (alt alta), ust ve alt paneller yatay (yan yana)
      boolean isVertical = ordinal == MapWidgetPlacementStore.Panel.LEFT.ordinal()
          || ordinal == MapWidgetPlacementStore.Panel.RIGHT.ordinal();
      column.setOrientation(isVertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
      column.setClickable(false);
      column.setFocusable(false);
      panelColumns[ordinal] = column;
      addView(column, generateColumnParams(ordinal));
    }
  }

  @NonNull
  private LayoutParams generateColumnParams(int panelOrdinal)
  {
    LayoutParams lp;
    if (panelOrdinal == MapWidgetPlacementStore.Panel.TOP.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
      lp.topMargin = dp(16);
      lp.leftMargin = dp(120); // Katman ve widget butonlarinin uzerine binmesin
      lp.rightMargin = dp(60); // Pusulanin uzerine binmesin
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.BOTTOM.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
      lp.bottomMargin = dp(16);
      lp.leftMargin = dp(70); // Arama ve favori butonlarindan uzak
      lp.rightMargin = dp(60); // Navigasyon okundan uzak
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.LEFT.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.START | Gravity.TOP;
      lp.leftMargin = dp(16);
      lp.topMargin = dp(68); // Katman butonunun hemen altinda temiz yerlesim
    }
    else // RIGHT
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.END | Gravity.TOP;
      lp.rightMargin = dp(16);
      lp.topMargin = dp(60); // Pusulanin hemen altinda temiz yerlesim
    }
    return lp;
  }

  /**
   * Periyodik veri tazeleme: view yapisini yeniden kurmadan, haritaya
   * yerlesmis widget'larin update() metodunu cagirir (hiz/saat canli).
   */
  public void tick()
  {
    for (BaseWidget widget : attachedWidgets.values())
    {
      try
      {
        widget.update();
      }
      catch (Exception ignored) {}
    }
  }

  /**
   * Harita uzeri widget'lari tazeler.
   */
  public void refresh()
  {
    if (mRefreshing)
      return;
    mRefreshing = true;
    try
    {
      refreshInternal();
    }
    finally
    {
      mRefreshing = false;
    }
  }

  public void refresh(@NonNull WidgetManager widgetManager)
  {
    refresh();
  }

  private void refreshInternal()
  {
    detachAll();

    if (!store.isOverlayEnabled())
      return;

    MwmApplication app = (MwmApplication) getContext().getApplicationContext();
    List<WidgetRegistry.WidgetEntry> available = WidgetRegistry.getAvailableWidgets();

    for (int ordinal : PANEL_ORDER)
    {
      MapWidgetPlacementStore.Panel panel = MapWidgetPlacementStore.Panel.values()[ordinal];
      List<WidgetRegistry.WidgetEntry> panelEntries = new ArrayList<>();
      for (WidgetRegistry.WidgetEntry entry : available)
      {
        MapWidgetPlacementStore.Placement p = store.getPlacement(entry.typeId);
        if (p != null && p.panel == panel)
          panelEntries.add(entry);
      }

      Collections.sort(panelEntries, (a, b) ->
      {
        MapWidgetPlacementStore.Placement pa = store.getPlacement(a.typeId);
        MapWidgetPlacementStore.Placement pb = store.getPlacement(b.typeId);
        int oa = pa != null ? pa.order : 0;
        int ob = pb != null ? pb.order : 0;
        return Integer.compare(oa, ob);
      });

      LinearLayout column = panelColumns[ordinal];
      for (WidgetRegistry.WidgetEntry entry : panelEntries)
      {
        BaseWidget widget = attachedWidgets.get(entry.typeId);
        if (widget == null)
        {
          widget = WidgetRegistry.createWidget(getContext(), app, entry.typeId);
          if (widget == null)
            continue;
          attachedWidgets.put(entry.typeId, widget);
        }

        View view = widget.getRootView();
        if (view == null)
          view = widget.createView();
        
        widget.onStart();
        widget.update();

        if (view.getParent() instanceof ViewGroup)
          ((ViewGroup) view.getParent()).removeView(view);

        MapWidgetPlacementStore.Placement p = store.getPlacement(entry.typeId);
        boolean wide = p != null && p.mode == MapWidgetPlacementStore.Mode.WIDE;
        column.addView(view, generateWidgetParams(ordinal, wide));
      }
    }
  }

  @NonNull
  private LinearLayout.LayoutParams generateWidgetParams(int panelOrdinal, boolean wide)
  {
    boolean isVertical = panelOrdinal == MapWidgetPlacementStore.Panel.LEFT.ordinal()
        || panelOrdinal == MapWidgetPlacementStore.Panel.RIGHT.ordinal();
    LinearLayout.LayoutParams lp;
    if (isVertical)
    {
      lp = new LinearLayout.LayoutParams(
          wide ? dp(200) : ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.bottomMargin = dp(8);
      lp.topMargin = dp(0);
    }
    else
    {
      lp = new LinearLayout.LayoutParams(
          wide ? dp(220) : ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.leftMargin = dp(6);
      lp.rightMargin = dp(6);
    }
    return lp;
  }

  private void detachAll()
  {
    for (int ordinal : PANEL_ORDER)
    {
      if (panelColumns[ordinal] != null)
        panelColumns[ordinal].removeAllViews();
    }
    // Tum view'lari temizle ama instance'lari saklayabilir veya durdurabiliriz
    for (BaseWidget widget : attachedWidgets.values())
    {
      widget.onStop();
      View view = widget.getRootView();
      if (view != null && view.getParent() instanceof ViewGroup)
        ((ViewGroup) view.getParent()).removeView(view);
    }
  }

  public boolean isWidgetAttachedToMap(@NonNull BaseWidget widget)
  {
    return attachedWidgets.containsKey(placementKey(widget));
  }

  public int getAttachedWidgetCount()
  {
    return attachedWidgets.size();
  }

  @NonNull
  public static String placementKey(@NonNull BaseWidget widget)
  {
    return widget.getId();
  }

  private int dp(int value)
  {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
