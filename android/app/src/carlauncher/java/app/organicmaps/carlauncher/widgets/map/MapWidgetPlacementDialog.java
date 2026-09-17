package app.organicmaps.carlauncher.widgets.map;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
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
 * Sabit alt buton bari (Pinned Footer) sayesinde hem dikey hem yatay ekranlarda
 * Vazgec ve Uygula butonlari her zaman gorunur ve basilabilir kalir.
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

    // KOK CONTAINER (Tum pencereyi kaplar, Dikey)
    LinearLayout root = new LinearLayout(getContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xFF16191F);
    int pad = dp(16);
    root.setPadding(pad, pad, pad, pad);

    // 1. SABIT UST BASLIK BAR (Header)
    TextView title = new TextView(getContext());
    title.setText("Harita Göstergeleri & Widget Ayarları");
    title.setTextColor(0xFFFFFFFF);
    title.setTextSize(17);
    title.setTypeface(null, Typeface.BOLD);
    title.setPadding(0, 0, 0, dp(10));
    root.addView(title);

    // 2. KAYDIRILABILIR ICERIK ALANI (ScrollView weight=1)
    ScrollView scroll = new ScrollView(getContext());
    scroll.setFillViewport(true);
    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
    root.addView(scroll, scrollParams);

    LinearLayout content = new LinearLayout(getContext());
    content.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(content);

    // 2.A. Hiz Kapsulu Konumu (Sol / Sag / Kapali)
    LinearLayout speedRow = new LinearLayout(getContext());
    speedRow.setOrientation(LinearLayout.HORIZONTAL);
    speedRow.setGravity(Gravity.CENTER_VERTICAL);
    speedRow.setPadding(0, dp(6), 0, dp(8));

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
    speedRow.addView(speedSpinner, new LinearLayout.LayoutParams(dp(165), LinearLayout.LayoutParams.WRAP_CONTENT));
    content.addView(speedRow);

    // 2.B. Dijital Saat Switch
    Switch clockSwitch = new Switch(getContext());
    clockSwitch.setText("Harita Hız Göstergesinde Saat Göster");
    clockSwitch.setTextColor(0xFFE0E0E0);
    clockSwitch.setChecked(pendingClockEnabled);
    clockSwitch.setOnCheckedChangeListener((b, checked) -> pendingClockEnabled = checked);
    content.addView(clockSwitch, marginLayoutParams(dp(0), dp(4)));

    // Ayrac
    View divider = new View(getContext());
    divider.setBackgroundColor(0x33FFFFFF);
    content.addView(divider, marginLayoutParams(dp(0), dp(12)));

    // 2.C. Moduler Kenar Panelleri Bolumu
    TextView sectionTitle = new TextView(getContext());
    sectionTitle.setText("OsmAnd Tarzı Modüler Kenar Panelleri");
    sectionTitle.setTextColor(0xFF90CAF9);
    sectionTitle.setTextSize(14);
    sectionTitle.setTypeface(null, Typeface.BOLD);
    sectionTitle.setPadding(0, dp(4), 0, dp(4));
    content.addView(sectionTitle);

    Switch overlaySwitch = new Switch(getContext());
    overlaySwitch.setText("Kenar Panellerini Aktif Et");
    overlaySwitch.setTextColor(0xFFE0E0E0);
    overlaySwitch.setChecked(pendingEnabled);
    overlaySwitch.setOnCheckedChangeListener((b, checked) -> pendingEnabled = checked);
    content.addView(overlaySwitch, marginLayoutParams(dp(0), dp(4)));

    List<WidgetRegistry.WidgetEntry> entries = WidgetRegistry.getAvailableWidgets();
    for (WidgetRegistry.WidgetEntry entry : entries)
    {
      content.addView(buildWidgetRow(entry), marginLayoutParams(dp(0), dp(4)));
    }

    // 3. SABIT ALT BUTON BARI (PINNED FOOTER - Asla scroll olmaz, her zaman gorunur)
    LinearLayout footer = new LinearLayout(getContext());
    footer.setOrientation(LinearLayout.HORIZONTAL);
    footer.setGravity(Gravity.END);
    footer.setPadding(0, dp(10), 0, 0);

    Button cancel = new Button(getContext());
    cancel.setText("Vazgeç");
    cancel.setBackgroundColor(0xFF333333);
    cancel.setTextColor(0xFFE0E0E0);
    cancel.setOnClickListener(v -> dismiss());
    LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    cancelParams.rightMargin = dp(10);
    footer.addView(cancel, cancelParams);

    Button apply = new Button(getContext());
    apply.setText("Uygula");
    apply.setBackgroundColor(0xFF2563EB);
    apply.setTextColor(0xFFFFFFFF);
    apply.setOnClickListener(v ->
    {
      // 1. Hiz ve Saat Ayarlarini Kaydet
      settings.setMapSpeedPosition(pendingSpeedPos);
      settings.setMapClockEnabled(pendingClockEnabled);

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
    footer.addView(apply);

    root.addView(footer, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    setContentView(root);

    // Ekrana tam oturan esnek Window boyutlandirmasi
    Window window = getWindow();
    if (window != null)
    {
      DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
      int dialogWidth = Math.min(dp(500), (int)(dm.widthPixels * 0.92f));
      int dialogHeight = Math.min(dp(540), (int)(dm.heightPixels * 0.88f));
      window.setLayout(dialogWidth, dialogHeight);
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
