package app.organicmaps.carlauncher.widgets.map;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
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
import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.carlauncher.ui.MapSpeedWidgetView;
import app.organicmaps.carlauncher.ui.ModesUiView;
import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;
import app.organicmaps.carlauncher.widgets.WidgetRegistry;

/**
 * Haritanin uzerine binen OsmAnd tarzi widget overlay'i.
 * Dokunmatik gecirgendir (click-through), harita hareketlerini kesinlikle engellemez.
 * Hiz gostergesi (Sol/Sag/Kapali), Saat ve Ust Ulasim Modlari Kapsulu barindirir.
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
  private final CarLauncherSettings settings;
  private final LinearLayout[] panelColumns = new LinearLayout[4];
  /** Harita panelinde gosterilen bagimsiz widget ornekleri (typeId -> BaseWidget) */
  private final Map<String, BaseWidget> attachedWidgets = new LinkedHashMap<>();

  private MapSpeedWidgetView mapSpeedView;
  private boolean mRefreshing;

  public MapWidgetsOverlay(@NonNull Context context, @NonNull MapWidgetPlacementStore store)
  {
    super(context);
    this.store = store;
    this.settings = new CarLauncherSettings(context);

    setClickable(false);
    setFocusable(false);

    buildPanels();
    buildFixedOverlays();
  }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev)
  {
    // Overlay katmani dokunmatik eventleri asla yutmaz, haritaya gecirir
    return false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event)
  {
    // Kendi zeminine gelen dokunuslari asla tuketmez (click-through)
    return false;
  }

  private void buildPanels()
  {
    for (int ordinal : PANEL_ORDER)
    {
      LinearLayout column = new LinearLayout(getContext());
      boolean isVertical = ordinal == MapWidgetPlacementStore.Panel.LEFT.ordinal()
          || ordinal == MapWidgetPlacementStore.Panel.RIGHT.ordinal();
      column.setOrientation(isVertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
      column.setClickable(false);
      column.setFocusable(false);
      panelColumns[ordinal] = column;
      addView(column, generateColumnParams(ordinal));
    }
  }

  private void buildFixedOverlays()
  {
    // Canli Hiz ve Limit Kapsulu (Sol veya Sag)
    mapSpeedView = new MapSpeedWidgetView(getContext());
    addView(mapSpeedView, generateSpeedParams());

    updateFixedOverlaysVisibility();
  }

  @NonNull
  private LayoutParams generateSpeedParams()
  {
    LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    String pos = settings.getMapSpeedPosition();
    if ("right".equalsIgnoreCase(pos))
    {
      lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
      lp.rightMargin = dp(16);
    }
    else
    {
      // Varsayilan SOL
      lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
      lp.leftMargin = dp(16);
    }
    return lp;
  }

  public void updateFixedOverlaysVisibility()
  {
    // Hiz widgeti gorunurlugu ve konumu
    if (mapSpeedView != null)
    {
      String pos = settings.getMapSpeedPosition();
      if ("none".equalsIgnoreCase(pos))
      {
        mapSpeedView.setVisibility(View.GONE);
      }
      else
      {
        mapSpeedView.setVisibility(View.VISIBLE);
        mapSpeedView.setLayoutParams(generateSpeedParams());
        mapSpeedView.update();
      }
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
      lp.topMargin = dp(60); // Modes UI kapsulunun altina gelsin
      lp.leftMargin = dp(120);
      lp.rightMargin = dp(60);
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.BOTTOM.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
      lp.bottomMargin = dp(16);
      lp.leftMargin = dp(70);
      lp.rightMargin = dp(60);
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.LEFT.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.START | Gravity.TOP;
      lp.leftMargin = dp(16);
      lp.topMargin = dp(68);
    }
    else // RIGHT
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.END | Gravity.TOP;
      lp.rightMargin = dp(16);
      lp.topMargin = dp(60);
    }
    return lp;
  }

  /**
   * Periyodik veri tazeleme: canli hiz/saat ve widget verilerini gunceller.
   */
  public void tick()
  {
    if (mapSpeedView != null && mapSpeedView.getVisibility() == View.VISIBLE)
    {
      mapSpeedView.update();
    }

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
      updateFixedOverlaysVisibility();
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
