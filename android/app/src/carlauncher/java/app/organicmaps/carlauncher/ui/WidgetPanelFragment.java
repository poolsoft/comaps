package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import app.organicmaps.carlauncher.widgets.BaseWidget;
import app.organicmaps.carlauncher.widgets.WidgetManager;
import app.organicmaps.carlauncher.widgets.SpeedWidget;
import app.organicmaps.carlauncher.widgets.DirectionWidget;
import app.organicmaps.carlauncher.widgets.MusicWidget;
import app.organicmaps.carlauncher.widgets.NavigationWidget;
import app.organicmaps.carlauncher.widgets.OBDWidget;
import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.plugins.odb.VehicleMetricsPlugin;
import app.organicmaps.plugins.PluginsHelper;
import app.organicmaps.carlauncher.AutoLaunchManager;
import app.organicmaps.carlauncher.CarLauncherInterface;
import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.widgets.WorkspacePageAdapter;

/**
 * Cok Sayfali Premium Grid Widget Workspace Fragment.
 * ViewPager2 tabanli, 4x4 Grid sayfali ve premium micro-indicator animasyonlu.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class WidgetPanelFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "WidgetPanelFragment";

    private androidx.viewpager2.widget.ViewPager2 viewPager;
    private android.widget.LinearLayout pageIndicator;
    private WidgetManager widgetManager;
    private MwmApplication app;
    private ViewGroup rootContent;
    private View widgetContentFrame;
    private android.widget.ImageView parallaxBg;
    private View menuBtn;
    
    private boolean isPinned = true; 
    private static final String PREF_IS_PINNED = "widget_panel_pinned";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            app = (MwmApplication) getContext().getApplicationContext();
            widgetManager = WidgetManager.getInstance(getContext());
            widgetManager.forceResetForNewSession(); // Temiz baslangic
            widgetManager.updateActivityContext(getContext());
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            isPinned = prefs.getBoolean(PREF_IS_PINNED, true);

            if (!widgetManager.loadWidgetConfig()) {
                initializeWidgets(); // Default widget'lari yukle
            }
            widgetManager.updateActivityContext(getContext()); // Yukleme sonrasinda context'leri guncelle
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root;
        try {
            root = inflater.inflate(app.organicmaps.R.layout.fragment_widget_panel, container, false);
        } catch (Exception e) {
            return createProgrammaticView();
        }

        viewPager = root.findViewById(app.organicmaps.R.id.widget_view_pager);
        pageIndicator = root.findViewById(app.organicmaps.R.id.workspace_page_indicator);
        menuBtn = root.findViewById(app.organicmaps.R.id.btn_widget_menu);
        
        // Parallax arka plan baglantisi
        parallaxBg = root.findViewById(app.organicmaps.R.id.workspace_parallax_bg);
        updateBackgroundStyle();
        
        // Bottom Navigation
        View navWidgets = root.findViewById(app.organicmaps.R.id.nav_widgets);
        View navNavigation = root.findViewById(app.organicmaps.R.id.nav_navigation);
        View navApps = root.findViewById(app.organicmaps.R.id.nav_apps);
        View navSettings = root.findViewById(app.organicmaps.R.id.nav_settings);
        
        setupBottomNav(navWidgets, navNavigation, navApps, navSettings);
        
        widgetContentFrame = root;
        rootContent = (ViewGroup) root;
 
        initListLayout();
        setupMenuButton(menuBtn);
        WorkspacePageAdapter.setWorkspaceLongClickListener(this::showPopupMenu);
        setupViewPagerCallback();
        
        return root;
    }
    
    private View createProgrammaticView() {
        FrameLayout contentFrame = new FrameLayout(getContext());
        contentFrame.setBackgroundColor(0xFF111111);
        
        // Programatik parallax arka plan
        parallaxBg = new android.widget.ImageView(getContext());
        parallaxBg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        contentFrame.addView(parallaxBg, lp);
        updateBackgroundStyle();
        
        contentFrame.post(() -> {
            if (parallaxBg != null) {
                int width = contentFrame.getWidth() > 0 ? contentFrame.getWidth() : getResources().getDisplayMetrics().widthPixels;
                ViewGroup.LayoutParams vlp = parallaxBg.getLayoutParams();
                if (vlp != null) {
                    vlp.width = (int) (width * 1.5f);
                    parallaxBg.setLayoutParams(vlp);
                }
            }
        });

        viewPager = new androidx.viewpager2.widget.ViewPager2(getContext());
        contentFrame.addView(viewPager, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setupViewPagerCallback();
        return contentFrame;
    }

    private void initListLayout() {
        if (viewPager == null) return;
        viewPager.post(() -> {
            if (getView() != null) {
                if (parallaxBg != null && viewPager != null) {
                    int width = viewPager.getWidth() > 0 ? viewPager.getWidth() : getResources().getDisplayMetrics().widthPixels;
                    ViewGroup.LayoutParams lp = parallaxBg.getLayoutParams();
                    if (lp != null) {
                        lp.width = (int) (width * 1.5f);
                        parallaxBg.setLayoutParams(lp);
                    }
                }
                applyWidgetsToView();
            }
        });
    }

    private static final int RC_SELECT_WALLPAPER = 105;

    private void setupMenuButton(View menuBtn) {
        if (menuBtn == null) return;
        menuBtn.setOnClickListener(v -> showPopupMenu(v));
    }

    private void showPopupMenu(View anchorView) {
        if (getContext() == null || anchorView == null) return;
        
        View actualAnchor = anchorView;
        if (actualAnchor instanceof app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout && menuBtn != null) {
            actualAnchor = menuBtn;
        }
        
        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), actualAnchor);
        
        popup.getMenu().add(0, 1, 0, "Widget Ekle (Yeni)");
        popup.getMenu().add(0, 2, 1, "Launcher Ayarlari");
        popup.getMenu().add(0, 4, 2, "Mevcut Duzeni Kaydet");
        popup.getMenu().add(0, 6, 3, "Masaustunu Duzenle (Edit Mode)");

        // Premium Arka Plan Secenekleri (Yeni WallpaperChooserDialog ile birlestirildi)
        popup.getMenu().add(0, 7, 4, "Duvar Kagidi Degistir");
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            CarLauncherSettings settings = new CarLauncherSettings(getContext());
            if (id == 1) {
                showWidgetControlDialog();
                return true;
            } else if (id == 2) {
                if (getActivity() instanceof app.organicmaps.activities.MapActivity) {
                    ((app.organicmaps.activities.MapActivity) getActivity()).openCarLauncherSettings();
                }
                return true;
            } else if (id == 6) {
                if (viewPager != null && viewPager.getAdapter() instanceof WorkspacePageAdapter) {
                    WorkspacePageAdapter adapter = (WorkspacePageAdapter) viewPager.getAdapter();
                    WorkspacePageAdapter.isEditMode = true;
                    adapter.notifyDataSetChanged();
                    if (adapter.getEditModeListener() != null) {
                        adapter.getEditModeListener().onEditModeChanged(true);
                    }
                }
                return true;
            } else if (id == 7) {
                showWallpaperChooserDialog();
                return true;
            } else if (id == 4) {
                if (widgetManager != null) {
                    widgetManager.saveUserLayout();
                    if (getView() != null) {
                        getView().performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
                    }
                    android.widget.Toast.makeText(getContext(), "Mevcut widget duzeni basariyla kaydedildi", android.widget.Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == RC_SELECT_WALLPAPER) {
                android.net.Uri uri = data.getData();
                if (getContext() != null) {
                    try {
                        getContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception e) {
                        // ignore
                    }
                    CarLauncherSettings settings = new CarLauncherSettings(getContext());
                    settings.getPrefs().edit().putString("car_launcher_wallpaper_uri", uri.toString()).apply();
                    settings.setBackgroundStyle("custom");
                    
                    updateBackgroundStyle();
                }
            }
        }
    }

    private enum LayoutPreset {
        NAVIGATION, MEDIA, MINIMALIST, USER
    }

    private void applyLayoutPreset(LayoutPreset preset) {
        if (widgetManager == null || getContext() == null) return;

        widgetManager.stopAllWidgets();
        widgetManager.forceResetForNewSession();
        widgetManager.updateActivityContext(getContext());

        switch (preset) {
            case NAVIGATION:
                widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.Material3ClockWidget(getContext()));
                widgetManager.addWidget(new SpeedWidget(getContext(), app));
                widgetManager.addWidget(new DirectionWidget(getContext(), app));
                widgetManager.addWidget(new NavigationWidget(getContext(), app));
                break;
            case MEDIA:
                widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.Material3ClockWidget(getContext()));
                widgetManager.addWidget(new MusicWidget(getContext(), app));
                widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.WeatherWidget(getContext(), app));
                break;
            case MINIMALIST:
                widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.Material3ClockWidget(getContext()));
                widgetManager.addWidget(new SpeedWidget(getContext(), app));
                break;
            case USER:
                if (!widgetManager.loadUserLayout()) {
                    android.widget.Toast.makeText(getContext(), "Kaydedilmis kullanici duzeni bulunamadi", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
        }

        widgetManager.saveWidgetConfig();
        widgetManager.startAllWidgets();
        applyWidgetsToView();
        
        if (getView() != null) {
            getView().performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        }
    }

    private void showWidgetControlDialog() {
        WidgetPickerDialog dialog = new WidgetPickerDialog();
        dialog.setWidgetManager(widgetManager);
        if (viewPager != null) {
            dialog.setActivePageIndex(viewPager.getCurrentItem());
        }
        dialog.setOnDismissCallback(() -> {
            int targetPage = viewPager != null ? viewPager.getCurrentItem() : 0;
            // Let the post method inside applyWidgetsToView find the exact final page index after binding
            applyWidgetsToView(targetPage);
        });
        dialog.show(getChildFragmentManager(), "WidgetPickerDialog");
    }

    private void applyWidgetsToView() {
        applyWidgetsToView(-1);
    }

    private void applyWidgetsToView(final int forcePageIndex) {
        if (viewPager != null) {
            int savedPage = 0;
            if (getContext() != null) {
                savedPage = PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getInt("widget_panel_last_page", 0);
            }
            final int defaultPage = savedPage;
            final int currentItem = forcePageIndex >= 0 ? forcePageIndex : defaultPage;
            java.util.List<app.organicmaps.carlauncher.widgets.BaseWidget> visibleWidgets = widgetManager.getVisibleWidgets();
            for (app.organicmaps.carlauncher.widgets.BaseWidget w : visibleWidgets) {
                if (getActivity() != null) w.setContext(getActivity());
            }

            if (viewPager.getAdapter() instanceof WorkspacePageAdapter) {
                final WorkspacePageAdapter adapter = (WorkspacePageAdapter) viewPager.getAdapter();
                adapter.updateWidgetsList(visibleWidgets);
                updatePageIndicator();

                viewPager.post(new Runnable() {
                    @Override
                    public void run() {
                        int targetPage = currentItem;
                        int count = adapter.getItemCount();
                        targetPage = Math.max(0, Math.min(count - 1, targetPage));
                        viewPager.setCurrentItem(targetPage, false);
                    }
                });
            } else {
                final WorkspacePageAdapter adapter = new WorkspacePageAdapter(
                    getContext(),
                    getChildFragmentManager(),
                    visibleWidgets,
                    new Runnable() {
                        @Override
                        public void run() {
                            updatePageIndicator();
                        }
                    }
                );
                adapter.setEditModeListener(new WorkspacePageAdapter.EditModeListener() {
                    @Override
                    public void onEditModeChanged(boolean isEditMode) {
                        viewPager.setUserInputEnabled(!isEditMode);
                        if (isEditMode) {
                            android.widget.Toast.makeText(getContext(), 
                                "Duzenleme Modu Aktif. Cikmak icin bos alana tiklayin.", 
                                android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            android.widget.Toast.makeText(getContext(), 
                                "Duzenlemeler Kaydedildi.", 
                                android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                viewPager.setAdapter(adapter);
                
                setupPageIndicator(adapter.getItemCount());

                // Sayfayi asenkron olarak hedef sayfa konumuna kaydir ve koru
                viewPager.post(new Runnable() {
                    @Override
                    public void run() {
                        int targetPage = currentItem;
                        int count = adapter.getItemCount();
                        targetPage = Math.max(0, Math.min(count - 1, targetPage));
                        viewPager.setCurrentItem(targetPage, false);
                    }
                });
            }
        }
    }

    private void setupPageIndicator(int count) {
        if (pageIndicator == null) return;
        pageIndicator.removeAllViews();
        int margin = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        for (int i = 0; i < count; i++) {
            View dot = new View(getContext());
            android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                    (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics()),
                    (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics())
            );
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);
            
            // Programatik GradientDrawable (Sifir risk, maksimum premium gorunum)
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            gd.setColor(0x55FFFFFF);
            dot.setBackground(gd);
            
            pageIndicator.addView(dot);
        }
        updatePageIndicatorSelection(0);
    }

    private void updatePageIndicatorSelection(int selectedPosition) {
        if (pageIndicator == null) return;
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            View dot = pageIndicator.getChildAt(i);
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            
            android.view.ViewGroup.LayoutParams params = dot.getLayoutParams();
            if (i == selectedPosition) {
                gd.setColor(0xFFFFFFFF);
                params.width = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            } else {
                gd.setColor(0x55FFFFFF);
                params.width = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            }
            dot.setBackground(gd);
            dot.setLayoutParams(params);
        }
    }

    private void setupViewPagerCallback() {
        if (viewPager == null) return;
        viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                if (parallaxBg != null) {
                    float totalScroll = position + positionOffset;
                    int width = viewPager.getWidth() > 0 ? viewPager.getWidth() : getResources().getDisplayMetrics().widthPixels;
                    float intensity = 20f;
                    if (getContext() != null) {
                        CarLauncherSettings settings = new CarLauncherSettings(getContext());
                        intensity = settings.getParallaxIntensity();
                    }
                    float translationX = -totalScroll * width * (intensity / 100f); // Premium parallax kaydirma katsayisi
                    parallaxBg.setTranslationX(translationX);
                }
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePageIndicatorSelection(position);
                if (getContext() != null) {
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit().putInt("widget_panel_last_page", position).apply();
                }
            }
        });
    }

    private void updatePageIndicator() {
        if (viewPager != null && viewPager.getAdapter() != null) {
            setupPageIndicator(viewPager.getAdapter().getItemCount());
            updatePageIndicatorSelection(viewPager.getCurrentItem());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBackgroundStyle();
        if (getContext() != null) {
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .registerOnSharedPreferenceChangeListener(this);
        }
        if (getContext() != null) {
            WidgetManager wm = WidgetManager.getInstance(getContext());
            if (!wm.isHasAutoLaunched()) {
                wm.setHasAutoLaunched(true);
                new AutoLaunchManager(getContext()).execute();
            }
        }
        if (widgetManager != null) {
            boolean isPanelOpen = true;
            if (getActivity() instanceof app.organicmaps.activities.MapActivity) {
                isPanelOpen = ((app.organicmaps.activities.MapActivity) getActivity()).isWidgetPanelOpen();
            }
            
            if (isPanelOpen) {
                widgetManager.startAllWidgets();
            }
            
            if (viewPager != null && viewPager.getAdapter() != null) {
                viewPager.getAdapter().notifyDataSetChanged();
                updatePageIndicator();
            } else {
                applyWidgetsToView();
            }
        }
    }

    public void onPanelVisibilityChanged(boolean visible) {
        if (widgetManager != null) {
            if (visible) {
                widgetManager.startAllWidgets();
                if (viewPager != null && viewPager.getAdapter() != null) {
                    viewPager.getAdapter().notifyDataSetChanged();
                    updatePageIndicator();
                }
            } else {
                widgetManager.stopAllWidgets();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        WorkspacePageAdapter.setWorkspaceLongClickListener(null);
        if (getContext() != null) {
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
        if (widgetManager != null) {
            widgetManager.stopAllWidgets();
        }
    }

    private void initializeWidgets() {
        if (widgetManager == null || app == null) return;
        
        widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.Material3ClockWidget(getContext()));
        widgetManager.addWidget(new SpeedWidget(getContext(), app));
        widgetManager.addWidget(new DirectionWidget(getContext(), app));
        
        app.organicmaps.carlauncher.antenna.AntennaPlugin antennaPlugin = app.organicmaps.plugins.PluginsHelper
                .getPlugin(app.organicmaps.carlauncher.antenna.AntennaPlugin.class);
        if (antennaPlugin != null && antennaPlugin.isActive()) {
            widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.AntennaWidget(getContext(), app));
        }
        
        widgetManager.addWidget(new app.organicmaps.carlauncher.widgets.WeatherWidget(getContext(), app));
        widgetManager.addWidget(new NavigationWidget(getContext(), app));
        widgetManager.addWidget(new MusicWidget(getContext(), app));
        
        VehicleMetricsPlugin obdPlugin = PluginsHelper.getPlugin(VehicleMetricsPlugin.class);
        if (obdPlugin != null && obdPlugin.isActive()) {
            widgetManager.addWidget(new OBDWidget(getContext(), app));
        }
    }

    private void setupBottomNav(View navWidgets, View navNavigation, View navApps, View navSettings) {
        setActiveNav(navWidgets);

        if (navWidgets != null) {
            navWidgets.setOnClickListener(v -> {
                setActiveNav(navWidgets);
            });
        }
        if (navNavigation != null) {
            navNavigation.setOnClickListener(v -> {
                setActiveNav(navNavigation);
            });
        }
        if (navApps != null) {
            navApps.setOnClickListener(v -> {
                setActiveNav(navApps);
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    CarLauncherInterface ci = (CarLauncherInterface) getActivity();
                    ci.setPanelContent(PanelContentManager.PanelContent.APP_DRAWER);
                    ci.openAppDrawer();
                }
            });
        }
        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                setActiveNav(navSettings);
                if (getActivity() instanceof app.organicmaps.activities.MapActivity) {
                    ((app.organicmaps.activities.MapActivity) getActivity()).openCarLauncherSettings();
                }
            });
        }
    }

    private void setActiveNav(View active) {
        View root = getView();
        if (root == null) return;
        int[] navIds = {app.organicmaps.R.id.nav_widgets, app.organicmaps.R.id.nav_navigation, 
                        app.organicmaps.R.id.nav_apps, app.organicmaps.R.id.nav_settings};
        for (int id : navIds) {
            View v = root.findViewById(id);
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(0xFF888888);
            }
        }
        if (active instanceof TextView) {
            ((TextView) active).setTextColor(0xFFFFFFFF);
        }
    }

    public WidgetManager getWidgetManager() {
        return widgetManager;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (CarLauncherSettings.KEY_BACKGROUND_STYLE.equals(key)) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::updateBackgroundStyle);
            }
        }
    }

    private void updateBackgroundStyle() {
        if (parallaxBg == null || getContext() == null) return;
        CarLauncherSettings settings = new CarLauncherSettings(getContext());
        String style = settings.getBackgroundStyle();
        
        if ("custom".equals(style)) {
            String uriString = settings.getPrefs().getString("car_launcher_wallpaper_uri", null);
            if (uriString != null) {
                try {
                    android.net.Uri uri = android.net.Uri.parse(uriString);
                    parallaxBg.setImageURI(uri);
                    return;
                } catch (Exception e) {
                    android.util.Log.e("WidgetPanelFragment", "Wallpaper yuklenemedi", e);
                }
            }
        } else if ("system".equals(style)) {
            try {
                android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(getContext());
                android.graphics.drawable.Drawable drawable = wm.getDrawable();
                if (drawable != null) {
                    parallaxBg.setImageDrawable(drawable);
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("WidgetPanelFragment", "Sistem duvar kagidi yuklenemedi", e);
            }
        }
        
        int resId = app.organicmaps.R.drawable.bg_panel_modern;
        if ("carbon".equals(style)) {
            resId = app.organicmaps.R.drawable.bg_panel_carbon;
        } else if ("space".equals(style)) {
            resId = app.organicmaps.R.drawable.bg_panel_space;
        }
        parallaxBg.setImageResource(resId);
    }

    private void showWallpaperChooserDialog() {
        WallpaperChooserDialog dialog = new WallpaperChooserDialog();
        dialog.setOnWallpaperSelectedListener(new WallpaperChooserDialog.OnWallpaperSelectedListener() {
            @Override
            public void onWallpaperSelected(String style) {
                CarLauncherSettings settings = new CarLauncherSettings(getContext());
                settings.setBackgroundStyle(style);
                updateBackgroundStyle();
            }

            @Override
            public void onPickCustomWallpaper() {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("image/*");
                    startActivityForResult(intent, RC_SELECT_WALLPAPER);
                } catch (Exception e) {
                    android.widget.Toast.makeText(getContext(), "Dosya secici acilamadi", android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onSetSystemWallpaper() {
                CarLauncherSettings settings = new CarLauncherSettings(getContext());
                settings.setBackgroundStyle("system");
                updateBackgroundStyle();
            }

            @Override
            public void onOpenSystemWallpaperChooser() {
                try {
                    Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                    startActivity(Intent.createChooser(intent, "Duvar Kagidi Secin"));
                    
                    // Geri donuldugunde otomatik sistemi yuklesin
                    CarLauncherSettings settings = new CarLauncherSettings(getContext());
                    settings.setBackgroundStyle("system");
                } catch (Exception e) {
                    android.widget.Toast.makeText(getContext(), "Sistem duvar kagidi secicisi acilamadi", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        dialog.show(getChildFragmentManager(), "WallpaperChooserDialog");
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getContext() != null && widgetManager != null) {
            widgetManager.updateActivityContext(getContext());
            widgetManager.loadWidgetConfig();
            applyWidgetsToView();
        }
    }
}
