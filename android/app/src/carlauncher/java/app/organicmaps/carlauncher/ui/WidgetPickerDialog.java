package app.organicmaps.carlauncher.ui;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;

import app.organicmaps.carlauncher.widgets.DirectionWidget;
import app.organicmaps.carlauncher.widgets.MusicWidget;
import app.organicmaps.carlauncher.widgets.NavigationWidget;




import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Premium DialogFragment tabanli modern ve gruplanmis Widget Ekleme Arayuzu.
 * En ustte kendi widget'larimizi yatay kartlar halinde sunar.
 * Altinda sistem widget'larini uygulama bazinda yatay olarak gruplar.
 * Her widget siralama ve buyuk Canvas tabanli onizleme (Preview) alanlarina sahiptir.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WidgetPickerDialog extends DialogFragment {

    private static final int REQUEST_CREATE_APPWIDGET = 502;
    private static final int REQUEST_BIND_APPWIDGET = 503;

    private WidgetManager widgetManager;
    private Runnable onDismissCallback;
    private MwmApplication app;

    private int activePageIndex = 0;
    private int pendingAppWidgetId = -1;

    private LinearLayout mainContainer;

    public void setWidgetManager(WidgetManager wm) {
        this.widgetManager = wm;
    }

    public void setOnDismissCallback(Runnable callback) {
        this.onDismissCallback = callback;
    }

    public void setActivePageIndex(int pageIndex) {
        this.activePageIndex = pageIndex;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);
        if (getContext() != null) {
            app = (MwmApplication) getContext().getApplicationContext();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            // Ekranin %85 genisligi ve %85 yuksekligini kaplayan premium diyalog
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            getDialog().getWindow().setLayout(width, height);
            
            // Yuvarlatilmis premium koyu gri arka plan
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0xFC1A1A1A); // Premium Koyu Gri
            gd.setCornerRadius(dpToPx(16));
            getDialog().getWindow().setBackgroundDrawable(gd);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = getContext();
        if (ctx == null) return null;

        // Ana Dikey Layout
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(20), dpToPx(16), dpToPx(20), dpToPx(20));

        // Header (Baslik ve Kapat Butonu)
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dpToPx(12));

        TextView titleView = new TextView(ctx);
        titleView.setText("Widget Kutuphanesi");
        titleView.setTextSize(20);
        titleView.setTextColor(Color.WHITE);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleView.setLayoutParams(titleLp);
        header.addView(titleView);

        // Kapat Butonu (Orijinal modern X simgesi)
        ImageView closeBtn = new ImageView(ctx);
        closeBtn.setImageResource(app.organicmaps.R.drawable.ic_action_close);
        closeBtn.setColorFilter(Color.WHITE);
        
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        
        TypedValue outValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        closeBtn.setBackgroundResource(outValue.resourceId);
        
        closeBtn.setOnClickListener(v -> dismiss());
        header.addView(closeBtn);

        root.addView(header);

        // Scrollable Ana Icerik
        ScrollView scrollView = new ScrollView(ctx);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollLp);

        mainContainer = new LinearLayout(ctx);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(mainContainer);
        root.addView(scrollView);

        // Icerigi Doldur
        buildInterface(ctx);

        return root;
    }

    private void buildInterface(Context ctx) {
        if (mainContainer == null) return;
        mainContainer.removeAllViews();

        // 1. Kisim: Kendi Uygulamamizin Widget'lari (Yatay Kart Listesi)
        TextView localTitle = new TextView(ctx);
        localTitle.setText("Car Launcher Widget'lari");
        localTitle.setTextColor(Color.WHITE);
        localTitle.setTextSize(15);
        localTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        localTitle.setPadding(0, dpToPx(8), 0, dpToPx(8));
        mainContainer.addView(localTitle);

        HorizontalScrollView localScroll = new HorizontalScrollView(ctx);
        localScroll.setClipToPadding(false);
        localScroll.setPadding(0, 0, 0, dpToPx(16));
        
        LinearLayout localList = new LinearLayout(ctx);
        localList.setOrientation(LinearLayout.HORIZONTAL);

        List<WidgetInfo> localWidgets = getAvailableWidgets();
        for (WidgetInfo info : localWidgets) {
            localList.addView(createLocalWidgetCard(ctx, info));
        }
        localScroll.addView(localList);
        mainContainer.addView(localScroll);

        // Ayirici
        View divider = new View(ctx);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp.setMargins(0, dpToPx(8), 0, dpToPx(16));
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(0x1FFFFFFF);
        mainContainer.addView(divider);

        // Car Launcher Kisayollari (Yatay Kart Listesi) (Turkce karakter yok)
        TextView internalShortcutsTitle = new TextView(ctx);
        internalShortcutsTitle.setText("Car Launcher Kisayollari");
        internalShortcutsTitle.setTextColor(Color.WHITE);
        internalShortcutsTitle.setTextSize(15);
        internalShortcutsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        internalShortcutsTitle.setPadding(0, 0, 0, dpToPx(8));
        mainContainer.addView(internalShortcutsTitle);

        HorizontalScrollView internalShortcutsScroll = new HorizontalScrollView(ctx);
        internalShortcutsScroll.setClipToPadding(false);
        internalShortcutsScroll.setPadding(0, 0, 0, dpToPx(16));
        
        LinearLayout internalShortcutsList = new LinearLayout(ctx);
        internalShortcutsList.setOrientation(LinearLayout.HORIZONTAL);

        buildInternalShortcutsSection(ctx, internalShortcutsList);
        internalShortcutsScroll.addView(internalShortcutsList);
        mainContainer.addView(internalShortcutsScroll);

        // Ayirici Yeni (Turkce karakter yok)
        View dividerNew = new View(ctx);
        LinearLayout.LayoutParams divNewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divNewLp.setMargins(0, dpToPx(8), 0, dpToPx(16));
        dividerNew.setLayoutParams(divNewLp);
        dividerNew.setBackgroundColor(0x1FFFFFFF);
        mainContainer.addView(dividerNew);

        // 2. Kisim: Uygulama Kisayollari (Yatay Kart Listesi)
        TextView shortcutsTitle = new TextView(ctx);
        shortcutsTitle.setText("Uygulama Kisayollari");
        shortcutsTitle.setTextColor(Color.WHITE);
        shortcutsTitle.setTextSize(15);
        shortcutsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        shortcutsTitle.setPadding(0, 0, 0, dpToPx(8));
        mainContainer.addView(shortcutsTitle);

        HorizontalScrollView shortcutsScroll = new HorizontalScrollView(ctx);
        shortcutsScroll.setClipToPadding(false);
        shortcutsScroll.setPadding(0, 0, 0, dpToPx(16));
        
        LinearLayout shortcutsList = new LinearLayout(ctx);
        shortcutsList.setOrientation(LinearLayout.HORIZONTAL);

        buildShortcutsSection(ctx, shortcutsList);
        shortcutsScroll.addView(shortcutsList);
        mainContainer.addView(shortcutsScroll);

        // Ayirici 2
        View divider2 = new View(ctx);
        LinearLayout.LayoutParams divLp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp2.setMargins(0, dpToPx(8), 0, dpToPx(16));
        divider2.setLayoutParams(divLp2);
        divider2.setBackgroundColor(0x1FFFFFFF);
        mainContainer.addView(divider2);

        // 3. Kisim: Sistem Widget'lari (Paket Bazli Gruplanmis)
        TextView systemTitle = new TextView(ctx);
        systemTitle.setText("Sistem Uygulama Widget'lari");
        systemTitle.setTextColor(Color.WHITE);
        systemTitle.setTextSize(15);
        systemTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        systemTitle.setPadding(0, 0, 0, dpToPx(12));
        mainContainer.addView(systemTitle);

        buildSystemWidgetsSection(ctx);
    }

    private View createLocalWidgetCard(Context ctx, WidgetInfo info) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.rightMargin = dpToPx(12);
        card.setLayoutParams(cardLp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x0FFFFFFF);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke(dpToPx(1), 0x1FFFFFFF);
        card.setBackground(cardBg);

        // Canvas Tabanli Widget Onizleme (Preview)
        WidgetPreviewView preview = new WidgetPreviewView(ctx, info.type);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dpToPx(100), dpToPx(70));
        prevLp.bottomMargin = dpToPx(4);
        preview.setLayoutParams(prevLp);
        card.addView(preview);

        // Widget Baslik
        TextView title = new TextView(ctx);
        title.setText(info.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        // Widget Boyut
        String sizeText = "1x1";
        if (info.defaultSize == BaseWidget.WidgetSize.MEDIUM) sizeText = "2x1";
        else if (info.defaultSize == BaseWidget.WidgetSize.LARGE) sizeText = "2x2";

        TextView sizeLabel = new TextView(ctx);
        sizeLabel.setText(sizeText);
        sizeLabel.setTextColor(0x88FFFFFF);
        sizeLabel.setTextSize(10);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setPadding(0, 0, 0, dpToPx(4));
        card.addView(sizeLabel);

        // Ekle Butonu
        TextView btnAdd = new TextView(ctx);
        btnAdd.setText("Ekle");
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setTextSize(11);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(28));
        btnAdd.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x22FFFFFF);
        btnBg.setCornerRadius(dpToPx(14));
        btnAdd.setBackground(btnBg);
        btnAdd.setOnClickListener(v -> addNewWidget(info));

        card.addView(btnAdd);

        return card;
    }

    private void buildSystemWidgetsSection(Context ctx) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
        List<AppWidgetProviderInfo> providers = null;
        try {
            providers = appWidgetManager.getInstalledProviders();
        } catch (Exception e) {
            android.util.Log.e("WidgetPickerDialog", "getInstalledProviders hatasi: " + e.getMessage());
        }

        if (providers == null || providers.isEmpty()) {
            TextView emptyText = new TextView(ctx);
            emptyText.setText("Yuklu sistem widget'i bulunamadi.");
            emptyText.setTextColor(Color.GRAY);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dpToPx(32), 0, dpToPx(32));
            mainContainer.addView(emptyText);
            return;
        }

        android.content.pm.PackageManager pm = ctx.getPackageManager();
        
        // Uygulama paket adina gore widget'lari grupla
        Map<String, List<AppWidgetProviderInfo>> groupedProviders = new HashMap<>();
        for (AppWidgetProviderInfo provider : providers) {
            String pkg = provider.provider.getPackageName();
            if (!groupedProviders.containsKey(pkg)) {
                groupedProviders.put(pkg, new ArrayList<>());
            }
            groupedProviders.get(pkg).add(provider);
        }

        // Her bir uygulama grubu icin yatay liste olustur
        for (Map.Entry<String, List<AppWidgetProviderInfo>> entry : groupedProviders.entrySet()) {
            String pkg = entry.getKey();
            List<AppWidgetProviderInfo> appWidgets = entry.getValue();

            // Uygulama basligi ve ikonu
            LinearLayout appHeader = new LinearLayout(ctx);
            appHeader.setOrientation(LinearLayout.HORIZONTAL);
            appHeader.setGravity(Gravity.CENTER_VERTICAL);
            appHeader.setPadding(0, dpToPx(8), 0, dpToPx(6));

            ImageView appIconView = new ImageView(ctx);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
            iconLp.rightMargin = dpToPx(8);
            appIconView.setLayoutParams(iconLp);

            Drawable appIcon = null;
            String appLabel = pkg;
            try {
                appIcon = pm.getApplicationIcon(pkg);
                appLabel = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception e) {
                try {
                    appIcon = appWidgets.get(0).loadIcon(ctx, ctx.getResources().getDisplayMetrics().densityDpi);
                } catch (Exception ex) {}
            }

            if (appIcon != null) {
                appIconView.setImageDrawable(appIcon);
            } else {
                appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            appHeader.addView(appIconView);

            TextView appTitle = new TextView(ctx);
            appTitle.setText(appLabel);
            appTitle.setTextColor(Color.WHITE);
            appTitle.setTextSize(14);
            appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            appHeader.addView(appTitle);

            mainContainer.addView(appHeader);

            // Uygulamanin widget'larini saga dogru listeleyen yatay scroll
            HorizontalScrollView systemScroll = new HorizontalScrollView(ctx);
            systemScroll.setClipToPadding(false);
            systemScroll.setPadding(0, 0, 0, dpToPx(12));

            LinearLayout systemList = new LinearLayout(ctx);
            systemList.setOrientation(LinearLayout.HORIZONTAL);

            for (AppWidgetProviderInfo provider : appWidgets) {
                systemList.addView(createSystemWidgetCard(ctx, provider, pm, appIcon));
            }
            systemScroll.addView(systemList);
            mainContainer.addView(systemScroll);
        }
    }

    private View createSystemWidgetCard(Context ctx, AppWidgetProviderInfo provider, android.content.pm.PackageManager pm, Drawable appIcon) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.rightMargin = dpToPx(12);
        card.setLayoutParams(cardLp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x0FFFFFFF);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke(dpToPx(1), 0x1FFFFFFF);
        card.setBackground(cardBg);

        // Canvas Tabanli Sistem Widget ÃƒÆ’Ã¢â‚¬â€œnizleme
        WidgetPreviewView preview = new WidgetPreviewView(ctx, "system");
        if (appIcon != null) {
            preview.setAppIcon(appIcon);
        }
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dpToPx(100), dpToPx(70));
        prevLp.bottomMargin = dpToPx(4);
        preview.setLayoutParams(prevLp);
        card.addView(preview);

        // Widget Boyut Hesaplamalari
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidthPx = dm.widthPixels;
        int screenHeightPx = dm.heightPixels;
        int paddingSidePx = Math.round(12 * density);
        int paddingTopBottomPx = Math.round(8 * density);
        int taskbarPx = Math.round(56 * density);
        int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
        int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
        
        int cellSize = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getCellSize(ctx, usableWidthPx, usableHeightPx);
        int colCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getColCount(ctx, usableWidthPx, cellSize);
        int rowCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getRowCount(ctx, usableHeightPx, cellSize);

        int minWidthPx = Math.round(provider.minWidth * density);
        int minHeightPx = Math.round(provider.minHeight * density);

        int spanX = Math.max(3, Math.min(colCount, Math.round((float) minWidthPx / cellSize)));
        int spanY = Math.max(3, Math.min(rowCount, Math.round((float) minHeightPx / cellSize)));

        String label = provider.loadLabel(pm);
        if (label == null || label.trim().isEmpty()) {
            label = "Sistem Widget (" + spanX + "x" + spanY + ")";
        }

        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(11);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView sizeLabel = new TextView(ctx);
        sizeLabel.setText(spanX + "x" + spanY);
        sizeLabel.setTextColor(0x88FFFFFF);
        sizeLabel.setTextSize(10);
        sizeLabel.setGravity(Gravity.CENTER);
        sizeLabel.setPadding(0, 0, 0, dpToPx(4));
        card.addView(sizeLabel);

        // Ekle Butonu
        TextView btnAdd = new TextView(ctx);
        btnAdd.setText("Ekle");
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setTextSize(11);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(28));
        btnAdd.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x22FFFFFF);
        btnBg.setCornerRadius(dpToPx(14));
        btnAdd.setBackground(btnBg);
        btnAdd.setOnClickListener(v -> addSystemWidgetSelected(provider));

        card.addView(btnAdd);

        return card;
    }

    private List<WidgetInfo> getAvailableWidgets() {
        List<WidgetInfo> list = new ArrayList<>();
        list.add(new WidgetInfo("clock", "Dijital Saat", "Masaustu saati", BaseWidget.WidgetSize.SMALL));
        list.add(new WidgetInfo("speed", "Hiz & Limit", "GPS hizi ve limiti", BaseWidget.WidgetSize.SMALL));
        list.add(new WidgetInfo("direction", "Pusula & Yon", "Surus yonu pusulasi", BaseWidget.WidgetSize.SMALL));
        list.add(new WidgetInfo("weather", "Hava Durumu", "Anlik sicaklik", BaseWidget.WidgetSize.MEDIUM));
        list.add(new WidgetInfo("music", "Medya Calar", "Muzik kontrol paneli", BaseWidget.WidgetSize.LARGE));
        list.add(new WidgetInfo("navigation", "Navigasyon", "Donus yonleri", BaseWidget.WidgetSize.MEDIUM));

        VehicleMetricsPlugin obdPlugin = null;
        if (obdPlugin != null && obdPlugin.isActive()) {
            list.add(new WidgetInfo("obd", "OBD Verileri", "Motor gostergeleri", BaseWidget.WidgetSize.LARGE));
        }

        // app.organicmaps.carlauncher.antenna.AntennaPlugin antennaPlugin = PluginsHelper.getPlugin(app.organicmaps.carlauncher.antenna.AntennaPlugin.class);
        if (antennaPlugin != null && antennaPlugin.isActive()) {
            list.add(new WidgetInfo("antenna", "Anten Durumu", "Anten sinyali", BaseWidget.WidgetSize.SMALL));
        }

        return list;
    }

    private void addNewWidget(WidgetInfo info) {
        if (widgetManager == null || getContext() == null || app == null) return;

        BaseWidget widget = null;
        if (info.type.equals("clock")) {
            widget = new app.organicmaps.carlauncher.widgets.Material3ClockWidget(getContext());
        } else if (info.type.equals("speed")) {
            widget = new SpeedWidget(getContext(), app);
        } else if (info.type.equals("direction")) {
            widget = new DirectionWidget(getContext(), app);
        } else if (info.type.equals("weather")) {
            widget = new app.organicmaps.carlauncher.widgets.WeatherWidget(getContext(), app);
        } else if (info.type.equals("music")) {
            widget = new MusicWidget(getContext(), app);
        } else if (info.type.equals("navigation")) {
            widget = new NavigationWidget(getContext(), app);
        } else if (info.type.equals("obd")) {
            widget = new OBDWidget(getContext(), app);
        } else if (info.type.equals("antenna")) {
            widget = new app.organicmaps.carlauncher.widgets.AntennaWidget(getContext(), app);
        }

        if (widget != null) {
            widget.setId(info.type + "_" + System.currentTimeMillis());
            widget.setPageIndex(activePageIndex);
            widget.setCellX(-1);
            widget.setCellY(-1);
            widget.setSize(info.defaultSize);
            
            widgetManager.addWidget(widget);
            Toast.makeText(getContext(), widget.getTitle() + " eklendi.", Toast.LENGTH_SHORT).show();
            dismiss();
            if (onDismissCallback != null) {
                onDismissCallback.run();
            }
        }
    }

    private void addSystemWidgetSelected(AppWidgetProviderInfo provider) {
        if (widgetManager == null || getContext() == null) return;
        AppWidgetHost host = widgetManager.getAppWidgetHost();
        if (host == null) return;

        try {
            int appWidgetId = host.allocateAppWidgetId();
            pendingAppWidgetId = appWidgetId;
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getContext());
            
            boolean allowed = false;
            try {
                Bundle options = new Bundle();
                
                // Provider'a gercek boyut bilgisini DP cinsinden gonder.
                // Bircok widget provider (BatteryGuru, Weather vb.)
                // onAppWidgetOptionsChanged() callback'ini kullanarak
                // boyuta gore RemoteViews olusturur. Bu bilgi olmadan
                // provider hicbir sey gondermez.
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                float density = dm.density;
                int screenWidthPx = dm.widthPixels;
                int screenHeightPx = dm.heightPixels;
                
                // Padding ve taskbar'i cikar
                int paddingSidePx = Math.round(12 * density);
                int paddingTopBottomPx = Math.round(8 * density);
                int taskbarPx = Math.round(56 * density);
                int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
                int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
                
                // Tek hucre boyutu piksel
                int cellSize = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getCellSize(getContext(), usableWidthPx, usableHeightPx);
                int colCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getColCount(getContext(), usableWidthPx, cellSize);
                int rowCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getRowCount(getContext(), usableHeightPx, cellSize);
                
                int minWidthPx = Math.round(provider.minWidth * density);
                int minHeightPx = Math.round(provider.minHeight * density);

                int spanX = Math.max(3, Math.min(colCount, Math.round((float) minWidthPx / cellSize)));
                int spanY = Math.max(3, Math.min(rowCount, Math.round((float) minHeightPx / cellSize)));
                
                int widthDp = Math.max(40, Math.round((cellSize * spanX) / density));
                int heightDp = Math.max(40, Math.round((cellSize * spanY) / density));
                
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
                
                try {
                    allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider, options);
                } catch (NoSuchMethodError e) {
                    allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider.provider);
                }
            } catch (Exception se) {
                allowed = false;
            }
            
            if (allowed) {
                configureOrAddSystemWidget(appWidgetId, provider);
            } else {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Sistem widgeti baslatilamadi (Izin eksikligi/Servis hatasi): " + e.getMessage(), Toast.LENGTH_LONG).show();
            android.util.Log.e("WidgetPickerDialog", "Sistem widgeti eklenemedi", e);
            writeExceptionToLogFile(e);
        }
    }

    private void writeExceptionToLogFile(Exception e) {
        try {
            if (getContext() == null) return;
            java.io.File logDir = getContext().getExternalFilesDir(null);
            if (logDir != null) {
                java.io.File logFile = new java.io.File(logDir, "carlauncher_widget_error.log");
                java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
                java.io.PrintWriter pw = new java.io.PrintWriter(fw);
                pw.println("--- Exception at " + new java.util.Date().toString() + " ---");
                e.printStackTrace(pw);
                pw.println();
                pw.close();
                fw.close();
            }
        } catch (Exception ex) {
            // Ignore log writing errors
        }
    }

    private void configureOrAddSystemWidget(int appWidgetId, AppWidgetProviderInfo info) {
        if (info.configure != null) {
            try {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                intent.setComponent(info.configure);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Widget konfigurasyon ekrani acilamadi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                addSystemAppWidgetToWorkspace(appWidgetId);
            }
        } else {
            addSystemAppWidgetToWorkspace(appWidgetId);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Context ctx = getContext();
        if (ctx == null) return;

        if (requestCode == REQUEST_BIND_APPWIDGET) {
            int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : pendingAppWidgetId;
            if (appWidgetId == -1) appWidgetId = pendingAppWidgetId;
            
            if (resultCode == Activity.RESULT_OK && appWidgetId != -1) {
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
                if (info != null) {
                    configureOrAddSystemWidget(appWidgetId, info);
                } else {
                    addSystemAppWidgetToWorkspace(appWidgetId);
                }
            } else {
                if (appWidgetId != -1 && widgetManager != null && widgetManager.getAppWidgetHost() != null) {
                    try {
                        widgetManager.getAppWidgetHost().deleteAppWidgetId(appWidgetId);
                    } catch (Exception e) {}
                }
            }
            pendingAppWidgetId = -1;
            return;
        }

        if (requestCode == REQUEST_CREATE_APPWIDGET) {
            int appWidgetId = (data != null) ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : pendingAppWidgetId;
            if (appWidgetId == -1) appWidgetId = pendingAppWidgetId;

            if (resultCode == Activity.RESULT_OK && appWidgetId != -1) {
                addSystemAppWidgetToWorkspace(appWidgetId);
            } else {
                // Kullanici konfigurasyon ekranini iptal etti veya bir hata olustu.
                if (appWidgetId != -1 && widgetManager != null && widgetManager.getAppWidgetHost() != null) {
                    try {
                        widgetManager.getAppWidgetHost().deleteAppWidgetId(appWidgetId);
                    } catch (Exception e) {}
                }
                Toast.makeText(getContext(), "Widget yapilandirmasi iptal edildi.", Toast.LENGTH_SHORT).show();
            }
            pendingAppWidgetId = -1;
        }
    }

    private void addSystemAppWidgetToWorkspace(int appWidgetId) {
        Context ctx = getContext();
        if (widgetManager != null && ctx != null) {
            app.organicmaps.carlauncher.widgets.SystemAppWidget widget = 
                new app.organicmaps.carlauncher.widgets.SystemAppWidget(ctx, appWidgetId);
            widget.setPageIndex(activePageIndex); 
            widget.setCellX(-1);
            widget.setCellY(-1);
            
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
            if (info != null) {
                android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                float density = dm.density;
                int screenWidthPx = dm.widthPixels;
                int screenHeightPx = dm.heightPixels;
                int paddingSidePx = Math.round(12 * density);
                int paddingTopBottomPx = Math.round(8 * density);
                int taskbarPx = Math.round(56 * density);
                int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
                int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
                
                int cellSize = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getCellSize(ctx, usableWidthPx, usableHeightPx);
                int colCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getColCount(ctx, usableWidthPx, cellSize);
                int rowCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getRowCount(ctx, usableHeightPx, cellSize);
                
                int minWidthPx = Math.round(info.minWidth * density);
                int minHeightPx = Math.round(info.minHeight * density);
                
                int spanX = Math.max(3, Math.min(colCount, Math.round((float) minWidthPx / cellSize)));
                int spanY = Math.max(3, Math.min(rowCount, Math.round((float) minHeightPx / cellSize)));
                
                if (spanX <= 3 && spanY <= 3) {
                    widget.setSize(BaseWidget.WidgetSize.SMALL);
                } else if (spanX <= 6 && spanY <= 3) {
                    widget.setSize(BaseWidget.WidgetSize.MEDIUM);
                } else {
                    widget.setSize(BaseWidget.WidgetSize.LARGE);
                }
            } else {
                widget.setSize(BaseWidget.WidgetSize.MEDIUM);
            }

            widgetManager.addWidget(widget);
            Toast.makeText(ctx, "Sistem widgeti eklendi.", Toast.LENGTH_SHORT).show();
            dismiss();
            if (onDismissCallback != null) {
                onDismissCallback.run();
            }
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // ==========================================
    // PREMIUM CANVAS TABANLI PREVIEW VIEW SINIFI
    // ==========================================

    private static class WidgetPreviewView extends View {
        private final String type;
        private final Paint bgPaint;
        private final Paint drawPaint;
        private final float density;
        private Drawable appIcon;

        public WidgetPreviewView(Context context, String type) {
            super(context);
            this.type = type;
            this.density = context.getResources().getDisplayMetrics().density;

            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setStyle(Paint.Style.FILL);
            bgPaint.setColor(0x0FFFFFFF); // Yari saydam premium beyaz

            drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            drawPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setAppIcon(Drawable icon) {
            this.appIcon = icon;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;

            // Yuvarlatilmis premium kart arkaplani
            RectF rect = new RectF(0, 0, w, h);
            canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint);

            drawPaint.setStyle(Paint.Style.STROKE);
            drawPaint.setStrokeWidth(2.5f * density);

            if (type.equals("clock")) {
                drawPaint.setColor(0xFFFF9800); // Turuncu
                canvas.drawCircle(cx, cy, 14f * density, drawPaint);
                canvas.drawLine(cx, cy, cx, cy - 8f * density, drawPaint);
                canvas.drawLine(cx, cy, cx + 6f * density, cy, drawPaint);
            } else if (type.equals("speed")) {
                drawPaint.setColor(0xFF4CAF50); // Yesil
                RectF arcRect = new RectF(cx - 15f * density, cy - 15f * density, cx + 15f * density, cy + 15f * density);
                canvas.drawArc(arcRect, 180, 180, false, drawPaint);
                canvas.drawLine(cx, cy + 3f * density, cx + 10f * density, cy - 10f * density, drawPaint);
            } else if (type.equals("direction")) {
                drawPaint.setColor(0xFF00E5FF); // Neon Turkuaz
                canvas.drawCircle(cx, cy, 14f * density, drawPaint);
                drawPaint.setStyle(Paint.Style.FILL);
                drawPaint.setColor(0xFFFF3333); // Kuzey Kirmizi
                android.graphics.Path pathN = new android.graphics.Path();
                pathN.moveTo(cx, cy - 10f * density);
                pathN.lineTo(cx - 3.5f * density, cy);
                pathN.lineTo(cx + 3.5f * density, cy);
                pathN.close();
                canvas.drawPath(pathN, drawPaint);
                
                drawPaint.setColor(Color.WHITE); // Guney Beyaz
                android.graphics.Path pathS = new android.graphics.Path();
                pathS.moveTo(cx, cy + 10f * density);
                pathS.lineTo(cx - 3.5f * density, cy);
                pathS.lineTo(cx + 3.5f * density, cy);
                pathS.close();
                canvas.drawPath(pathS, drawPaint);
            } else if (type.equals("weather")) {
                drawPaint.setColor(0xFF2196F3); // Mavi Gunes
                canvas.drawCircle(cx - 4f * density, cy - 4f * density, 7f * density, drawPaint);
                drawPaint.setColor(Color.WHITE); // Bulut
                drawPaint.setStyle(Paint.Style.FILL);
                RectF cloud = new RectF(cx - 10f * density, cy, cx + 10f * density, cy + 7f * density);
                canvas.drawRoundRect(cloud, 3.5f * density, 3.5f * density, drawPaint);
                canvas.drawCircle(cx + 3f * density, cy + 2f * density, 5.5f * density, drawPaint);
            } else if (type.equals("music")) {
                drawPaint.setColor(0xFFE91E63); // Pembe Nota
                canvas.drawCircle(cx - 3f * density, cy + 5f * density, 3.5f * density, drawPaint);
                canvas.drawCircle(cx + 5f * density, cy + 3f * density, 3.5f * density, drawPaint);
                drawPaint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx - 1.5f * density, cy + 5f * density, cx - 1.5f * density, cy - 5f * density, drawPaint);
                canvas.drawLine(cx + 6.5f * density, cy + 3f * density, cx + 6.5f * density, cy - 7f * density, drawPaint);
                canvas.drawLine(cx - 1.5f * density, cy - 5f * density, cx + 6.5f * density, cy - 7f * density, drawPaint);
            } else if (type.equals("navigation")) {
                drawPaint.setColor(0xFF9C27B0); // Mor Yon oku
                android.graphics.Path navPath = new android.graphics.Path();
                navPath.moveTo(cx - 8f * density, cy + 8f * density);
                navPath.lineTo(cx - 8f * density, cy - 3f * density);
                navPath.quadTo(cx - 8f * density, cy - 8f * density, cx - 3f * density, cy - 8f * density);
                navPath.lineTo(cx + 5f * density, cy - 8f * density);
                canvas.drawPath(navPath, drawPaint);
                
                drawPaint.setStyle(Paint.Style.FILL);
                android.graphics.Path head = new android.graphics.Path();
                head.moveTo(cx + 5f * density, cy - 11f * density);
                head.lineTo(cx + 10f * density, cy - 8f * density);
                head.lineTo(cx + 5f * density, cy - 5f * density);
                head.close();
                canvas.drawPath(head, drawPaint);
            } else if (type.equals("obd")) {
                drawPaint.setColor(0xFFFFD54F); // Amber Motor
                canvas.drawRect(cx - 8f * density, cy - 5f * density, cx + 8f * density, cy + 7f * density, drawPaint);
                canvas.drawRect(cx - 11f * density, cy - 1.5f * density, cx - 8f * density, cy + 3.5f * density, drawPaint);
            } else if (type.equals("antenna")) {
                drawPaint.setColor(0xFF81C784); // Yesil Cubuklar
                canvas.drawLine(cx - 8f * density, cy + 8f * density, cx - 8f * density, cy + 5f * density, drawPaint);
                canvas.drawLine(cx - 3f * density, cy + 8f * density, cx - 3f * density, cy + 1f * density, drawPaint);
                canvas.drawLine(cx + 2f * density, cy + 8f * density, cx + 2f * density, cy - 3f * density, drawPaint);
                canvas.drawLine(cx + 7f * density, cy + 8f * density, cx + 7f * density, cy - 7f * density, drawPaint);
            } else {
                // Sistem widget'i (Yari saydam arka plan ve uygulama ikonu)
                drawPaint.setColor(0x1FFFFFFF);
                canvas.drawLine(cx - 10f * density, 0, cx - 10f * density, h, drawPaint);
                canvas.drawLine(cx + 10f * density, 0, cx + 10f * density, h, drawPaint);
                canvas.drawLine(0, cy - 10f * density, w, cy - 10f * density, drawPaint);
                canvas.drawLine(0, cy + 10f * density, w, cy + 10f * density, drawPaint);

                if (appIcon != null) {
                    int size = Math.round(28 * density);
                    appIcon.setBounds((int)(cx - size/2), (int)(cy - size/2), (int)(cx + size/2), (int)(cy + size/2));
                    appIcon.draw(canvas);
                }
            }
        }
    }
    private void buildInternalShortcutsSection(Context ctx, LinearLayout container) {
        // Dahili uygulamalari sirayla ekle (Turkce karakter yok)
        String[][] internalApps = {
            {"internal://settings", "Car Launcher Ayarlar"},
            {"internal://music", "Muzik Calici"},
            {"internal://antenna", "Anten Hizalama"}
        };
        for (String[] app : internalApps) {
            String packageName = app[0];
            String label = app[1];
            Drawable icon = app.organicmaps.carlauncher.ui.AppDrawerFragment.getAppIcon(ctx, packageName);
            container.addView(createShortcutCard(ctx, packageName, label, icon));
        }
    }

    private void buildShortcutsSection(Context ctx, LinearLayout container) {
        // AppDrawer'daki cache'lenmis uygulamalari ve ikonlari kullan (Turkce karakter yok)
        List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem> cachedList = 
                app.organicmaps.carlauncher.ui.AppDrawerFragment.getCachedApps();

        if (cachedList != null && !cachedList.isEmpty()) {
            for (app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem item : cachedList) {
                if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(item.packageName)) {
                    continue;
                }
                Drawable icon = app.organicmaps.carlauncher.ui.AppDrawerFragment.getAppIcon(ctx, item.packageName);
                container.addView(createShortcutCard(ctx, item.packageName, item.label, icon));
            }
        } else {
            // Eger cache henuz bos ise asenkron yukle (Turkce karakter yok)
            new android.os.AsyncTask<Void, Void, List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem>>() {
                @Override
                protected List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem> doInBackground(Void... voids) {
                    List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem> list = new ArrayList<>();
                    try {
                        android.content.pm.PackageManager pm = ctx.getPackageManager();
                        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        List<android.content.pm.ResolveInfo> launchables = pm.queryIntentActivities(mainIntent, 0);

                        for (android.content.pm.ResolveInfo info : launchables) {
                            app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem item = 
                                     new app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem();
                            item.label = info.loadLabel(pm).toString();
                            item.packageName = info.activityInfo.packageName;
                            list.add(item);
                        }

                        java.util.Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return list;
                }

                @Override
                protected void onPostExecute(List<app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem> result) {
                    if (result != null && !result.isEmpty() && container != null) {
                        for (app.organicmaps.carlauncher.ui.AppDrawerFragment.AppItem item : result) {
                            if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(item.packageName)) {
                                continue;
                            }
                            Drawable icon = app.organicmaps.carlauncher.ui.AppDrawerFragment.getAppIcon(ctx, item.packageName);
                            container.addView(createShortcutCard(ctx, item.packageName, item.label, icon));
                        }
                    }
                }
            }.execute();
        }
    }

    private View createShortcutCard(Context ctx, String packageName, String label, android.graphics.drawable.Drawable icon) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(dpToPx(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.rightMargin = dpToPx(12);
        card.setLayoutParams(cardLp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x0FFFFFFF);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke(dpToPx(1), 0x1FFFFFFF);
        card.setBackground(cardBg);

        // Ikon (ImageView)
        android.widget.ImageView iconView = new android.widget.ImageView(ctx);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        iconLp.bottomMargin = dpToPx(4);
        iconView.setLayoutParams(iconLp);
        iconView.setImageDrawable(icon);
        card.addView(iconView);

        // Uygulama Baslik
        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dpToPx(6));
        card.addView(title);

        // Ekle Butonu
        TextView btnAdd = new TextView(ctx);
        btnAdd.setText("Ekle");
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setTextSize(11);
        btnAdd.setGravity(Gravity.CENTER);
        btnAdd.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(28));
        btnAdd.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0x22FFFFFF);
        btnBg.setCornerRadius(dpToPx(14));
        btnAdd.setBackground(btnBg);

        btnAdd.setOnClickListener(v -> {
            app.organicmaps.carlauncher.widgets.AppShortcutWidget shortcut =
                    new app.organicmaps.carlauncher.widgets.AppShortcutWidget(ctx, packageName, label);
            widgetManager.addWidget(shortcut);
            dismiss();
            if (onDismissCallback != null) {
                onDismissCallback.run();
            }
        });

        card.addView(btnAdd);

        return card;
    }

    private static class WidgetInfo {
        String type;
        String title;
        String desc;
        BaseWidget.WidgetSize defaultSize;

        WidgetInfo(String type, String title, String desc, BaseWidget.WidgetSize defaultSize) {
            this.type = type;
            this.title = title;
            this.desc = desc;
            this.defaultSize = defaultSize;
        }
    }
}



