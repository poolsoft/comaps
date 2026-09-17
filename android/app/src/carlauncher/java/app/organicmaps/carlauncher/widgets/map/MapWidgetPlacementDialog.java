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

import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.carlauncher.widgets.WidgetManager;
import app.organicmaps.carlauncher.widgets.WidgetRegistry;

/**
 * Harita uzeri widget ve gosterge yerlesim ayarlari.
 * Hiz kapsulu konumu (Sol/Sag/Kapali), Saat (Acik/Kapali),
 * Ulasim modlari kapsulu (Modes UI) ve modul panelleri yonetilir.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public final class MapWidgetPlacementDialog extends Dialog
{
  public interface OnApplyListener
  {
    void onApplied();
  }

  private static final String[] PANEL_LABELS = { "Yok (Gizli)", "Üst Panel", "Sol Panel", "Sağ Panel", "Alt Panel" };
  private static final MapWidgetPlacementStore.Panel[] PANEL_VALUES = {
      MapWidgetPlacementStore.Panel.NONE,
      MapWidgetPlacementStore.Panel.TOP,
      MapWidgetPlacementStore.Panel.LEFT,
      MapWidgetPlacementStore.Panel.RIGHT,
      MapWidgetPlacementStore.Panel.BOTTOM
  };
  private static final String[] MODE_LABELS = { "Kompakt", "Geniş" };
  private static final MapWidgetPlacementStore.Mode[] MODE_VALUES = {
      MapWidgetPlacementStore.Mode.COMPACT,
      MapWidgetPlacementStore.Mode.WIDE
  };

  private static final String[] SPEED_POS_LABELS = { "Sol Kenar (Önerilen)", "Sağ Kenar", "Gizli (Kapalı)" };
  private static final String[] SPEED_POS_VALUES = { "left", "right", "none" };

  private final MapWidgetPlacementStore store;
  private final CarLauncherSettings settings;
  @NonNull
  private final OnApplyListener applyListener;

  private final java.util.Map<String, MapWidgetPlacementStore.Panel> pendingPanels = new java.util.HashMap<>();
  private final java.util.Map<String, MapWidgetPlacementStore.Mode> pendingModes = new java.util.HashMap<>();
  private boolean pendingEnabled;
  private String pendingSpeedPos;
  private boolean pendingClockEnabled;
  private boolean pendingModesUiEnabled;

  public MapWidgetPlacementDialog(@NonNull Context context,
                                  @NonNull MapWidgetPlacementStore store,
                                  @NonNull OnApplyListener applyListener)
  {
    super(context);
    this.store = store;
    this.settings = new CarLauncherSettings(context);
    this.applyListener = applyListener;

    this.pendingEnabled = store.isOverlayEnabled();
    this.pendingSpeedPos = settings.getMapSpeedPosition();
    this.pendingClockEnabled = settings.isMapClockEnabled();
    this.pendingModesUiEnabled = settings.isModesUiEnabled();

    setupDialog();
  }

  public MapWidgetPlacementDialog(@NonNull Context context,
                                  @NonNull WidgetManager widgetManager,
                                  @NonNull MapWidgetPlacementStore store,
                                  @NonNull OnApplyListener applyListener)
  {
    this(context, store, applyListener);
  }

  private void setupDialog()
  {
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    ScrollView scroll = new ScrollView(getContext());
    LinearLayout main = new LinearLayout(getContext());
    main.setOrientation(LinearLayout.VERTICAL);
    main.setBackgroundColor(0xFF16191F);
    int pad = dp(20);
    main.setPadding(pad, pad, pad, pad);

    TextView title = new TextView(getContext());
    title.setText("Harita Göstergeleri & Widget Ayarları");
    title.setTextColor(0xFFFFFFFF);
    title.setTextSize(18);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    title.setPadding(0, 0, 0, dp(14));
    main.addView(title);

    // 1. Hiz Kapsulu Konumu (Sol / Sag / Kapali)
    LinearLayout speedRow = new LinearLayout(getContext());
    speedRow.setOrientation(LinearLayout.HORIZONTAL);
    speedRow.setGravity(Gravity.CENTER_VERTICAL);
    speedRow.setPadding(0, dp(4), 0, dp(8));

    TextView speedLabel = new TextView(getContext());
    speedLabel.setText("Hız & Limit Göstergesi:");
    speedLabel.setTextColor(0xFFE0E0E0);
    speedLabel.setTextSize(14);
    speedRow.addView(speedLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

    Spinner speedSpinner = new Spinner(getContext());
    ArrayAdapter<String> speedAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, SPEED_POS_LABELS);
    speedSpinner.setAdapter(speedAdapter);
    int initialSpeedIndex = 0;
    for (int i = 0; i < SPEED_POS_VALUES.length; i++)
    {
      if (SPEED_POS_VALUES[i].equalsIgnoreCase(pendingSpeedPos))
      {
        initialSpeedIndex = i;
        break;
      }
    }
    speedSpinner.setSelection(initialSpeedIndex);
    speedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
    {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
      {
        pendingSpeedPos = SPEED_POS_VALUES[position];
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    speedRow.addView(speedSpinner, new LinearLayout.LayoutParams(dp(170), LinearLayout.LayoutParams.WRAP_CONTENT));
    main.addView(speedRow);

    // 2. Dijital Saat Switch
    Switch clockSwitch = new Switch(getContext());
    clockSwitch.setText("Haritada Dijital Saat Göster");
    clockSwitch.setTextColor(0xFFE0E0E0);
    clockSwitch.setChecked(pendingClockEnabled);
    clockSwitch.setOnCheckedChangeListener((b, checked) -> pendingClockEnabled = checked);
    main.addView(clockSwitch, marginLayoutParams(dp(0), dp(6)));

    // 3. Ulasim Modlari Kapsulu (Modes UI) Switch
    Switch modesSwitch = new Switch(getContext());
    modesSwitch.setText("Ulaşım Modları Kapsülü (Modes UI - 🚶 🚴 🚗 🚌)");
    modesSwitch.setTextColor(0xFFE0E0E0);
    modesSwitch.setChecked(pendingModesUiEnabled);
    modesSwitch.setOnCheckedChangeListener((b, checked) -> pendingModesUiEnabled = checked);
    main.addView(modesSwitch, marginLayoutParams(dp(0), dp(6)));

    // Ayrac
    View divider = new View(getContext());
    divider.setBackgroundColor(0x33FFFFFF);
    main.addView(divider, marginLayoutParams(dp(0), dp(14)));

    TextView sectionTitle = new TextView(getContext());
    sectionTitle.setText("OsmAnd Tarzı Modüler Kenar Panelleri");
    sectionTitle.setTextColor(0xFF90CAF9);
    sectionTitle.setTextSize(14);
    sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
    sectionTitle.setPadding(0, dp(8), 0, dp(4));
    main.addView(sectionTitle);

    Switch overlaySwitch = new Switch(getContext());
    overlaySwitch.setText("Kenar Panellerini Aktif Et");
    overlaySwitch.setTextColor(0xFFE0E0E0);
    overlaySwitch.setChecked(pendingEnabled);
    overlaySwitch.setOnCheckedChangeListener((b, checked) -> pendingEnabled = checked);
    main.addView(overlaySwitch, marginLayoutParams(dp(0), dp(6)));

    List<WidgetRegistry.WidgetEntry> entries = WidgetRegistry.getAvailableWidgets();
    for (WidgetRegistry.WidgetEntry entry : entries)
    {
      main.addView(buildWidgetRow(entry), marginLayoutParams(dp(0), dp(4)));
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
    apply.setBackgroundColor(0xFF2563EB);
    apply.setTextColor(0xFFFFFFFF);
    apply.setOnClickListener(v ->
    {
      // 1. Yeni Ayarlari Kaydet
      settings.setMapSpeedPosition(pendingSpeedPos);
      settings.setMapClockEnabled(pendingClockEnabled);
      settings.setModesUiEnabled(pendingModesUiEnabled);

      // 2. Moduler Panel Ayarlarini Kaydet
      store.setOverlayEnabled(pendingEnabled);
      int order = 0;
      for (WidgetRegistry.WidgetEntry entry : WidgetRegistry.getAvailableWidgets())
      {
        String key = entry.typeId;
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
          store.setPlacement(key, panel, resolved, order++);
        }
      }

      applyListener.onApplied();
      dismiss();
    });
    buttons.addView(apply, marginLayoutParams(dp(0), dp(0)));

    main.addView(buttons, marginLayoutParams(dp(0), dp(18)));
    scroll.addView(main);

    setContentView(scroll);
    Window window = getWindow();
    if (window != null)
    {
      window.setLayout(dp(480), dp(540));
      window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF101216));
    }
  }

  @NonNull
  private View buildWidgetRow(@NonNull final WidgetRegistry.WidgetEntry entry)
  {
    String key = entry.typeId;
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(6), 0, dp(6));

    TextView name = new TextView(getContext());
    name.setText(entry.displayName);
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
