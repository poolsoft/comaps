package app.organicmaps.carlauncher.widgets.map;

import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

/**
 * Harita uzeri widget yerlesimi ayarlari (liste tabanli).
 * Her widget icin: harita paneli (Yok/Ust/Sol/Sag/Alt) + gorunum (Compact/Wide).
 * Kaydet dugmesine basincaya kadar degisiklikler uygulanmaz.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public final class MapWidgetPlacementDialog extends Dialog
{
  public interface OnApplyListener
  {
    void onApplied();
  }

  private static final String[] PANEL_LABELS = { "Yok (yan panel)", "Üst", "Sol", "Sağ", "Alt" };
  private static final MapWidgetPlacementStore.Panel[] PANEL_VALUES = {
      MapWidgetPlacementStore.Panel.NONE,
      MapWidgetPlacementStore.Panel.TOP,
      MapWidgetPlacementStore.Panel.LEFT,
      MapWidgetPlacementStore.Panel.RIGHT,
      MapWidgetPlacementStore.Panel.BOTTOM
  };
  private static final String[] MODE_LABELS = { "Compact", "Wide" };
  private static final MapWidgetPlacementStore.Mode[] MODE_VALUES = {
      MapWidgetPlacementStore.Mode.COMPACT,
      MapWidgetPlacementStore.Mode.WIDE
  };

  private final WidgetManager widgetManager;
  private final MapWidgetPlacementStore store;
  @NonNull
  private final OnApplyListener applyListener;

  private final java.util.Map<String, MapWidgetPlacementStore.Panel> pendingPanels = new java.util.HashMap<>();
  private final java.util.Map<String, MapWidgetPlacementStore.Mode> pendingModes = new java.util.HashMap<>();
  private boolean pendingEnabled;

  public MapWidgetPlacementDialog(@NonNull Context context, @NonNull WidgetManager widgetManager,
                                  @NonNull MapWidgetPlacementStore store,
                                  @NonNull OnApplyListener applyListener)
  {
    super(context);
    this.widgetManager = widgetManager;
    this.store = store;
    this.applyListener = applyListener;
    this.pendingEnabled = store.isOverlayEnabled();
    setupDialog();
  }

  private void setupDialog()
  {
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    ScrollView scroll = new ScrollView(getContext());
    LinearLayout main = new LinearLayout(getContext());
    main.setOrientation(LinearLayout.VERTICAL);
    main.setBackgroundColor(0xFF1A1A1A);
    int pad = dp(20);
    main.setPadding(pad, pad, pad, pad);

    TextView title = new TextView(getContext());
    title.setText("Harita Widget Yerleşimi");
    title.setTextColor(0xFFFFFFFF);
    title.setTextSize(18);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    title.setPadding(0, 0, 0, dp(12));
    main.addView(title);

    Switch overlaySwitch = new Switch(getContext());
    overlaySwitch.setText("Harita üzeri widget'lar açık");
    overlaySwitch.setTextColor(0xFFE0E0E0);
    overlaySwitch.setChecked(pendingEnabled);
    overlaySwitch.setOnCheckedChangeListener((b, checked) -> pendingEnabled = checked);
    main.addView(overlaySwitch, marginLayoutParams(dp(0), dp(12)));

    List<BaseWidget> widgets = widgetManager.getAllWidgets();
    for (BaseWidget widget : widgets)
    {
      main.addView(buildWidgetRow(widget), marginLayoutParams(dp(0), dp(2)));
    }

    LinearLayout buttons = new LinearLayout(getContext());
    buttons.setOrientation(LinearLayout.HORIZONTAL);
    buttons.setGravity(Gravity.END);

    Button cancel = new Button(getContext());
    cancel.setText("Vazgeç");
    cancel.setBackgroundColor(0xFF333333);
    cancel.setTextColor(0xFFE0E0E0);
    cancel.setOnClickListener(v -> dismiss());
    buttons.addView(cancel, marginLayoutParams(dp(8), dp(0)));

    Button apply = new Button(getContext());
    apply.setText("Uygula");
    apply.setBackgroundColor(0xFF2E7D32);
    apply.setTextColor(0xFFFFFFFF);
    apply.setOnClickListener(v ->
    {
      store.setOverlayEnabled(pendingEnabled);
      for (BaseWidget widget : widgetManager.getAllWidgets())
      {
        String key = MapWidgetsOverlay.placementKey(widget);
        MapWidgetPlacementStore.Panel panel = pendingPanels.get(key);
        MapWidgetPlacementStore.Mode mode = pendingModes.get(key);
        if (panel == null)
          continue;
        if (panel == MapWidgetPlacementStore.Panel.NONE)
        {
          store.removePlacement(key);
        }
        else
        {
          MapWidgetPlacementStore.Mode resolved =
              mode != null ? mode : MapWidgetPlacementStore.Mode.COMPACT;
          store.setPlacement(key, panel, resolved, widget.getOrder());
        }
      }
      applyListener.onApplied();
      dismiss();
    });
    buttons.addView(apply, marginLayoutParams(dp(0), dp(0)));

    main.addView(buttons, marginLayoutParams(dp(0), dp(8)));
    scroll.addView(main);

    setContentView(scroll);
    Window window = getWindow();
    if (window != null)
    {
      window.setLayout(dp(440), dp(520));
      window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF101010));
    }
  }

  @NonNull
  private View buildWidgetRow(@NonNull final BaseWidget widget)
  {
    String key = MapWidgetsOverlay.placementKey(widget);
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(6), 0, dp(6));

    TextView name = new TextView(getContext());
    name.setText(widget.getTitle());
    name.setTextColor(0xFFE0E0E0);
    name.setTextSize(14);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    row.addView(name, nameParams);

    MapWidgetPlacementStore.Placement current = store.getPlacement(key);
    MapWidgetPlacementStore.Panel currentPanel =
        current != null ? current.panel : MapWidgetPlacementStore.Panel.NONE;
    MapWidgetPlacementStore.Mode currentMode =
        current != null ? current.mode : MapWidgetPlacementStore.Mode.COMPACT;

    final Spinner panelSpinner = new Spinner(getContext());
    ArrayAdapter<String> panelAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, PANEL_LABELS);
    panelSpinner.setAdapter(panelAdapter);
    panelSpinner.setSelection(indexOf(PANEL_VALUES, currentPanel));
    panelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
    {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
      {
        pendingPanels.put(key, PANEL_VALUES[position]);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    row.addView(panelSpinner, new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));

    final Spinner modeSpinner = new Spinner(getContext());
    ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, MODE_LABELS);
    modeSpinner.setAdapter(modeAdapter);
    modeSpinner.setSelection(indexOf(MODE_VALUES, currentMode));
    modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
    {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
      {
        pendingModes.put(key, MODE_VALUES[position]);
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    row.addView(modeSpinner, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

    return row;
  }

  private static int indexOf(@NonNull MapWidgetPlacementStore.Panel[] values, @NonNull MapWidgetPlacementStore.Panel value)
  {
    for (int i = 0; i < values.length; i++)
      if (values[i] == value)
        return i;
    return 0;
  }

  private static int indexOf(@NonNull MapWidgetPlacementStore.Mode[] values, @NonNull MapWidgetPlacementStore.Mode value)
  {
    for (int i = 0; i < values.length; i++)
      if (values[i] == value)
        return i;
    return 0;
  }

  @NonNull
  private LinearLayout.LayoutParams marginLayoutParams(int marginLeft, int marginTop)
  {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    lp.setMargins(marginLeft, marginTop, 0, 0);
    return lp;
  }

  private int dp(int value)
  {
    return Math.round(value * getContext().getResources().getDisplayMetrics().density);
  }
}
