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

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

/**
 * Haritanin uzerine binen OsmAnd tarzi widget overlay'i.
 * Dort kenarda dikey/yatay kolonlar (panel) vardir; WidgetManager'daki
 * widget'larin bir kismi buraya yerlesir (MapWidgetPlacementStore ile),
 * bir kismi yan panelde kalir. Kolonlar dokunma olaylarini haritaya
 * gecirmez ama bos alanlar gecirgendir.
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
  /** Panel -> o panelde gorunen widget keyleri (instance id oncelikli). */
  private final Map<String, BaseWidget> attachedWidgets = new LinkedHashMap<>();
  private boolean mRefreshing;

  public MapWidgetsOverlay(@NonNull Context context, @NonNull MapWidgetPlacementStore store)
  {
    super(context);
    this.store = store;
    setClickable(false);
    setFocusable(false);
    buildPanels();
  }

  private void buildPanels()
  {
    for (int ordinal : PANEL_ORDER)
    {
      LinearLayout column = new LinearLayout(getContext());
      column.setOrientation(
          ordinal == MapWidgetPlacementStore.Panel.TOP.ordinal()
              || ordinal == MapWidgetPlacementStore.Panel.BOTTOM.ordinal()
              ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
      // Bos alanlar haritaya dokunmayi gecirsin; widget'larin kendisi clickable.
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
      lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.TOP;
      lp.topMargin = dp(8);
      lp.leftMargin = dp(8);
      lp.rightMargin = dp(8);
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.BOTTOM.ordinal())
    {
      lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      lp.gravity = Gravity.BOTTOM;
      lp.bottomMargin = dp(8);
      lp.leftMargin = dp(8);
      lp.rightMargin = dp(8);
    }
    else if (panelOrdinal == MapWidgetPlacementStore.Panel.LEFT.ordinal())
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
      lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
      lp.leftMargin = dp(8);
      lp.topMargin = dp(56);
      lp.bottomMargin = dp(56);
    }
    else
    {
      lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
      lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
      lp.rightMargin = dp(8);
      lp.topMargin = dp(56);
      lp.bottomMargin = dp(56);
    }
    return lp;
  }

  /**
   * WidgetManager'daki tum widget'larin yerlesimine gore overlay icerigini
   * tazeler. Yalnizca harita paneline atanmis ve gorunur widget'lar burada
   * gorunur; NONE (veya yerlesimi olmayan) widget'lar yan panelde kalir.
   */
  public void refresh(@NonNull WidgetManager widgetManager)
  {
    if (mRefreshing)
      return;
    mRefreshing = true;
    try
    {
      refreshInternal(widgetManager);
    }
    finally
    {
      mRefreshing = false;
    }
  }

  private void refreshInternal(@NonNull WidgetManager widgetManager)
  {
    detachAll();

    if (!store.isOverlayEnabled())
    {
      // NOTE: listener burada cagrilmaz; cagirirsak activity tekrar refresh()
      // yapar ve sonsuz dongu (StackOverflowError) olusur.
      return;
    }

    // Key -> widget (instance id; ayni widget hem yan panel hem harita
    // overlay'inde olamaz, instance view'i tek parent'a eklenebilir).
    List<BaseWidget> candidates = new ArrayList<>();
    for (BaseWidget widget : widgetManager.getAllWidgets())
    {
      if (!widget.isVisible())
        continue;
      MapWidgetPlacementStore.Placement placement =
          store.getPlacement(placementKey(widget));
      if (placement == null || placement.panel == MapWidgetPlacementStore.Panel.NONE)
        continue;
      candidates.add(widget);
    }

    // Panele ve order'a gore grupla.
    for (int ordinal : PANEL_ORDER)
    {
      MapWidgetPlacementStore.Panel panel = MapWidgetPlacementStore.Panel.values()[ordinal];
      List<BaseWidget> panelWidgets = new ArrayList<>();
      for (BaseWidget widget : candidates)
      {
        MapWidgetPlacementStore.Placement p = store.getPlacement(placementKey(widget));
        if (p != null && p.panel == panel)
          panelWidgets.add(widget);
      }
      Collections.sort(panelWidgets, (a, b) ->
      {
        MapWidgetPlacementStore.Placement pa = store.getPlacement(placementKey(a));
        MapWidgetPlacementStore.Placement pb = store.getPlacement(placementKey(b));
        int oa = pa != null ? pa.order : 0;
        int ob = pb != null ? pb.order : 0;
        return Integer.compare(oa, ob);
      });

      LinearLayout column = panelColumns[ordinal];
      for (BaseWidget widget : panelWidgets)
      {
        View view = widget.getRootView();
        if (view == null)
          view = widget.createView();
        widget.onStart();
        if (view.getParent() instanceof ViewGroup)
          ((ViewGroup) view.getParent()).removeView(view);

        MapWidgetPlacementStore.Placement p = store.getPlacement(placementKey(widget));
        boolean wide = p != null && p.mode == MapWidgetPlacementStore.Mode.WIDE;
        column.addView(view, generateWidgetParams(ordinal, wide));
        attachedWidgets.put(placementKey(widget), widget);
      }
    }

    // Layout degisti bildirimi: refresh() cagrisindan DONMEDEN once yapilmamali
    // (activity handler'i tekrar refresh() cagiriyor -> recursion). Cagrilmayacak;
    // cagiran taraf zaten refresh sonrasi kendi layout'unu gunceller.
  }

  @NonNull
  private LayoutParams generateWidgetParams(int panelOrdinal, boolean wide)
  {
    boolean horizontal = panelOrdinal == MapWidgetPlacementStore.Panel.LEFT.ordinal()
        || panelOrdinal == MapWidgetPlacementStore.Panel.RIGHT.ordinal();
    LayoutParams lp;
    if (horizontal)
    {
      lp = new LayoutParams(wide ? dp(220) : LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT);
    }
    else
    {
      lp = new LayoutParams(wide ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT,
          LayoutParams.WRAP_CONTENT);
    }
    lp.bottomMargin = dp(6);
    lp.topMargin = dp(0);
    if (horizontal)
    {
      lp.bottomMargin = dp(0);
      lp.topMargin = dp(6);
    }
    return lp;
  }

  private void detachAll()
  {
    for (BaseWidget widget : attachedWidgets.values())
    {
      widget.onStop();
      View view = widget.getRootView();
      if (view != null && view.getParent() instanceof ViewGroup)
        ((ViewGroup) view.getParent()).removeView(view);
    }
    attachedWidgets.clear();
    for (int ordinal : PANEL_ORDER)
      panelColumns[ordinal].removeAllViews();
  }

  /**
   * Yan panel ile harita overlay'i ayni widget instance'ini payisamaz:
   * haritaya yerlesen widget, yan panel tarafindan gizlenmeli. WidgetManager
   * tarafindan filtreleme icin yardimci.
   */
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
