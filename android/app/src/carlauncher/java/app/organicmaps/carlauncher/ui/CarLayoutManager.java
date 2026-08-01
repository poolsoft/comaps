package app.organicmaps.carlauncher.ui;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherActivity;
import app.organicmaps.carlauncher.CarLauncherSettings;

/**
 * Manages the layout constraints and UI state for the CarLauncher interface.
 * Decouples layout logic from app.organicmaps.MwmActivity.
 */
public class CarLayoutManager {

    private final CarLauncherActivity activity;
    private final ConstraintLayout rootLayout;
    private final View mapContainer;
    private final View widgetPanel;
    private final View appDock;
    private final View appDrawerContainer;
    private final ImageButton widgetHandle;

    public CarLayoutManager(CarLauncherActivity activity) {
        this.activity = activity;
        this.rootLayout = activity.findViewById(R.id.root_layout);
        this.mapContainer = activity.findViewById(R.id.car_map_container);
        this.widgetPanel = activity.findViewById(R.id.widget_panel);
        this.appDock = activity.findViewById(R.id.app_dock);
        this.appDrawerContainer = activity.findViewById(R.id.app_drawer_container);
        this.widgetHandle = activity.findViewById(R.id.widget_handle);
    }

    /** Uses actual window bounds because some head-unit ROMs report stale orientation. */
    public static boolean isPortraitWindow(android.app.Activity activity) {
        View root = activity.findViewById(R.id.root_layout);
        if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
            return root.getHeight() >= root.getWidth();
        }
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            return metrics.heightPixels >= metrics.widthPixels;
        }
        return activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
    }

    private boolean isContentFullScreen = false;

    public void setContentFullScreen(boolean fullScreen) {
        this.isContentFullScreen = fullScreen;
    }

    public boolean isContentFullScreen() {
        return isContentFullScreen;
    }

    private boolean rootMatchesCurrentOrientation() {
        if (rootLayout == null || rootLayout.getWidth() <= 0 || rootLayout.getHeight() <= 0) {
            return false;
        }
        boolean portrait = isPortraitWindow(activity);
        return portrait == (rootLayout.getHeight() >= rootLayout.getWidth());
    }

    private int getCurrentContentWidth() {
        if (rootMatchesCurrentOrientation()) {
            return rootLayout.getWidth() - rootLayout.getPaddingLeft()
                    - rootLayout.getPaddingRight();
        }
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    private int getCurrentContentHeight() {
        if (rootMatchesCurrentOrientation()) {
            return rootLayout.getHeight() - rootLayout.getPaddingTop()
                    - rootLayout.getPaddingBottom();
        }
        return activity.getResources().getDisplayMetrics().heightPixels;
    }

    public int getAvailablePanelWidth() {
        int width = getCurrentContentWidth();
        boolean portrait = isPortraitWindow(activity);
        String dockPosition =
                new CarLauncherSettings(activity).getEffectiveDockPosition(portrait);
        if (appDock != null && appDock.getVisibility() == View.VISIBLE
                && ("left".equals(dockPosition) || "right".equals(dockPosition))) {
            width -= appDock.getWidth();
        }
        return Math.max(0, width);
    }

    public int getAvailablePanelHeight() {
        int height = getCurrentContentHeight();
        boolean portrait = isPortraitWindow(activity);
        String dockPosition =
                new CarLauncherSettings(activity).getEffectiveDockPosition(portrait);
        if (appDock != null && appDock.getVisibility() == View.VISIBLE
                && "bottom".equals(dockPosition)) {
            height -= appDock.getHeight();
        }
        return Math.max(0, height);
    }

    public float getRenderedSmallPanelFraction() {
        boolean portrait = isPortraitWindow(activity);
        View smallView = isContentFullScreen ? mapContainer : widgetPanel;
        int available = portrait ? getAvailablePanelHeight() : getAvailablePanelWidth();
        int rendered = smallView == null ? 0
                : (portrait ? smallView.getHeight() : smallView.getWidth());
        if (available <= 0 || rendered <= 0) {
            return -1f;
        }
        return Math.max(0.15f, Math.min(0.65f, rendered / (float) available));
    }

    public void applyLayout(boolean isWidgetPanelOpen, int layoutMode) {
        if (rootLayout == null || widgetPanel == null || appDock == null) return;

        // PiP modunda normal cizim yapilmasini engelle, sadece PiP yerlesimini koru (Turkce karakter yok)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity.isInPictureInPictureMode()) {
            applyPipLayout(true);
            return;
        }

        CarLauncherSettings carSettings = new CarLauncherSettings(activity);
        boolean isPortrait = isPortraitWindow(activity);
        boolean configurationPortrait = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait != configurationPortrait) {
            android.util.Log.w("CarLayoutManager", "Orientation mismatch: windowPortrait="
                    + isPortrait + ", configurationPortrait=" + configurationPortrait
                    + ", root=" + rootLayout.getWidth() + "x" + rootLayout.getHeight());
        }
        String dockPos = carSettings.getEffectiveDockPosition(isPortrait);
        String widgetPos = carSettings.getWidgetPanelPosition();

        ConstraintSet cs = new ConstraintSet();
        cs.clone(rootLayout);

        // 1. Reset all regions (widget_handle'i resetleme - elle yonetilir)
        int[] ids = {R.id.app_dock, R.id.widget_panel, R.id.car_map_container, R.id.app_drawer_container};
        for (int id : ids) {
            cs.clear(id, ConstraintSet.TOP);
            cs.clear(id, ConstraintSet.BOTTOM);
            cs.clear(id, ConstraintSet.START);
            cs.clear(id, ConstraintSet.END);
        }

        // Portrait is always a launcher layout: bottom dock plus map/widget stack.
        // A map-only portrait branch conflicts with head-unit portrait mode and can
        // leave the activity looking like stock CoMaps after a configuration change.
        cs.setVisibility(R.id.app_dock, View.VISIBLE);

        // 2. Dock Region - dockSize (0-100) ayarina gore olceklendir
        // 0=min(0.3x), 50=normal(1.0x), 100=max(1.7x)
        int dockSizePercent = carSettings.getEffectiveDockSize(isPortrait);
        float dockScale = 0.3f + (dockSizePercent / 100.0f) * 1.4f;
        int dockSize = (int) (activity.getResources().getDimension(R.dimen.dock_height) * dockScale);
        int sidebarWidth = (int) (64 * activity.getResources().getDisplayMetrics().density * dockScale);
        
        // Asgari boyut sinirlamasi (Clipped buton ve widgetlari engellemek icin minimum 50dp)
        int minAllowedSize = (int) (50 * activity.getResources().getDisplayMetrics().density);
        if (dockSize < minAllowedSize) {
            dockSize = minAllowedSize;
        }
        if (sidebarWidth < minAllowedSize) {
            sidebarWidth = minAllowedSize;
        }
        
        switch (dockPos) {
            case "left":
                cs.connect(R.id.app_dock, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.app_dock, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                cs.constrainWidth(R.id.app_dock, sidebarWidth);
                cs.constrainHeight(R.id.app_dock, 0);
                break;
            case "right":
                cs.connect(R.id.app_dock, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                cs.connect(R.id.app_dock, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                cs.constrainWidth(R.id.app_dock, sidebarWidth);
                cs.constrainHeight(R.id.app_dock, 0);
                break;
            default: // bottom
                cs.connect(R.id.app_dock, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                cs.connect(R.id.app_dock, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.app_dock, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                cs.constrainHeight(R.id.app_dock, dockSize);
                cs.constrainWidth(R.id.app_dock, 0);
                break;
        }

        // 3. Widget ve Harita Alanlari - Harita herzaman ekranda kalacak sekilde swap mantigi
        float panelPercent = carSettings.getWidgetPanelWidthPercent();
        int screenWidth = getCurrentContentWidth();
        int screenHeight = getCurrentContentHeight();

        if (activity.isDesktopMode()) {
            cs.setVisibility(R.id.car_map_container, View.GONE);
            cs.setVisibility(R.id.widget_handle, View.GONE);
            cs.setVisibility(R.id.widget_panel, View.VISIBLE);

            cs.connect(R.id.widget_panel, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            
            if (isPortrait || "bottom".equals(dockPos)) {
                cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, R.id.app_dock, ConstraintSet.TOP);
                cs.connect(R.id.widget_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.widget_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            } else if ("left".equals(dockPos)) {
                cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                cs.connect(R.id.widget_panel, ConstraintSet.START, R.id.app_dock, ConstraintSet.END);
                cs.connect(R.id.widget_panel, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            } else { // right
                cs.connect(R.id.widget_panel, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                cs.connect(R.id.widget_panel, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.widget_panel, ConstraintSet.END, R.id.app_dock, ConstraintSet.START);
            }
            cs.constrainWidth(R.id.widget_panel, 0);
            cs.constrainHeight(R.id.widget_panel, 0);
        } else if (!isWidgetPanelOpen) {
            cs.setVisibility(R.id.car_map_container, View.VISIBLE);
            cs.setVisibility(R.id.widget_panel, View.GONE);
            // Harita tum ekrani kaplar (dock haric)
            cs.connect(R.id.car_map_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            if (isPortrait) {
                cs.connect(R.id.car_map_container, ConstraintSet.BOTTOM, "bottom".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID, "bottom".equals(dockPos) ? ConstraintSet.TOP : ConstraintSet.BOTTOM);
                cs.connect(R.id.car_map_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.car_map_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            } else {
                cs.connect(R.id.car_map_container, ConstraintSet.START, "left".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID, "left".equals(dockPos) ? ConstraintSet.END : ConstraintSet.START);
                cs.connect(R.id.car_map_container, ConstraintSet.END, "right".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID, "right".equals(dockPos) ? ConstraintSet.START : ConstraintSet.END);
                cs.connect(R.id.car_map_container, ConstraintSet.BOTTOM, "bottom".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID, "bottom".equals(dockPos) ? ConstraintSet.TOP : ConstraintSet.BOTTOM);
            }
            cs.constrainWidth(R.id.car_map_container, 0);
            cs.constrainHeight(R.id.car_map_container, 0);
        } else {
            cs.setVisibility(R.id.car_map_container, View.VISIBLE);
            cs.setVisibility(R.id.widget_panel, View.VISIBLE);
            boolean isSwapped = isContentFullScreen;

            if (isPortrait || "bottom".equals(widgetPos)) {
                // Dikey yerlesim: Ust Panel ve Alt Panel
                int topViewId;
                int bottomViewId;
                if (isSwapped) {
                    topViewId = R.id.widget_panel;   // LARGE
                    bottomViewId = R.id.car_map_container; // SMALL
                } else {
                    topViewId = R.id.car_map_container;  // LARGE
                    bottomViewId = R.id.widget_panel;  // SMALL
                }

                float portraitPanelHeight = carSettings.getWidgetPanelHeightPortrait();
                int availableHeight = screenHeight - ("bottom".equals(dockPos) ? dockSize : 0);
                int smallHeight = (int) (availableHeight * portraitPanelHeight);
                float density = activity.getResources().getDisplayMetrics().density;
                int gapSize = (int) (8 * density); // Premium 8dp bosluk

                // Her iki gorunum de yatayda yayilir
                int leftBorder = "left".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID;
                int rightBorder = "right".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID;
                int leftSide = "left".equals(dockPos) ? ConstraintSet.END : ConstraintSet.START;
                int rightSide = "right".equals(dockPos) ? ConstraintSet.START : ConstraintSet.END;

                cs.connect(topViewId, ConstraintSet.START, leftBorder, leftSide);
                cs.connect(topViewId, ConstraintSet.END, rightBorder, rightSide);
                cs.constrainWidth(topViewId, 0);

                cs.connect(bottomViewId, ConstraintSet.START, leftBorder, leftSide);
                cs.connect(bottomViewId, ConstraintSet.END, rightBorder, rightSide);
                cs.constrainWidth(bottomViewId, 0);

                // Dikey zincirleme (Vertical chains) - Araya 16dp bosluk eklenir
                cs.connect(topViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(topViewId, ConstraintSet.BOTTOM, bottomViewId, ConstraintSet.TOP, gapSize);

                cs.connect(bottomViewId, ConstraintSet.TOP, topViewId, ConstraintSet.BOTTOM, gapSize);
                cs.connect(bottomViewId, ConstraintSet.BOTTOM, "bottom".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID, "bottom".equals(dockPos) ? ConstraintSet.TOP : ConstraintSet.BOTTOM);

                // Tutamac (widget_handle) dikey boslugun tam ortasina dairesel olarak hizalanir
                cs.connect(R.id.widget_handle, ConstraintSet.TOP, topViewId, ConstraintSet.BOTTOM);
                cs.connect(R.id.widget_handle, ConstraintSet.BOTTOM, bottomViewId, ConstraintSet.TOP);
                cs.connect(R.id.widget_handle, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                cs.connect(R.id.widget_handle, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                
                int handleSize = (int) (40 * density);
                cs.constrainWidth(R.id.widget_handle, handleSize);
                cs.constrainHeight(R.id.widget_handle, handleSize);

                // Yukseklikleri sinirla
                cs.constrainHeight(topViewId, 0); // LARGE gorunum kalan alani doldurur
                cs.constrainHeight(bottomViewId, smallHeight); // SMALL gorunum sabit/hesapli yukseklik alir

                View clockContainer = activity.findViewById(R.id.clock_settings_container);
                if (clockContainer != null) clockContainer.setVisibility(isPortrait ? View.GONE : View.VISIBLE);

            } else {
                // Yatay yerlesim: Sol Panel ve Sag Panel
                int leftBorder = "left".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID;
                int rightBorder = "right".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID;
                int leftSide = "left".equals(dockPos) ? ConstraintSet.END : ConstraintSet.START;
                int rightSide = "right".equals(dockPos) ? ConstraintSet.START : ConstraintSet.END;

                boolean isFixed = "fixed".equals(carSettings.getExpansionBehavior());
                int leftViewId;
                int rightViewId;
                boolean leftViewIsSmall = "left".equals(widgetPos);

                if (isFixed) {
                    if (leftViewIsSmall) {
                        leftViewId = R.id.widget_panel;
                        rightViewId = R.id.car_map_container;
                    } else {
                        leftViewId = R.id.car_map_container;
                        rightViewId = R.id.widget_panel;
                    }
                } else {
                    if (leftViewIsSmall) {
                        if (isSwapped) {
                            // SMALL solda (harita), LARGE sagda (widget)
                            leftViewId = R.id.car_map_container;
                            rightViewId = R.id.widget_panel;
                        } else {
                            // SMALL solda (widget), LARGE sagda (harita)
                            leftViewId = R.id.widget_panel;
                            rightViewId = R.id.car_map_container;
                        }
                    } else {
                        // Sagdaki panel SMALL
                        if (isSwapped) {
                            // LARGE solda (widget), SMALL sagda (harita)
                            leftViewId = R.id.widget_panel;
                            rightViewId = R.id.car_map_container;
                        } else {
                            // LARGE solda (harita), SMALL sagda (widget)
                            leftViewId = R.id.car_map_container;
                            rightViewId = R.id.widget_panel;
                        }
                    }
                }

                float density = activity.getResources().getDisplayMetrics().density;
                int availableWidth = screenWidth
                        - (("left".equals(dockPos) || "right".equals(dockPos))
                        ? sidebarWidth : 0);
                int smallWidth = (int) (availableWidth * panelPercent);
                int gapSize = (int) (8 * density); // Premium 8dp bosluk

                // Her iki gorunum de dikeyde yayilir
                int bottomBorder = "bottom".equals(dockPos) ? R.id.app_dock : ConstraintSet.PARENT_ID;
                int bottomSide = "bottom".equals(dockPos) ? ConstraintSet.TOP : ConstraintSet.BOTTOM;

                cs.connect(leftViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(leftViewId, ConstraintSet.BOTTOM, bottomBorder, bottomSide);
                cs.constrainHeight(leftViewId, 0);

                cs.connect(rightViewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(rightViewId, ConstraintSet.BOTTOM, bottomBorder, bottomSide);
                cs.constrainHeight(rightViewId, 0);

                // Yatay zincirleme (Horizontal chains) - Araya 16dp bosluk eklenir
                cs.connect(leftViewId, ConstraintSet.START, leftBorder, leftSide);
                cs.connect(leftViewId, ConstraintSet.END, rightViewId, ConstraintSet.START, gapSize);

                cs.connect(rightViewId, ConstraintSet.START, leftViewId, ConstraintSet.END, gapSize);
                cs.connect(rightViewId, ConstraintSet.END, rightBorder, rightSide);

                // Tutamac (widget_handle) yatay boslugun tam ortasina dairesel olarak hizalanir
                cs.connect(R.id.widget_handle, ConstraintSet.START, leftViewId, ConstraintSet.END);
                cs.connect(R.id.widget_handle, ConstraintSet.END, rightViewId, ConstraintSet.START);
                cs.connect(R.id.widget_handle, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                cs.connect(R.id.widget_handle, ConstraintSet.BOTTOM, bottomBorder, bottomSide);
                
                int handleSize = (int) (40 * density);
                cs.constrainWidth(R.id.widget_handle, handleSize);
                cs.constrainHeight(R.id.widget_handle, handleSize);

                // Genislikleri yerlesime gore ayarla
                if (isFixed) {
                    if (leftViewIsSmall) {
                        if (isSwapped) {
                            cs.constrainWidth(R.id.widget_panel, 0);
                            cs.constrainWidth(R.id.car_map_container, smallWidth);
                        } else {
                            cs.constrainWidth(R.id.widget_panel, smallWidth);
                            cs.constrainWidth(R.id.car_map_container, 0);
                        }
                    } else {
                        if (isSwapped) {
                            cs.constrainWidth(R.id.car_map_container, smallWidth);
                            cs.constrainWidth(R.id.widget_panel, 0);
                        } else {
                            cs.constrainWidth(R.id.car_map_container, 0);
                            cs.constrainWidth(R.id.widget_panel, smallWidth);
                        }
                    }
                } else {
                    if (leftViewIsSmall) {
                        cs.constrainWidth(leftViewId, smallWidth);
                        cs.constrainWidth(rightViewId, 0);
                    } else {
                        cs.constrainWidth(leftViewId, 0);
                        cs.constrainWidth(rightViewId, smallWidth);
                    }
                }
            }
        }

        // 5. App Drawer Region
        cs.connect(R.id.app_drawer_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        cs.connect(R.id.app_drawer_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        cs.connect(R.id.app_drawer_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        if (isPortrait) {
            cs.connect(R.id.app_drawer_container, ConstraintSet.BOTTOM, isWidgetPanelOpen ? R.id.widget_panel : R.id.app_dock, ConstraintSet.TOP);
        } else {
            cs.connect(R.id.app_drawer_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        }

        // 6. Handle constraint'lerini ayarla (visibility durumunu ConstraintSet uzerinden guvenle yonetiriz)
        updateWidgetHandleConstraints(cs, carSettings, isWidgetPanelOpen);

        // 7. Final UI Touch-ups (Elevation, etc)
        updateElevations(isPortrait);

        // 8. APPLY ALL CONSTRAINTS AT ONCE
        cs.applyTo(rootLayout);

        // 9. BRING TO FRONT (Z-INDEX)
        if (widgetHandle != null) {
            widgetHandle.bringToFront();
        }

        // 10. TRANSLATIONX - constraint uygulandiktan SONRA (Sifirlayip ikonu 3-nokta yapar)
        applyWidgetHandleTranslation(carSettings, isWidgetPanelOpen);
        
        // 11. Refresh Dock orientation
        boolean isVertical = ("left".equals(dockPos) || "right".equals(dockPos)) && !isPortrait;
        refreshDockFragment(isVertical);

    }

    private void updateElevations(boolean isPortrait) {
        if (mapContainer != null) mapContainer.setElevation(0f);
        // Yatay modda muzik panelinin (15f) ustunde kalmasi ve dokunma onceligi icin elevation yukseltildi
        if (appDock != null) appDock.setElevation(isPortrait ? 10f : 20f);
        if (widgetPanel != null) widgetPanel.setElevation(isPortrait ? 2f : 15f);
        if (appDrawerContainer != null) appDrawerContainer.setElevation(50f);
        if (widgetHandle != null) {
            widgetHandle.setElevation(25f);
            widgetHandle.setZ(25f);
        }
    }

    private void updateWidgetHandleConstraints(ConstraintSet cs, CarLauncherSettings settings, boolean isOpen) {
        if (widgetHandle != null) {
            if (!isOpen || activity.isDesktopMode()) {
                cs.setVisibility(R.id.widget_handle, View.GONE);
            } else {
                cs.setVisibility(R.id.widget_handle, View.VISIBLE);
            }
        }
    }

    /**
     * Constraint uygulandiktan SONRA translation/pozisyon ayarlanir.
     */
    private void applyWidgetHandleTranslation(CarLauncherSettings settings, boolean isOpen) {
        if (widgetHandle == null) return;
        
        // Sifirlamalar ve transparan arka plan
        widgetHandle.setTranslationX(0);
        widgetHandle.setTranslationY(0);
        widgetHandle.setBackground(null);
        widgetHandle.setPadding(0, 0, 0, 0);
        
        // Premium grab indicator olarak ikon ve renk set edilir
        widgetHandle.setImageResource(R.drawable.ic_more_vert);
        widgetHandle.setColorFilter(0xCCFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
        
        // Yonelime gore 3-noktayi donduruyoruz (Dikey modda yatay dots, yatay modda dikey dots)
        boolean isPortrait = isPortraitWindow(activity);
        if (isPortrait) {
            widgetHandle.setRotation(90f);
        } else {
            widgetHandle.setRotation(0f);
        }
    }

    private void refreshDockFragment(boolean isVertical) {
        if (activity instanceof app.organicmaps.carlauncher.CarLauncherActivity) {
            // AppDockFragment dock = ((app.organicmaps.carlauncher.CarLauncherActivity)activity).getAppDockFragment();
            // if (dock != null) {
            //    dock.setOrientation(isVertical);
            // }
        }
    }

    public void applyPipLayout(boolean isInPip) {
        if (rootLayout == null || mapContainer == null) return;

        ConstraintSet cs = new ConstraintSet();
        cs.clone(rootLayout);

        if (isInPip) {
            // PiP modunda diger her seyi gizle (Turkce karakter yok)
            cs.setVisibility(R.id.app_dock, View.GONE);
            cs.setVisibility(R.id.widget_panel, View.GONE);
            cs.setVisibility(R.id.widget_handle, View.GONE);
            cs.setVisibility(R.id.app_drawer_container, View.GONE);

            // Haritayi tam ekran yap (Turkce karakter yok)
            cs.clear(R.id.car_map_container, ConstraintSet.TOP);
            cs.clear(R.id.car_map_container, ConstraintSet.BOTTOM);
            cs.clear(R.id.car_map_container, ConstraintSet.START);
            cs.clear(R.id.car_map_container, ConstraintSet.END);

            cs.connect(R.id.car_map_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            cs.connect(R.id.car_map_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            cs.connect(R.id.car_map_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            cs.connect(R.id.car_map_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            cs.constrainWidth(R.id.car_map_container, 0);
            cs.constrainHeight(R.id.car_map_container, 0);
        } else {
            // PiP modundan cikildiginda normal layout'a donmek icin applyLayout cagriliyor (Turkce karakter yok)
            cs.setVisibility(R.id.app_dock, View.VISIBLE);
            cs.setVisibility(R.id.widget_panel, View.VISIBLE);
        }

        cs.applyTo(rootLayout);
    }
}

