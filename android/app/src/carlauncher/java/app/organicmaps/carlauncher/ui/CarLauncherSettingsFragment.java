package app.organicmaps.carlauncher.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import app.organicmaps.R;

import app.organicmaps.carlauncher.CarLauncherSettings;

import app.organicmaps.carlauncher.dock.AppDockManager;
import app.organicmaps.carlauncher.dock.AppPickerDialog;
import app.organicmaps.carlauncher.music.MusicManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Car Launcher Ayarlar Fragmenti.
 * Tum ayarlari gruplar halinde gosterir ve yonetir.
 */
public class CarLauncherSettingsFragment extends PreferenceFragmentCompat {

    public static final String TAG = "CarLauncherSettingsFragment";

    private CarLauncherSettings settings;


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName("car_launcher_prefs");
        setPreferencesFromResource(R.xml.carlauncher_prefs, rootKey);

        if (getContext() != null) {
            settings = new CarLauncherSettings(getContext());
        }

        setupAppearancePrefs();
        setupLanguagePrefs();
        setupMusicPrefs();
        setupAutoLaunchPrefs();
        setupBackupPrefs();
        setupDockPrefs();
        setupAssistantPrefs();
        setupAboutPrefs();
    }

    private android.widget.LinearLayout splitContainer;
    private androidx.preference.PreferenceCategory currentActiveCategory;
    private final List<androidx.preference.PreferenceCategory> allCategories = new ArrayList<>();
    private View selectionHighlight;
    private android.widget.LinearLayout categoriesList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        // Wrap inflater context with Material 3 Dark theme to fix black text on dark background
        android.content.Context context = getContext();
        LayoutInflater themedInflater = inflater;
        if (context != null) {
            android.content.Context themedContext = new androidx.appcompat.view.ContextThemeWrapper(context, com.google.android.material.R.style.Theme_Material3_Dark);
            themedInflater = inflater.cloneInContext(themedContext);
        }
        // Super creates the RecyclerView for preferences default view
        View prefsView = super.onCreateView(themedInflater, container, savedInstanceState);
        if (prefsView == null) return null;

        // Determine Orientation
        boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
             return createSplitLayout(prefsView);
        } else {
             return createSingleLayout(prefsView);
        }
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    private View createTitleBar() {
        android.widget.RelativeLayout titleBar = new android.widget.RelativeLayout(getContext());
        titleBar.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        titleBar.setBackgroundColor(0xFF14141C); // Sleek Space Grey
        titleBar.setElevation(8f);

        // Title
        android.widget.TextView titleView = new android.widget.TextView(getContext());
        titleView.setText(R.string.car_settings_title);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTextSize(22);
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        titleView.setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16));
        
        android.widget.RelativeLayout.LayoutParams titleParams = new android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_START);
        titleParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        titleBar.addView(titleView, titleParams);

        // Close Button
        android.widget.ImageButton closeBtn = new android.widget.ImageButton(getContext());
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        closeBtn.setBackgroundResource(outValue.resourceId);
        closeBtn.setColorFilter(0xFFFFFFFF);
        closeBtn.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        closeBtn.setOnClickListener(v -> closeSettings());

        android.widget.RelativeLayout.LayoutParams btnParams = new android.widget.RelativeLayout.LayoutParams(
                dpToPx(56), dpToPx(56)); 
        btnParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        btnParams.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        btnParams.setMarginEnd(dpToPx(8));
        titleBar.addView(closeBtn, btnParams);

        return titleBar;
    }

    private View createSingleLayout(View prefsView) {
        prefsView.setBackgroundColor(0xFF0B0B0E);
        if (prefsView instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) prefsView;
            rv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
            rv.setClipToPadding(false);
        }

        android.widget.LinearLayout mainContainer = new android.widget.LinearLayout(getContext());
        mainContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        mainContainer.setFitsSystemWindows(true);

        mainContainer.addView(createTitleBar());

        android.widget.LinearLayout.LayoutParams prefsParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        mainContainer.addView(prefsView, prefsParams);

        restoreAllCategories();
        return mainContainer;
    }

    private View createSplitLayout(View prefsView) {
        android.widget.LinearLayout mainContainer = new android.widget.LinearLayout(getContext());
        mainContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainContainer.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        mainContainer.setBackgroundColor(0xFF0B0B0E);

        mainContainer.addView(createTitleBar());

        splitContainer = new android.widget.LinearLayout(getContext());
        splitContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.LinearLayout.LayoutParams splitParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        splitContainer.setLayoutParams(splitParams);

        // --- Left Pane: Categories ---
        android.widget.ScrollView leftScroll = new android.widget.ScrollView(getContext());
        leftScroll.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0.3f));
        leftScroll.setBackgroundColor(0xFF14141C); // Sleek Space Grey
        
        categoriesList = new android.widget.LinearLayout(getContext());
        categoriesList.setOrientation(android.widget.LinearLayout.VERTICAL);
        categoriesList.setPadding(0, dpToPx(8), 0, dpToPx(24));
        leftScroll.addView(categoriesList);
        
        splitContainer.addView(leftScroll);
        
        // --- Divider ---
        View divider = new View(getContext());
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                dpToPx(1), android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        divider.setBackgroundColor(0xFF222232);
        splitContainer.addView(divider);

        // --- Right Pane: Content ---
        android.widget.FrameLayout rightPane = new android.widget.FrameLayout(getContext());
        rightPane.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0.7f));
        rightPane.setBackgroundColor(0xFF0B0B0E);
        
        if (prefsView.getParent() != null) {
            ((android.view.ViewGroup)prefsView.getParent()).removeView(prefsView);
        }
        
        if (prefsView instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) prefsView;
            rv.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16));
            rv.setClipToPadding(false);
            rv.setBackgroundColor(0xFF0B0B0E);
        }
        rightPane.addView(prefsView);
        splitContainer.addView(rightPane);

        mainContainer.addView(splitContainer);

        setupCategoriesList();
        return mainContainer;
    }

    private void restoreAllCategories() {
         for (androidx.preference.PreferenceCategory cat : allCategories) {
             cat.setVisible(true);
         }
    }

    private void setupCategoriesList() {
        allCategories.clear();
        categoriesList.removeAllViews();
        
        androidx.preference.PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference p = screen.getPreference(i);
            if (p instanceof androidx.preference.PreferenceCategory) {
                androidx.preference.PreferenceCategory cat = (androidx.preference.PreferenceCategory) p;
                allCategories.add(cat);
                addCategoryToMenu(cat);
                
                // SaÃ„Å¸ panelde (Detay) bu Kategori baÃ…Å¸lÃ„Â±Ã„Å¸Ã„Â±nÃ„Â±n gereksiz yer/padding kaplamamasÃ„Â± iÃƒÂ§in siliyoruz.
                // Sol menÃƒÂ¼ye (Master) baÃ…Å¸lÃ„Â±k ve ikon kopyalandÃ„Â±Ã„Å¸Ã„Â± iÃƒÂ§in orasÃ„Â± etkilenmez.
                cat.setTitle(null);
                cat.setIcon(null);
                cat.setIconSpaceReserved(false);
                cat.setLayoutResource(R.layout.empty_preference_category);
            }
        }
        
        // Select first default
        if (!allCategories.isEmpty()) {
            selectCategory(allCategories.get(0));
        }
    }

    private void addCategoryToMenu(androidx.preference.PreferenceCategory cat) {
        android.widget.TextView item = new android.widget.TextView(getContext());
        item.setText(cat.getTitle());
        item.setTextColor(0xFF8E8E93);
        item.setTextSize(15);
        item.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        item.setTag(cat);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        item.setMaxLines(2);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            item.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE);
        }
        
        android.graphics.drawable.Drawable icon = cat.getIcon();
        if (icon != null) {
            android.graphics.drawable.Drawable wrappedIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(icon.mutate());
            wrappedIcon.setBounds(0, 0, dpToPx(24), dpToPx(24));
            androidx.core.graphics.drawable.DrawableCompat.setTint(wrappedIcon, 0xFF8E8E93);
            item.setCompoundDrawables(wrappedIcon, null, null, null);
            item.setCompoundDrawablePadding(dpToPx(8));
        }

        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        item.setLayoutParams(lp);

        item.setOnClickListener(v -> selectCategory(cat));
        categoriesList.addView(item);
    }
    
    private void selectCategory(androidx.preference.PreferenceCategory target) {
        // Toggle Visibility
        for (androidx.preference.PreferenceCategory cat : allCategories) {
            cat.setVisible(cat == target);
        }
        
        // Update Menu UI (Highlight with HSL blue gradient/borders)
        for (int i = 0; i < categoriesList.getChildCount(); i++) {
            View child = categoriesList.getChildAt(i);
            if (child instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) child;
                android.graphics.drawable.Drawable[] drawables = tv.getCompoundDrawables();
                
                if (tv.getTag() == target) {
                    tv.setTextColor(0xFF3D63FF);
                    tv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                    if (drawables[0] != null) {
                        androidx.core.graphics.drawable.DrawableCompat.setTint(drawables[0], 0xFF3D63FF);
                    }
                    
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setCornerRadius(dpToPx(12));
                    gd.setColor(0x1E3D63FF); // 12% opacity bright blue
                    gd.setStroke(dpToPx(1), 0xFF3D63FF); // Solid premium blue border
                    tv.setBackground(gd);
                } else {
                    tv.setTextColor(0xFF8E8E93);
                    tv.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
                    if (drawables[0] != null) {
                        androidx.core.graphics.drawable.DrawableCompat.setTint(drawables[0], 0xFF8E8E93);
                    }
                    
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setCornerRadius(dpToPx(12));
                    gd.setColor(0x00000000);
                    tv.setBackground(gd);
                }
            }
        }
        
        currentActiveCategory = target;
    }

    private void closeSettings() {
        if (getActivity() != null) {
            if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity()).closeAppDrawer();
            } else {
                getActivity().onBackPressed();
            }
        }
    }



    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // GÃƒâ€“RÃƒÅ“NÃƒÅ“M AYARLARI
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private void setupAppearancePrefs() {
        // Status Bar
        SwitchPreferenceCompat statusBarPref = findPreference(CarLauncherSettings.KEY_STATUS_BAR);
        if (statusBarPref != null) {
            statusBarPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean show = (Boolean) newValue;
                applyStatusBarVisibility(show);
                return true;
            });
        }

        // Dark Theme
        SwitchPreferenceCompat themePref = findPreference(CarLauncherSettings.KEY_DARK_THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                Toast.makeText(getContext(), "Tema degisikligi uygulamanin yeniden baslatilmasini gerektirir",
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // Widget Display Mode
        androidx.preference.ListPreference displayModePref = findPreference(CarLauncherSettings.KEY_WIDGET_DISPLAY_MODE);
        if (displayModePref != null) {
            displayModePref.setEntries(new CharSequence[]{"Liste (VarsayÃ„Â±lan)", "SayfalÃ„Â± (Carousel)"});
            displayModePref.setEntryValues(new CharSequence[]{"0", "1"});
            displayModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                CarLauncherSettings settings = new CarLauncherSettings(getContext());
                try {
                    settings.setWidgetDisplayMode(Integer.parseInt((String) newValue));
                } catch (NumberFormatException e) {
                    settings.setWidgetDisplayMode(0);
                }

                Toast.makeText(getContext(), "GÃƒÂ¶rÃƒÂ¼nÃƒÂ¼m deÃ„Å¸iÃ…Å¸ikliÃ„Å¸i iÃƒÂ§in widget paneli yenilenecek",
                        Toast.LENGTH_SHORT).show();
                
                 if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Widget Manager
        Preference widgetPref = findPreference("car_launcher_widget_manager");
        if (widgetPref != null) {
            widgetPref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Widget ayarlari ust panelde duzenlenir", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        // Dikey modda sadece harita
        SwitchPreferenceCompat portraitMapOnlyPref = findPreference(CarLauncherSettings.KEY_PORTRAIT_MAP_ONLY);
        if (portraitMapOnlyPref != null) {
            portraitMapOnlyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean val = (Boolean) newValue;
                if (settings != null) {
                    settings.setPortraitMapOnly(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Yuzen yardimci buton
        SwitchPreferenceCompat floatingButtonPref = findPreference(CarLauncherSettings.KEY_FLOATING_BUTTON);
        if (floatingButtonPref != null) {
            floatingButtonPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean val = (Boolean) newValue;
                if (settings != null) {
                    settings.setFloatingButtonEnabled(val);
                }
                if (getContext() != null) {
                    CarFloatingButtonManager.getInstance(getContext()).updateButtonState();
                }
                return true;
            });
        }

        // Yuzen Buton Surekli GPS (Force GPS)
        SwitchPreferenceCompat floatingButtonForceGpsPref = findPreference(CarLauncherSettings.KEY_FLOATING_BUTTON_FORCE_GPS);
        if (floatingButtonForceGpsPref != null) {
            floatingButtonForceGpsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean val = (Boolean) newValue;
                if (settings != null) {
                    settings.setFloatingButtonForceGpsEnabled(val);
                }
                return true;
            });
        }

        // Yuzen Buton Boyutu
        SeekBarPreference floatingButtonSizePref = findPreference(CarLauncherSettings.KEY_FLOATING_BUTTON_SIZE);
        if (floatingButtonSizePref != null) {
            floatingButtonSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                int val = (Integer) newValue;
                if (settings != null) {
                    settings.setFloatingButtonSize(val);
                }
                if (getContext() != null) {
                    // Boyut degisikligini anlik yansitmak icin butonu kapatip aciyoruz
                    CarFloatingButtonManager mgr = CarFloatingButtonManager.getInstance(getContext());
                    mgr.hideButton();
                    mgr.updateButtonState();
                }
                return true;
            });
        }

        // Gece karartma modu (Turkce karakter yok)
        androidx.preference.ListPreference nightDimModePref = findPreference(CarLauncherSettings.KEY_NIGHT_DIM_MODE);
        if (nightDimModePref != null) {
            nightDimModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setNightDimMode(val);
                }
                if (getActivity() instanceof app.organicmaps.MwmActivity) {
                    ((app.organicmaps.MwmActivity) getActivity()).applyNightDimMode();
                } else if (getContext() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.NIGHT_DIM_CHANGED");
                    getContext().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Premium Parallax Intensity
        SeekBarPreference parallaxIntensityPref = findPreference(CarLauncherSettings.KEY_PARALLAX_INTENSITY);
        if (parallaxIntensityPref != null) {
            parallaxIntensityPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int val = (Integer) newValue;
                if (settings != null) {
                    settings.setParallaxIntensity(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Premium Background Style
        androidx.preference.ListPreference backgroundStylePref = findPreference(CarLauncherSettings.KEY_BACKGROUND_STYLE);
        if (backgroundStylePref != null) {
            backgroundStylePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setBackgroundStyle(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Yuzen harita (PiP) ayari (Turkce karakter yok)
        SwitchPreferenceCompat pipPref = findPreference(CarLauncherSettings.KEY_PIP_MODE);
        if (pipPref != null) {
            pipPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean val = (Boolean) newValue;
                if (settings != null) {
                    settings.setPipModeEnabled(val);
                }
                return true;
            });
        }

        // Widget Paneli Konumu (Turkce karakter yok)
        androidx.preference.ListPreference panelPositionPref = findPreference(CarLauncherSettings.KEY_WIDGET_PANEL_POSITION);
        if (panelPositionPref != null) {
            panelPositionPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setWidgetPanelPosition(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Panel Genisleme Davranisi (Turkce karakter yok)
        androidx.preference.ListPreference expansionBehaviorPref = findPreference("car_launcher_expansion_behavior");
        if (expansionBehaviorPref != null) {
            expansionBehaviorPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setExpansionBehavior(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent("net.osmand.carlauncher.WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }
    }

    private void applyStatusBarVisibility(boolean show) {
        if (getActivity() instanceof app.organicmaps.MwmActivity) {
            ((app.organicmaps.MwmActivity) getActivity()).applyStatusBarVisibility();
        }
    }

    private void setupLanguagePrefs() {
        androidx.preference.ListPreference langPref = findPreference("car_launcher_language");
        if (langPref != null) {
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            
            // Secenekleri ayarla
            langPref.setEntries(new CharSequence[]{"Sistem (System)", "TÃƒÂ¼rkÃƒÂ§e", "English", "Deutsch"});
            langPref.setEntryValues(new CharSequence[]{"", "tr", "en", "de"});
            
            // Mevcut degeri set et
            String current = prefs.getString("pref_app_locale", "");
            langPref.setValue(current);
            
            langPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                prefs.edit().putString("pref_app_locale", val).apply();
                
                // Dil guncelle ve yeniden baslat
                Toast.makeText(getContext(), "Dil gÃƒÂ¼ncelleniyor...", Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    Intent intent = getActivity().getIntent();
                    getActivity().finish();
                    startActivity(intent);
                }
                return true;
            });
        }
    }

    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // MÃƒÅ“ZÃ„Â°K AYARLARI
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private void setupMusicPrefs() {
        // Music App Picker
        Preference musicAppPref = findPreference(CarLauncherSettings.KEY_MUSIC_APP);
        if (musicAppPref != null) {
            updateMusicAppSummary(musicAppPref);
            musicAppPref.setOnPreferenceClickListener(preference -> {
                showMusicAppPicker();
                return true;
            });
        }

        // Equalizer
        Preference eqPref = findPreference("car_launcher_equalizer");
        if (eqPref != null) {
            eqPref.setOnPreferenceClickListener(preference -> {
                openEqualizer();
                return true;
            });
        }

        // Muzikleri Yeniden Tara (Turkce karakter yok)
        Preference scanMusicPref = findPreference("car_launcher_scan_music");
        if (scanMusicPref != null) {
            scanMusicPref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Muzik taramasi baslatildi...", Toast.LENGTH_SHORT).show();
                    MusicManager.getInstance(getContext()).getRepository().scanMusic((tracks, folders, artists) -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Tarama tamamlandi. Bulunan sarki: " + tracks.size(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }
                return true;
            });
        }

        // Ambians gorsellestirici ayari (Turkce karakter yok)
        SwitchPreferenceCompat ambianceVisualizerPref = findPreference(CarLauncherSettings.KEY_AMBIANCE_VISUALIZER);
        if (ambianceVisualizerPref != null) {
            ambianceVisualizerPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean val = (Boolean) newValue;
                if (settings != null) {
                    settings.setAmbianceVisualizerEnabled(val);
                }
                return true;
            });
        }
    }

    private void updateMusicAppSummary(Preference pref) {
        if (settings == null || getContext() == null)
            return;

        String pkg = settings.getMusicApp();
        if ("internal".equals(pkg)) {
            pref.setSummary("Dahili Player");
        } else {
            try {
                PackageManager pm = getContext().getPackageManager();
                String appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                pref.setSummary(appName);
            } catch (PackageManager.NameNotFoundException e) {
                pref.setSummary(pkg);
            }
        }
    }

    private void showMusicAppPicker() {
        if (getContext() == null) return;
        new AppPickerDialog(getContext(), true, (packageName, name, icon) -> {
            settings.setMusicApp(packageName);
            MusicManager musicManager = MusicManager.getInstance(getContext());
            musicManager.setPreferredPackage("internal".equals(packageName) ? null : packageName);
            
            Preference pref = findPreference(CarLauncherSettings.KEY_MUSIC_APP);
            if (pref != null) updateMusicAppSummary(pref);
        }).show();
    }

    private void openEqualizer() {
        try {
            Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Equalizer bulunamadÃ„Â±", Toast.LENGTH_SHORT).show();
        }
    }

    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // DOCK AYARLARI
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private void setupDockPrefs() {
        androidx.preference.ListPreference dockPosPref = findPreference(CarLauncherSettings.KEY_DOCK_POSITION);
        if (dockPosPref != null) {
            dockPosPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setDockPosition(val);
                }
                if (getActivity() instanceof app.organicmaps.MwmActivity) {
                    ((app.organicmaps.MwmActivity) getActivity()).checkAndRefreshDockFragmentIfNeeded();
                }
                return true;
            });
        }

        SeekBarPreference maxShortcutsPref = findPreference(CarLauncherSettings.KEY_MAX_SHORTCUTS);
        if (maxShortcutsPref != null) {
            maxShortcutsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int max = (Integer) newValue;
                if (settings != null) {
                    settings.setMaxShortcuts(max);
                }
                return true;
            });
        }

        Preference resetPref = findPreference("car_launcher_reset_dock");
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(preference -> {
                confirmResetDock();
                return true;
            });
        }
    }

    private void confirmResetDock() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Dock'u SÃ„Â±fÃ„Â±rla")
                .setMessage("TÃƒÂ¼m uygulama kÃ„Â±sayollarÃ„Â± silinecek. Emin misiniz?")
                .setPositiveButton("SÃ„Â±fÃ„Â±rla", (dialog, which) -> resetDock())
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void resetDock() {
        if (getContext() == null) return;
        AppDockManager dockManager = new AppDockManager(getContext());
        dockManager.clearAllShortcuts();
        Intent intent = new Intent("net.osmand.carlauncher.DOCK_UPDATED");
        getContext().sendBroadcast(intent);
        Toast.makeText(getContext(), "Dock sÃ„Â±fÃ„Â±rlandÃ„Â±", Toast.LENGTH_SHORT).show();
    }

    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // OTOMATÃ„Â°K BAÃ…ÂLATMA
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private void setupAutoLaunchPrefs() {
        bindAutoLaunchSlot(1);
        bindAutoLaunchSlot(2);
        bindAutoLaunchSlot(3);
    }

    private void bindAutoLaunchSlot(int slot) {
        String key = CarLauncherSettings.KEY_AUTOLAUNCH_ENABLE_PREFIX + slot;
        SwitchPreferenceCompat pref = findPreference(key);
        if (pref != null) {
            String appName = settings.getAutoLaunchAppName(slot);
            if (settings.getAutoLaunchPackage(slot) != null) {
                pref.setSummary(appName);
            } else {
                pref.setSummary("Seçmek için metne tıklayın");
            }

            pref.setOnPreferenceClickListener(preference -> {
                if (getContext() == null) return true;
                
                new AppPickerDialog(getContext(), false, (packageName, name, icon) -> {
                    settings.setAutoLaunchApp(slot, packageName, name);
                    pref.setSummary(name);
                    // Force Enable
                    settings.setAutoLaunchEnabled(slot, true);
                    pref.setChecked(true);
                }).show();
                
                return true; // Consume click event -> Handled by dialog
            });
        }
    }

    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // YEDEKLEME
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private static final int RC_BACKUP_EXPORT = 101;
    private static final int RC_BACKUP_IMPORT = 102;

    private void setupBackupPrefs() {
        Preference exportPref = findPreference("action_backup_export");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, "CarLauncher_Backup_" + System.currentTimeMillis() + ".json");
                    startActivityForResult(intent, RC_BACKUP_EXPORT);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Dosya oluÃ…Å¸turulamadÃ„Â±", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        Preference importPref = findPreference("action_backup_import");
        if (importPref != null) {
            importPref.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json"); 
                    startActivityForResult(intent, RC_BACKUP_IMPORT);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Dosya seÃƒÂ§ici aÃƒÂ§Ã„Â±lamadÃ„Â±", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    private static final int RC_IMPORT_VOICE_MODEL = 103;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == RC_BACKUP_EXPORT) {
                // LauncherBackupManager.exportBackup(getContext(), uri);
            } else if (requestCode == RC_BACKUP_IMPORT) {
                // LauncherBackupManager.restoreBackup(getContext(), uri);
                getPreferenceScreen().removeAll();
                onCreatePreferences(null, getPreferenceScreen().getKey());
                Toast.makeText(getContext(), "Ayarlar yenilendi", Toast.LENGTH_SHORT).show();
            } else if (requestCode == RC_IMPORT_VOICE_MODEL) {
                importVoiceModelFromUri(uri);
            }
        }
    }

    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â
    // HAKKINDA
    // Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â

    private void setupAboutPrefs() {
        Preference versionPref = findPreference("car_launcher_version");
        if (versionPref != null) {
            try {
                String version = getContext().getPackageManager()
                        .getPackageInfo(getContext().getPackageName(), 0).versionName;
                versionPref.setSummary("v" + version);
            } catch (Exception e) {
                versionPref.setSummary("1.0.0");
            }
        }

        Preference githubPref = findPreference("car_launcher_github");
        if (githubPref != null) {
            githubPref.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/poolsoft/OsmAnd/tree/right-panel-plugin"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "TarayÃ„Â±cÃ„Â± aÃƒÂ§Ã„Â±lamadÃ„Â±", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        Preference checkUpdatePref = findPreference("car_launcher_check_updates");
        if (checkUpdatePref != null) {
            checkUpdatePref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    UpdaterHelper.checkUpdates(getContext(), true);
                }
                return true;
            });
        }
    }

    private void setupAssistantPrefs() {
        Preference importModelPref = findPreference("car_launcher_import_voice_model");
        if (importModelPref != null) {
            importModelPref.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    startActivityForResult(intent, RC_IMPORT_VOICE_MODEL);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Dosya seÃƒÂ§ici aÃƒÂ§Ã„Â±lamadÃ„Â±", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    private void importVoiceModelFromUri(Uri uri) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), "Model dosyasÃ„Â± kopyalanÃ„Â±yor, lÃƒÂ¼tfen bekleyin...", Toast.LENGTH_SHORT).show();
        
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            File targetDir = new File(getContext().getExternalFilesDir(null), "vosk-model-tr");
            File tempZip = new File(getContext().getExternalFilesDir(null), "vosk-model-tr-temp.zip");
            File tempExtractDir = new File(getContext().getExternalFilesDir(null), "vosk-model-temp-extract");
            
            try (android.os.ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
                 java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor());
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(tempZip)) {
                
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                
                if (tempExtractDir.exists()) {
                    deleteRecursive(tempExtractDir);
                }
                tempExtractDir.mkdirs();
                
                unzip(tempZip, tempExtractDir);
                
                File actualModelDir = findModelDirRecursive(tempExtractDir);
                if (actualModelDir != null && actualModelDir.exists()) {
                    if (targetDir.exists()) {
                        deleteRecursive(targetDir);
                    }
                    boolean success = actualModelDir.renameTo(targetDir);
                    android.util.Log.d("CarLauncherSettings", "Model klasoru basariyla tasindi: " + success);
                } else {
                    throw new java.io.IOException("Zip icerisinde gecerli bir model klasoru bulunamadi");
                }
                
                if (tempZip.exists()) {
                    tempZip.delete();
                }
                if (tempExtractDir.exists()) {
                    deleteRecursive(tempExtractDir);
                }
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ses modeli baÃ…Å¸arÃ„Â±yla kuruldu! Servis yeniden baÃ…Å¸latÃ„Â±lÃ„Â±yor...", Toast.LENGTH_LONG).show();
                        restartVoiceService();
                    });
                }
                
            } catch (Exception e) {
                android.util.Log.e("CarLauncherSettings", "Model kopyalama/unzip hatasÃ„Â±", e);
                if (tempZip.exists()) tempZip.delete();
                if (tempExtractDir.exists()) deleteRecursive(tempExtractDir);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Ses modeli yÃƒÂ¼klenemedi!", Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    private File findModelDirRecursive(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }
        File amDir = new File(dir, "am");
        File graphDir = new File(dir, "graph");
        if ((amDir.exists() && amDir.isDirectory()) || (graphDir.exists() && graphDir.isDirectory())) {
            return dir;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    File found = findModelDirRecursive(child);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private void restartVoiceService() {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), app.organicmaps.carlauncher.voice.VoiceCommandService.class);
        if (app.organicmaps.carlauncher.voice.VoiceCommandService.isServiceRunning) {
            getContext().stopService(intent);
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getContext().startForegroundService(intent);
        } else {
            getContext().startService(intent);
        }
    }

    private void unzip(File zipFile, File targetDirectory) throws java.io.IOException {
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
            new java.io.BufferedInputStream(new java.io.FileInputStream(zipFile)));
        try {
            java.util.zip.ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];
            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new java.io.IOException("Klasor olusturulamadi: " + dir.getAbsolutePath());
                }
                if (ze.isDirectory()) {
                    continue;
                }
                java.io.FileOutputStream fout = new java.io.FileOutputStream(file);
                try {
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }
                } finally {
                    fout.close();
                }
            }
        } finally {
            zis.close();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}

