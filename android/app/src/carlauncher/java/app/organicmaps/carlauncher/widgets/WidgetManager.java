package app.organicmaps.carlauncher.widgets;

import android.appwidget.AppWidgetHost;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import app.organicmaps.MwmApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Widget'lari yoneten merkezi sinif.
 * Widget ekleme, cikarma, siralama, kaydetme.
 */
public class WidgetManager {

    private static final String PREFS_NAME = "car_launcher_widgets";
    private static final String KEY_WIDGET_CONFIG = "widget_config";

    private final Context context;
    private final MwmApplication app;
    private final SharedPreferences prefs;

    private final List<BaseWidget> allWidgets;
    private final List<BaseWidget> visibleWidgets;
    private AppWidgetHost appWidgetHost;

    private static WidgetManager instance;
    
    private static boolean hasAutoLaunched = false;

    public boolean isHasAutoLaunched() {
        return hasAutoLaunched;
    }

    public void setHasAutoLaunched(boolean hasAutoLaunched) {
        WidgetManager.hasAutoLaunched = hasAutoLaunched;
    }

    public static synchronized WidgetManager getInstance(Context context) {
        if (instance == null) {
            instance = new WidgetManager(context);
        }
        return instance;
    }

    private WidgetManager(@NonNull Context context) {
        this.context = context.getApplicationContext(); // Use App Context for storage/prefs
        this.app = (MwmApplication) context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.allWidgets = new ArrayList<>();
        this.visibleWidgets = new ArrayList<>();
        this.appWidgetHost = new CarAppWidgetHost(this.context, 1024);
    }

    public AppWidgetHost getAppWidgetHost() {
        return appWidgetHost;
    }

    private boolean configLoaded = false;

    public boolean isConfigLoaded() {
        return configLoaded;
    }
    
    /**
     * Force reset for new Fragment session.
     * Clears all widget lists and config flag to ensure clean state.
     * This prevents duplication when singleton persists across app "restarts".
     */
    public void forceResetForNewSession() {
        allWidgets.clear();
        visibleWidgets.clear();
        isStarted = false;
        configLoaded = false;
    }

    /**
     * Tum widget'lar icin Activity Context bilgisini guncelle (Post-Rotation).
     * Tekil AppWidgetHost dinleme durumunu guvenle tazeler.
     * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
     */
    public void updateActivityContext(Context activityContext) {
        // AppWidgetHost nesnesinin surekli yeniden yaratilmasi Android'in widget binding state'ini
        // bozar ve "Widget eklenemedi" (Problem loading widget) hatasina sebep olur.
        // Bu yuzden Host sadece WidgetManager constructor'inda 1 kez yaratilmali.
        // Burada sadece widget'lara yeni Activity Context'i geciriyoruz.

        for (BaseWidget widget : allWidgets) {
            widget.setContext(activityContext);
            // Yeni context ile yeniden olusturulmasi icin eski view'i temizle
            widget.onDestroy(); 
        }
    }

    private static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private Point findEmptySpace(boolean[][] occupied, int spanX, int spanY) {
        if (occupied == null || occupied.length == 0 || occupied[0].length == 0) return null;
        int maxCol = occupied.length;
        int maxRow = occupied[0].length;
        for (int y = 0; y <= maxRow - spanY; y++) {
            for (int x = 0; x <= maxCol - spanX; x++) {
                boolean fits = true;
                for (int sy = 0; sy < spanY; sy++) {
                    for (int sx = 0; sx < spanX; sx++) {
                        if (occupied[x + sx][y + sy]) {
                            fits = false;
                            break;
                        }
                    }
                    if (!fits) break;
                }
                if (fits) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    private String getOrientationSuffix() {
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            return "_portrait";
        }
        return "_landscape";
    }

    private void autoPlaceWidget(BaseWidget widget) {
        int maxPage = 2; // En az 3 sayfa (0, 1, 2) varsayilan
        for (BaseWidget w : allWidgets) {
            if (w.isVisible()) {
                maxPage = Math.max(maxPage, w.getPageIndex());
            }
        }
        int pageCount = maxPage + 1;
        int spanX = widget.getSpanX();
        int spanY = widget.getSpanY();
        
        // 1. Adim: Kullanicinin o an bulundugu aktif sayfada yer ara
        int targetPage = widget.getPageIndex();
        if (targetPage < 0) {
            targetPage = 0;
        }
        
        // Aktif sayfada yer var mi kontrol et
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidthPx = dm.widthPixels;
        int screenHeightPx = dm.heightPixels;
        
        int paddingSidePx = Math.round(12 * density);
        int paddingTopBottomPx = Math.round(8 * density);
        int taskbarPx = Math.round(56 * density);
        
        int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
        int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
        
        int cellSize = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getCellSize(context, usableWidthPx, usableHeightPx);
        int maxCol = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getColCount(context, usableWidthPx, cellSize);
        int maxRow = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getRowCount(context, usableHeightPx, cellSize);

        if (targetPage < pageCount) {
            boolean[][] occupied = new boolean[maxCol][maxRow];
            for (BaseWidget w : allWidgets) {
                if (w != widget && w.isVisible() && w.getPageIndex() == targetPage) {
                    int cx = w.getCellX();
                    int cy = w.getCellY();
                    int sx = w.getSpanX();
                    int sy = w.getSpanY();
                    if (cx >= 0 && cx + sx <= maxCol && cy >= 0 && cy + sy <= maxRow) {
                        for (int x = cx; x < cx + sx; x++) {
                            for (int y = cy; y < cy + sy; y++) {
                                occupied[x][y] = true;
                            }
                        }
                    }
                }
            }
            Point p = findEmptySpace(occupied, spanX, spanY);
            if (p != null) {
                widget.setPageIndex(targetPage);
                widget.setCellX(p.x);
                widget.setCellY(p.y);
                return;
            }
        }

        // 2. Adim: Diger tum mevcut sayfalarda sirayla bos yer ara
        for (int page = 0; page < pageCount; page++) {
            if (page == targetPage) continue; // Zaten denemistik
            
            boolean[][] occupied = new boolean[maxCol][maxRow];
            for (BaseWidget w : allWidgets) {
                if (w != widget && w.isVisible() && w.getPageIndex() == page) {
                    int cx = w.getCellX();
                    int cy = w.getCellY();
                    int sx = w.getSpanX();
                    int sy = w.getSpanY();
                    if (cx >= 0 && cx + sx <= maxCol && cy >= 0 && cy + sy <= maxRow) {
                        for (int x = cx; x < cx + sx; x++) {
                            for (int y = cy; y < cy + sy; y++) {
                                occupied[x][y] = true;
                            }
                        }
                    }
                }
            }
            
            Point p = findEmptySpace(occupied, spanX, spanY);
            if (p != null) {
                widget.setPageIndex(page);
                widget.setCellX(p.x);
                widget.setCellY(p.y);
                return;
            }
        }
        
        // 3. Adim: Hicbir sayfada yer kalmadiysa, yeni bir sayfa olustur ve oraya koy
        int newPage = pageCount; // Yeni sayfa indeksi
        widget.setPageIndex(newPage);
        widget.setCellX(0);
        widget.setCellY(0);
    }

    /**
     * Widget ekle veya zaten varsa goster.
     * Bos yerleri dinamik olarak otomatik hesaplar ve yerlestirir.
     * Kod icinde kesinlikle Turkce karakter kullanilmamistir.
     */
    public void addWidget(@NonNull BaseWidget widget) {
        BaseWidget targetWidget = widget;
        BaseWidget existing = findWidgetById(widget.getId());
        if (existing != null) {
            existing.setVisible(true);
            existing.setSize(widget.getSize());
            // Aktarim: Yeni eklenmek istenen sayfa indeksini mevcut widget'a veriyoruz
            existing.setPageIndex(widget.getPageIndex());
            existing.setCellX(-1);
            existing.setCellY(-1);
            targetWidget = existing;

            // Widget'i listenin sonuna tasiyoruz (En son eklenen olma ozelligi korunsun)
            allWidgets.remove(existing);
            allWidgets.add(existing);
        } else {
            if (!allWidgets.contains(widget)) {
                allWidgets.add(widget);
            }
            targetWidget = widget;
        }

        // Eger koordinatlar -1 ise bos yer bulup yerlestir
        if (targetWidget.getCellX() == -1 || targetWidget.getCellY() == -1) {
            autoPlaceWidget(targetWidget);
        }

        // Listede herkesin order degerini bulundugu siraya gore yenileyelim
        for (int i = 0; i < allWidgets.size(); i++) {
            allWidgets.get(i).setOrder(i);
        }

        updateVisibleWidgets();
        if (isStarted && !targetWidget.isStarted()) {
            targetWidget.onStart();
        }
        saveWidgetConfig();
    }

    /**
     * Widget cikar.
     */
    public void removeWidget(@NonNull BaseWidget widget) {
        allWidgets.remove(widget);
        visibleWidgets.remove(widget);
        saveWidgetConfig(); // SAVE ON REMOVE
    }

    /**
     * Widget'i gosterilir/gizli yap.
     */
    public void setWidgetVisible(@NonNull String widgetId, boolean visible) {
        BaseWidget widget = findWidgetById(widgetId);
        if (widget != null) {
            widget.setVisible(visible);
            updateVisibleWidgets();
            saveWidgetConfig(); // SAVE ON VISIBILITY CHANGE
        }
    }
    
    /**
     * Widget siralamasini degistir.
     */
    public void moveWidget(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= allWidgets.size() ||
                toIndex < 0 || toIndex >= allWidgets.size()) {
            return;
        }

        BaseWidget widget = allWidgets.remove(fromIndex);
        allWidgets.add(toIndex, widget);

        // Update order fields
        for (int i = 0; i < allWidgets.size(); i++) {
            allWidgets.get(i).setOrder(i);
        }

        updateVisibleWidgets();
        saveWidgetConfig();
    }
    
    public void updateVisibleOrder(List<BaseWidget> newOrder) {
        // 1. Update order index
        for (int i = 0; i < newOrder.size(); i++) {
             BaseWidget w = newOrder.get(i);
             w.setOrder(i);
        }
        
        // 2. FIXED: Simply replace allWidgets with newOrder
        //    Dialog's editingList is the single source of truth
        //    Deleted widgets should NOT be preserved as "invisible"
        allWidgets.clear();
        allWidgets.addAll(newOrder);
        
        // 3. Sync visibleWidgets (all widgets in newOrder are visible by definition)
        visibleWidgets.clear();
        visibleWidgets.addAll(newOrder);
        
        // 4. Save
        saveWidgetConfig();
    }

    /**
     * Gorunur widget'lari guncelle ve sirala.
     */
    private void updateVisibleWidgets() {
        visibleWidgets.clear();
        for (BaseWidget widget : allWidgets) {
            if (widget.isVisible()) {
                visibleWidgets.add(widget);
            }
        }

        // Order'a gore sirala
        Collections.sort(visibleWidgets, new Comparator<BaseWidget>() {
            @Override
            public int compare(BaseWidget w1, BaseWidget w2) {
                return Integer.compare(w1.getOrder(), w2.getOrder());
            }
        });
    }

    /**
     * Widget'lari container'a ekle.
     */
    public void attachWidgetsToContainer(@NonNull ViewGroup container) {
        container.removeAllViews();

        int margin = dpToPx(4); // Reduced margin

        for (int i = 0; i < visibleWidgets.size(); i++) {
            BaseWidget widget = visibleWidgets.get(i);
            View widgetView = widget.getRootView();
            if (widgetView == null) {
                widgetView = widget.createView();
            }

            if (widgetView != null) {
                // Apply margins consistently to all widgets
                // Detect Layout Orientation
                boolean isVertical = true;
                LinearLayout.LayoutParams params;
                if (container instanceof LinearLayout) {
                    isVertical = ((LinearLayout) container).getOrientation() == LinearLayout.VERTICAL;
                }

                if (isVertical) {
                    // Vertical (Landscape): Width Fill, Height Wrap
                    params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                } else {
                    // Horizontal (Portrait): Width Fixed (~100-130dp), Height Fill
                    int width = dpToPx(130);
                    params = new LinearLayout.LayoutParams(
                            width,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                }

                // First widget gets 0 top margin
                int topMargin = (i == 0) ? 0 : margin;
                params.setMargins(margin, topMargin, margin, margin);
                widgetView.setLayoutParams(params);

                container.addView(widgetView);
            }
        }
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private boolean isStarted = false;

    /**
     * Tum widget'lari guncelle.
     */
    public void updateAllWidgets() {
        for (BaseWidget widget : visibleWidgets) {
            if (widget.isStarted()) {
                widget.update();
            }
        }
    }

    /**
     * Tum widget'lari baslat.
     * Sistem widget'larinin eski view referanslarini sifirlayarak taze context ile baslatir.
     * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
     */
    public void startAllWidgets() {
        isStarted = true;
        try {
            appWidgetHost.startListening();
        } catch (Exception e) {
            android.util.Log.e("WidgetManager", "AppWidgetHost dinlemesi baslatilamadi: " + e.getMessage());
        }
        for (BaseWidget widget : visibleWidgets) {
            if (widget instanceof SystemAppWidget) {
                widget.onDestroy(); // Taze context ile yeniden yaratilmasi icin view referansini sifirla
            }
            widget.onStart();
        }
    }

    /**
     * Tum widget'lari durdur.
     */
    public void stopAllWidgets() {
        isStarted = false;
        try {
            appWidgetHost.stopListening();
        } catch (Exception e) {
            android.util.Log.e("WidgetManager", "AppWidgetHost dinlemesi durdurulamadi: " + e.getMessage());
        }
        for (BaseWidget widget : visibleWidgets) {
            widget.onStop();
        }
    }

    /**
     * Widget config'i kaydet.
     */
    public void saveWidgetConfig() {
        SharedPreferences.Editor editor = prefs.edit();
        String suffix = getOrientationSuffix();
        
        List<String> ids = new ArrayList<>();
        for (BaseWidget widget : allWidgets) {
            ids.add(widget.getId());
            editor.putBoolean("visible_" + widget.getId() + suffix, widget.isVisible());
            editor.putInt("size_" + widget.getId() + suffix, widget.getSize().ordinal());
            
            // Grid koordinatlari ve boyutlari
            editor.putInt("page_" + widget.getId() + suffix, widget.getPageIndex());
            editor.putInt("cellx_" + widget.getId() + suffix, widget.getCellX());
            editor.putInt("celly_" + widget.getId() + suffix, widget.getCellY());
            editor.putInt("spanx_" + widget.getId() + suffix, widget.getSpanX());
            editor.putInt("spany_" + widget.getId() + suffix, widget.getSpanY());
        }
        
        // Join Ids
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            sb.append(ids.get(i));
            if (i < ids.size() - 1) sb.append(",");
        }
        editor.putString(KEY_WIDGET_CONFIG + suffix, sb.toString());
        editor.apply();
    }

    public boolean loadWidgetConfig() {
        String suffix = getOrientationSuffix();
        String configKey = KEY_WIDGET_CONFIG + suffix;
        
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float density = dm.density;
        int screenWidthPx = dm.widthPixels;
        int screenHeightPx = dm.heightPixels;
        int paddingSidePx = Math.round(12 * density);
        int paddingTopBottomPx = Math.round(8 * density);
        int taskbarPx = Math.round(56 * density);
        int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
        int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx) - taskbarPx;
        
        int cellSize = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getCellSize(context, usableWidthPx, usableHeightPx);
        int targetColCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getColCount(context, usableWidthPx, cellSize);
        int targetRowCount = app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout.getRowCount(context, usableHeightPx, cellSize);

        // Eger bu yon icin daha once hic kayit yapilmadiysa, diger yonu kopyalayarak basla
        if (!prefs.contains(configKey)) {
            String fallbackSuffix = suffix.equals("_portrait") ? "_landscape" : "_portrait";
            String fallbackKey = KEY_WIDGET_CONFIG + fallbackSuffix;
            if (prefs.contains(fallbackKey)) {
                String savedConfig = prefs.getString(fallbackKey, "");
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(configKey, savedConfig);
                if (!savedConfig.isEmpty()) {
                    String[] ids = savedConfig.split(",");
                    for (String id : ids) {
                        if (id.isEmpty()) continue;
                        editor.putBoolean("visible_" + id + suffix, prefs.getBoolean("visible_" + id + fallbackSuffix, true));
                        editor.putInt("size_" + id + suffix, prefs.getInt("size_" + id + fallbackSuffix, BaseWidget.WidgetSize.SMALL.ordinal()));
                        editor.putInt("page_" + id + suffix, prefs.getInt("page_" + id + fallbackSuffix, 0));
                        
                        int savedSpanX = prefs.getInt("spanx_" + id + fallbackSuffix, 1);
                        int savedSpanY = prefs.getInt("spany_" + id + fallbackSuffix, 1);
                        int savedCellX = prefs.getInt("cellx_" + id + fallbackSuffix, -1);
                        int savedCellY = prefs.getInt("celly_" + id + fallbackSuffix, -1);
                        
                        int spanX = Math.max(3, Math.min(targetColCount, savedSpanX));
                        int spanY = Math.max(3, Math.min(targetRowCount, savedSpanY));
                        
                        int cellX = savedCellX;
                        int cellY = savedCellY;
                        if (cellX != -1) {
                            cellX = Math.max(0, Math.min(targetColCount - spanX, cellX));
                        }
                        if (cellY != -1) {
                            cellY = Math.max(0, Math.min(targetRowCount - spanY, cellY));
                        }
                        
                        editor.putInt("spanx_" + id + suffix, spanX);
                        editor.putInt("spany_" + id + suffix, spanY);
                        editor.putInt("cellx_" + id + suffix, cellX);
                        editor.putInt("celly_" + id + suffix, cellY);
                    }
                }
                editor.apply();
            } else {
                return false; // Ilk calistirmada varsayilanlar yuklensin
            }
        }
        
        String savedConfig = prefs.getString(configKey, "");
        if (savedConfig.isEmpty()) {
            allWidgets.clear();
            visibleWidgets.clear();
            return true; 
        }

        String[] ids = savedConfig.split(",");
        List<BaseWidget> restoredWidgets = new ArrayList<>();
        java.util.Set<String> processingIds = new java.util.HashSet<>();
        
        for (String id : ids) {
            if (id.isEmpty()) continue;
            
            BaseWidget widget = findWidgetById(id);
            if (widget == null) {
                if (id.startsWith("appwidget_")) {
                    try {
                        int appWidgetId = Integer.parseInt(id.substring("appwidget_".length()));
                        widget = new SystemAppWidget(context, appWidgetId);
                    } catch (Exception e) {
                        android.util.Log.e("WidgetManager", "SystemAppWidget geri yuklenirken hata: " + e.getMessage());
                    }
                } else if (id.startsWith("shortcut_")) {
                    try {
                        String packageName = id.substring("shortcut_".length());
                        String label = "App";
                        if (packageName.equals("internal://settings")) {
                            label = "Car Launcher Ayarlar";
                        } else if (packageName.equals("internal://music")) {
                            label = "Muzik Calici";
                        } else if (packageName.equals("internal://antenna")) {
                            label = "Anten Hizalama";
                        } else {
                            try {
                                android.content.pm.PackageManager pm = context.getPackageManager();
                                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                                label = pm.getApplicationLabel(ai).toString();
                            } catch (Exception ignored) {}
                        }
                        widget = new AppShortcutWidget(context, packageName, label);
                    } catch (Exception e) {
                        android.util.Log.e("WidgetManager", "AppShortcutWidget geri yuklenirken hata: " + e.getMessage());
                    }
                } else {
                    String type = id.split("_")[0];
                    if (processingIds.contains(id)) {
                        widget = WidgetRegistry.createUniqueWidget(context, app, type);
                    } else {
                        widget = WidgetRegistry.createWidget(context, app, type);
                        if (widget != null) {
                             try {
                                java.lang.reflect.Field idField = BaseWidget.class.getDeclaredField("id");
                                idField.setAccessible(true);
                                idField.set(widget, id);
                            } catch (Exception e) {}
                        }
                    }
                }
            } else {
                 if (processingIds.contains(id)) {
                     continue;
                 }
            }
            
            if (widget != null) {
                processingIds.add(widget.getId());
                
                boolean visible = prefs.getBoolean("visible_" + id + suffix, true);
                int sizeOrd = prefs.getInt("size_" + id + suffix, BaseWidget.WidgetSize.SMALL.ordinal());
                
                widget.setVisible(visible);
                if (sizeOrd >= 0 && sizeOrd < BaseWidget.WidgetSize.values().length) {
                    widget.setSize(BaseWidget.WidgetSize.values()[sizeOrd]);
                }
                
                int page = prefs.getInt("page_" + id + suffix, 0);
                int cellx = prefs.getInt("cellx_" + id + suffix, -1);
                int celly = prefs.getInt("celly_" + id + suffix, -1);
                int spanx = prefs.getInt("spanx_" + id + suffix, widget.getSpanX());
                int spany = prefs.getInt("spany_" + id + suffix, widget.getSpanY());
                
                spanx = Math.max(3, Math.min(targetColCount, spanx));
                spany = Math.max(3, Math.min(targetRowCount, spany));
                if (cellx != -1) {
                    cellx = Math.max(0, Math.min(targetColCount - spanx, cellx));
                }
                if (celly != -1) {
                    celly = Math.max(0, Math.min(targetRowCount - spany, celly));
                }

                widget.setPageIndex(page);
                widget.setCellX(cellx);
                widget.setCellY(celly);
                widget.setSpanX(spanx);
                widget.setSpanY(spany);
                
                restoredWidgets.add(widget);
            }
        }
        
        allWidgets.clear();
        allWidgets.addAll(restoredWidgets);
        
        updateVisibleWidgets();
        configLoaded = true;
        return true;
    }

    /**
     * ID'ye gore widget bul.
     */
    private BaseWidget findWidgetById(String id) {
        for (BaseWidget widget : allWidgets) {
            if (widget.getId().equals(id)) {
                return widget;
            }
        }
        return null;
    }

    /**
     * Tum widget'lari al.
     */
    public List<BaseWidget> getAllWidgets() {
        return new ArrayList<>(allWidgets);
    }

    /**
     * Gorunur widget'lari al.
     */
    public List<BaseWidget> getVisibleWidgets() {
        return new ArrayList<>(visibleWidgets);
    }

    /**
     * Mevcut widget config ve durumlarini user_ prefix'i ile SharedPreferences'a kaydeder.
     * Turkce karakter kullanilmamistir.
     */
    public void saveUserLayout() {
        String suffix = getOrientationSuffix();
        String savedConfig = prefs.getString(KEY_WIDGET_CONFIG + suffix, "");
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_" + KEY_WIDGET_CONFIG + suffix, savedConfig);
        
        for (BaseWidget widget : allWidgets) {
            String id = widget.getId();
            editor.putBoolean("user_visible_" + id + suffix, widget.isVisible());
            editor.putInt("user_size_" + id + suffix, widget.getSize().ordinal());
            
            // Grid koordinatlarini da yedekle
            editor.putInt("user_page_" + id + suffix, widget.getPageIndex());
            editor.putInt("user_cellx_" + id + suffix, widget.getCellX());
            editor.putInt("user_celly_" + id + suffix, widget.getCellY());
            editor.putInt("user_spanx_" + id + suffix, widget.getSpanX());
            editor.putInt("user_spany_" + id + suffix, widget.getSpanY());
        }
        editor.apply();
    }

    public boolean loadUserLayout() {
        String suffix = getOrientationSuffix();
        if (!prefs.contains("user_" + KEY_WIDGET_CONFIG + suffix)) {
            return false;
        }
        
        String userConfig = prefs.getString("user_" + KEY_WIDGET_CONFIG + suffix, "");
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_WIDGET_CONFIG + suffix, userConfig);
        
        if (!userConfig.isEmpty()) {
            String[] ids = userConfig.split(",");
            for (String id : ids) {
                if (id.isEmpty()) continue;
                if (prefs.contains("user_visible_" + id + suffix)) {
                    editor.putBoolean("visible_" + id + suffix, prefs.getBoolean("user_visible_" + id + suffix, true));
                }
                if (prefs.contains("user_size_" + id + suffix)) {
                    editor.putInt("size_" + id + suffix, prefs.getInt("user_size_" + id + suffix, BaseWidget.WidgetSize.SMALL.ordinal()));
                }
                if (prefs.contains("user_page_" + id + suffix)) {
                    editor.putInt("page_" + id + suffix, prefs.getInt("user_page_" + id + suffix, 0));
                }
                if (prefs.contains("user_cellx_" + id + suffix)) {
                    editor.putInt("cellx_" + id + suffix, prefs.getInt("user_cellx_" + id + suffix, -1));
                }
                if (prefs.contains("user_celly_" + id + suffix)) {
                    editor.putInt("celly_" + id + suffix, prefs.getInt("user_celly_" + id + suffix, -1));
                }
                if (prefs.contains("user_spanx_" + id + suffix)) {
                    editor.putInt("spanx_" + id + suffix, prefs.getInt("user_spanx_" + id + suffix, 1));
                }
                if (prefs.contains("user_spany_" + id + suffix)) {
                    editor.putInt("spany_" + id + suffix, prefs.getInt("user_spany_" + id + suffix, 1));
                }
            }
        }
        editor.apply();
        return loadWidgetConfig();
    }
}
