package app.organicmaps.carlauncher.ui;

import app.organicmaps.R;

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
    private static final int REQUEST_MEDIA_PERMISSION = 300;
    private static final int REQUEST_ALL_RUNTIME_PERMISSIONS = 301;

    private CarLauncherSettings settings;
    private boolean permissionGuideActive;
    private boolean runtimePermissionRequestInFlight;
    private boolean overlayStepVisited;
    private boolean notificationStepVisited;
    private boolean backgroundLocationStepVisited;


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
        setupPermissionsPrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Kullanici ayarlardan donunce izin durumlarini guncelle
        updatePermissionSummaries();
        if (permissionGuideActive && !runtimePermissionRequestInFlight) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::openNextSpecialPermission, 250);
        }
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
                
                // SaÃƒâ€Ã…Â¸ panelde (Detay) bu Kategori baÃƒâ€¦Ã…Â¸lÃƒâ€Ã‚Â±Ãƒâ€Ã…Â¸Ãƒâ€Ã‚Â±nÃƒâ€Ã‚Â±n gereksiz yer/padding kaplamamasÃƒâ€Ã‚Â± iÃƒÆ’Ã‚Â§in siliyoruz.
                // Sol menÃƒÆ’Ã‚Â¼ye (Master) baÃƒâ€¦Ã…Â¸lÃƒâ€Ã‚Â±k ve ikon kopyalandÃƒâ€Ã‚Â±Ãƒâ€Ã…Â¸Ãƒâ€Ã‚Â± iÃƒÆ’Ã‚Â§in orasÃƒâ€Ã‚Â± etkilenmez.
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



    // ÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚ÂÃƒÂ¢Ã¢â‚¬Â¢Ã‚Â
    // GÃƒÆ’Ã¢â‚¬â€œRÃƒÆ’Ã…â€œNÃƒÆ’Ã…â€œM AYARLARI
    // 
    // GÖRÜNÜM AYARLARI
    // 

    private void setupAppearancePrefs() {
        // Status Bar
        SwitchPreferenceCompat statusBarPref = findPreference(CarLauncherSettings.KEY_STATUS_BAR);
        if (statusBarPref != null) {
            statusBarPref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean show = (Boolean) newValue;
                if (getContext() != null) {
                    new CarLauncherSettings(getContext()).setStatusBarVisible(show);
                }
                applyStatusBarVisibility(show);
                return true;
            });
        }

        // Dark Theme
        SwitchPreferenceCompat themePref = findPreference(CarLauncherSettings.KEY_DARK_THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                Toast.makeText(getContext(), getString(R.string.car_settings_theme_restart),
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // Screen Orientation
        androidx.preference.ListPreference orientationPref = findPreference(CarLauncherSettings.KEY_SCREEN_ORIENTATION);
        if (orientationPref != null) {
            orientationPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setScreenOrientation(val);
                }
                applyScreenOrientation(val);
                return true;
            });
        }

        // Widget Display Mode
        androidx.preference.ListPreference displayModePref = findPreference(CarLauncherSettings.KEY_WIDGET_DISPLAY_MODE);
        if (displayModePref != null) {
            displayModePref.setEntries(new CharSequence[]{
                    getString(R.string.car_settings_list_default),
                    getString(R.string.car_settings_carousel)
            });
            displayModePref.setEntryValues(new CharSequence[]{"0", "1"});
            displayModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                CarLauncherSettings settings = new CarLauncherSettings(getContext());
                try {
                    settings.setWidgetDisplayMode(Integer.parseInt((String) newValue));
                } catch (NumberFormatException e) {
                    settings.setWidgetDisplayMode(0);
                }

                Toast.makeText(getContext(), getString(R.string.car_settings_widget_mode_restart),
                        Toast.LENGTH_SHORT).show();
                
                 if (getActivity() != null) {
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
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
                    Toast.makeText(getContext(), getString(R.string.car_settings_widget_manager_edit), Toast.LENGTH_SHORT).show();
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
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
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
                if (val && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (getContext() != null && !android.provider.Settings.canDrawOverlays(getContext())) {
                        android.widget.Toast.makeText(getContext(), getString(R.string.car_settings_draw_overlays_permission), android.widget.Toast.LENGTH_LONG).show();
                        android.content.Intent intent = new android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getContext().getPackageName()));
                        startActivity(intent);
                        return false; 
                    }
                }
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
                    CarFloatingButtonManager mgr = CarFloatingButtonManager.getInstance(getContext());
                    mgr.hideButton();
                    mgr.updateButtonState();
                }
                return true;
            });
        }

        // Gece karartma modu
        androidx.preference.ListPreference nightDimModePref = findPreference(CarLauncherSettings.KEY_NIGHT_DIM_MODE);
        if (nightDimModePref != null) {
            nightDimModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setNightDimMode(val);
                }
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                            .applyNightDimMode();
                } else if (getContext() != null) {
                    Intent intent = new Intent(getContext().getPackageName() + ".NIGHT_DIM_CHANGED");
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
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
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
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Yuzen harita (PiP) ayari
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

        // Widget Paneli Konumu
        androidx.preference.ListPreference panelPositionPref = findPreference(CarLauncherSettings.KEY_WIDGET_PANEL_POSITION);
        if (panelPositionPref != null) {
            panelPositionPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setWidgetPanelPosition(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }

        // Panel Genisleme Davranisi
        androidx.preference.ListPreference expansionBehaviorPref = findPreference("car_launcher_expansion_behavior");
        if (expansionBehaviorPref != null) {
            expansionBehaviorPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setExpansionBehavior(val);
                }
                if (getActivity() != null) {
                    Intent intent = new Intent(getActivity().getPackageName() + ".WIDGET_MODE_CHANGED");
                    getActivity().sendBroadcast(intent);
                }
                return true;
            });
        }
    }

    private void applyStatusBarVisibility(boolean show) {
        if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherActivity) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                ((app.organicmaps.carlauncher.CarLauncherActivity) getActivity()).applyStatusBarVisibility();
            });
        } else if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
            ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                    .applyStatusBarVisibility();
        }
    }

    private void setupLanguagePrefs() {
        androidx.preference.ListPreference langPref = findPreference("car_launcher_language");
        if (langPref != null) {
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            
            langPref.setEntries(new CharSequence[]{
                getContext().getString(R.string.car_settings_lang_system), 
                getContext().getString(R.string.car_settings_lang_turkish), 
                getContext().getString(R.string.car_settings_lang_english), 
                getContext().getString(R.string.car_settings_lang_german)
            });
            langPref.setEntryValues(new CharSequence[]{"", "tr", "en", "de"});
            
            String current = prefs.getString("pref_app_locale", "");
            langPref.setValue(current);
            
            langPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                prefs.edit().putString("pref_app_locale", val).apply();
                
                Toast.makeText(getContext(), getContext().getString(R.string.car_settings_language_updating), Toast.LENGTH_SHORT).show();
                if (getActivity() != null) {
                    Intent intent = getActivity().getIntent();
                    getActivity().finish();
                    startActivity(intent);
                }
                return true;
            });
        }
    }

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

        // Muzikleri Yeniden Tara
        Preference scanMusicPref = findPreference("car_launcher_scan_music");
        if (scanMusicPref != null) {
            scanMusicPref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), getString(R.string.car_settings_music_scanning), Toast.LENGTH_SHORT).show();
                    MusicManager.getInstance(getContext()).getRepository().scanMusic((tracks, folders, artists) -> {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), getString(R.string.car_settings_music_scan_results, tracks.size()), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }
                return true;
            });
        }

        // Ambians gorsellestirici ayari
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
            pref.setSummary(getString(R.string.car_settings_music_app_internal));
        } else {
            try {
                PackageManager pm = getContext().getPackageManager();
                String appName = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                pref.setSummary(appName);
            } catch (Exception e) {
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
            Toast.makeText(getContext(), getString(R.string.car_settings_equalizer_not_found), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupDockPrefs() {
        androidx.preference.ListPreference dockPosPref = findPreference(CarLauncherSettings.KEY_DOCK_POSITION);
        if (dockPosPref != null) {
            dockPosPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String val = (String) newValue;
                if (settings != null) {
                    settings.setDockPosition(val);
                }
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                            .checkAndRefreshDockFragmentIfNeeded();
                }
                return true;
            });
        }

        SeekBarPreference dockSizePref =
                findPreference(CarLauncherSettings.KEY_DOCK_SIZE);
        if (dockSizePref != null) {
            dockSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (settings != null) {
                    settings.setDockSize((Integer) newValue);
                }
                refreshLauncherLayout();
                return true;
            });
        }

        SeekBarPreference portraitDockSizePref =
                findPreference(CarLauncherSettings.KEY_DOCK_SIZE_PORTRAIT);
        if (portraitDockSizePref != null) {
            portraitDockSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (settings != null) {
                    settings.setDockSizePortrait((Integer) newValue);
                }
                refreshLauncherLayout();
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

    private void refreshLauncherLayout() {
        if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
            ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                    .checkAndRefreshDockFragmentIfNeeded();
        }
    }

    private void confirmResetDock() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.car_settings_reset_dock_title)
                .setMessage(R.string.car_settings_reset_dock_msg)
                .setPositiveButton(R.string.car_settings_reset_dock_confirm, (dialog, which) -> resetDock())
                .setNegativeButton(R.string.car_music_cancel, null)
                .show();
    }

    private void resetDock() {
        if (getContext() == null) return;
        AppDockManager dockManager = new AppDockManager(getContext());
        dockManager.clearAllShortcuts();
        Intent intent = new Intent(getContext().getPackageName() + ".DOCK_UPDATED");
        getContext().sendBroadcast(intent);
        Toast.makeText(getContext(), getString(R.string.car_settings_dock_reset_success), Toast.LENGTH_SHORT).show();
    }

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
                pref.setSummary(R.string.car_settings_autolaunch_select_hint);
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

    private static final int RC_BACKUP_EXPORT_FOLDER = 101;
    private static final int RC_BACKUP_EXPORT_ZIP = 102;
    private static final int RC_BACKUP_IMPORT_FOLDER = 104;
    private static final int RC_BACKUP_IMPORT_ZIP = 105;

    private void setupBackupPrefs() {
        Preference downloadMapsPref = findPreference("action_download_maps");
        if (downloadMapsPref != null) {
            downloadMapsPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getContext(), app.organicmaps.downloader.DownloaderActivity.class);
                startActivity(intent);
                return true;
            });
        }

        Preference exportPref = findPreference("action_backup_export");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                new android.app.AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.car_backup_type_title))
                    .setItems(new CharSequence[]{getString(R.string.car_backup_type_folder), getString(R.string.car_backup_type_zip)}, (dialog, which) -> {
                        try {
                            Intent intent;
                            if (which == 0) {
                                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                startActivityForResult(intent, RC_BACKUP_EXPORT_FOLDER);
                            } else {
                                intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("application/zip");
                                intent.putExtra(Intent.EXTRA_TITLE, "CoMaps_Backup_" + System.currentTimeMillis() + ".zip");
                                startActivityForResult(intent, RC_BACKUP_EXPORT_ZIP);
                            }
                        } catch (Exception e) {
                            Toast.makeText(getContext(), getString(R.string.car_settings_file_picker_error), Toast.LENGTH_SHORT).show();
                        }
                    }).show();
                return true;
            });
        }

        Preference importPref = findPreference("action_backup_import");
        if (importPref != null) {
            importPref.setOnPreferenceClickListener(preference -> {
                new android.app.AlertDialog.Builder(getContext())
                    .setTitle(R.string.car_settings_restore_type)
                    .setItems(new CharSequence[]{
                            getString(R.string.car_settings_restore_from_folder),
                            getString(R.string.car_settings_restore_from_zip)
                    }, (dialog, which) -> {
                        try {
                            Intent intent;
                            if (which == 0) {
                                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                startActivityForResult(intent, RC_BACKUP_IMPORT_FOLDER);
                            } else {
                                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("application/zip"); 
                                startActivityForResult(intent, RC_BACKUP_IMPORT_ZIP);
                            }
                        } catch (Exception e) {
                            Toast.makeText(getContext(), getString(R.string.car_settings_file_picker_error), Toast.LENGTH_SHORT).show();
                        }
                    }).show();
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
            if (requestCode == RC_BACKUP_EXPORT_FOLDER) {
                showBackupProgress();
                app.organicmaps.carlauncher.backup.LauncherBackupManager.exportToFolder(getContext(), uri, createBackupCallback());
            } else if (requestCode == RC_BACKUP_EXPORT_ZIP) {
                showBackupProgress();
                app.organicmaps.carlauncher.backup.LauncherBackupManager.exportToZip(getContext(), uri, createBackupCallback());
            } else if (requestCode == RC_BACKUP_IMPORT_FOLDER) {
                showBackupProgress();
                app.organicmaps.carlauncher.backup.LauncherBackupManager.importFromFolder(getContext(), uri, createBackupCallback());
            } else if (requestCode == RC_BACKUP_IMPORT_ZIP) {
                showBackupProgress();
                app.organicmaps.carlauncher.backup.LauncherBackupManager.importFromZip(getContext(), uri, createBackupCallback());
            } else if (requestCode == RC_IMPORT_VOICE_MODEL) {
                importVoiceModelFromUri(uri);
            }
        }
    }

    private android.app.ProgressDialog mBackupDialog;

    private app.organicmaps.carlauncher.backup.LauncherBackupManager.BackupCallback createBackupCallback() {
        return new app.organicmaps.carlauncher.backup.LauncherBackupManager.BackupCallback() {
            @Override
            public void onProgress(String message) {
                try {
                    if (mBackupDialog != null && mBackupDialog.isShowing()) {
                        mBackupDialog.setMessage(message);
                    }
                } catch (Exception ignored) {}
            }

            @Override
            public void onSuccess() {
                try {
                    if (mBackupDialog != null && mBackupDialog.isShowing()) {
                        mBackupDialog.dismiss();
                    }
                } catch (Exception ignored) {}
                try {
                    Toast.makeText(getContext(), getString(R.string.car_settings_backup_success), Toast.LENGTH_LONG).show();
                    getPreferenceScreen().removeAll();
                    onCreatePreferences(null, getPreferenceScreen().getKey());
                } catch (Exception ignored) {}
            }

            @Override
            public void onError(String error) {
                try {
                    if (mBackupDialog != null && mBackupDialog.isShowing()) {
                        mBackupDialog.dismiss();
                    }
                } catch (Exception ignored) {}
                try {
                    Toast.makeText(getContext(), getString(R.string.car_settings_error_generic, error), Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        };
    }

    private void showBackupProgress() {
        mBackupDialog = new android.app.ProgressDialog(getContext());
        mBackupDialog.setTitle(getString(R.string.car_settings_please_wait));
        mBackupDialog.setMessage(getString(R.string.car_settings_backup_starting));
        mBackupDialog.setCancelable(false);
        mBackupDialog.show();
    }

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
                    Toast.makeText(getContext(), getString(R.string.car_settings_browser_error), Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), getString(R.string.car_settings_file_picker_error), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }

    private void importVoiceModelFromUri(Uri uri) {
        if (getContext() == null) return;
        Toast.makeText(getContext(), getString(R.string.car_settings_assistant_copying_model), Toast.LENGTH_SHORT).show();
        
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
                        Toast.makeText(getContext(), getString(R.string.car_settings_voice_model_success), Toast.LENGTH_LONG).show();
                        restartVoiceService();
                    });
                }
                
            } catch (Exception e) {
                android.util.Log.e("CarLauncherSettings", "Model kopyalama/unzip hatasi", e);
                if (tempZip.exists()) tempZip.delete();
                if (tempExtractDir.exists()) deleteRecursive(tempExtractDir);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), getString(R.string.car_settings_voice_model_error), Toast.LENGTH_LONG).show();
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

    // ═══════════════════════════════════════════════════════════════
    // İZİN YÖNETİMİ
    // ═══════════════════════════════════════════════════════════════

    private void setupPermissionsPrefs() {
        Preference grantAllPref = findPreference("perm_grant_all");
        if (grantAllPref != null) {
            grantAllPref.setOnPreferenceClickListener(preference -> {
                startPermissionGuide();
                return true;
            });
        }

        // Ekranin Uzerinde Ciz izni
        Preference overlayPref = findPreference("perm_overlay");
        if (overlayPref != null) {
            overlayPref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getContext().getPackageName()));
                    startActivity(intent);
                }
                return true;
            });
        }

        // Arka Plan Konumu (Her Zaman) izni
        Preference bgLocationPref = findPreference("perm_bg_location");
        if (bgLocationPref != null) {
            bgLocationPref.setOnPreferenceClickListener(preference -> {
                if (getContext() != null) {
                    android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
                return true;
            });
        }

        // Bildirim Erisimi izni
        Preference notificationPref = findPreference("perm_notification");
        if (notificationPref != null) {
            notificationPref.setOnPreferenceClickListener(preference -> {
                android.content.Intent intent = new android.content.Intent(
                        android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
                return true;
            });
        }

        // Depolama / Medya izni
        Preference mediaPref = findPreference("perm_media");
        if (mediaPref != null) {
            mediaPref.setOnPreferenceClickListener(preference -> {
                if (getContext() == null) return true;
                List<String> perms = new ArrayList<>();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (getContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_MEDIA_AUDIO);
                    }
                } else {
                    if (getContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    }
                }
                if (!perms.isEmpty()) {
                    requestPermissions(perms.toArray(new String[0]), REQUEST_MEDIA_PERMISSION);
                } else {
                    Toast.makeText(getContext(), getString(R.string.car_perm_media_summary_ok), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        Preference microphonePref = findPreference("perm_microphone");
        if (microphonePref != null) {
            microphonePref.setOnPreferenceClickListener(preference -> {
                if (getContext() == null) return true;
                if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},
                            REQUEST_ALL_RUNTIME_PERMISSIONS);
                } else {
                    Toast.makeText(getContext(),
                            getString(R.string.car_perm_microphone_summary_ok),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        updatePermissionSummaries();
    }

    private void updatePermissionSummaries() {
        if (getContext() == null) return;

        // Overlay izni durumu
        Preference overlayPref = findPreference("perm_overlay");
        if (overlayPref != null) {
            boolean hasOverlay = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
                    || android.provider.Settings.canDrawOverlays(getContext());
            overlayPref.setSummary(hasOverlay
                    ? getString(R.string.car_perm_overlay_summary_ok)
                    : getString(R.string.car_perm_overlay_summary_missing));
        }

        // Arka plan konumu durumu
        Preference bgLocationPref = findPreference("perm_bg_location");
        if (bgLocationPref != null) {
            boolean hasBgLocation = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q
                    || androidx.core.content.ContextCompat.checkSelfPermission(getContext(),
                            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
            bgLocationPref.setSummary(hasBgLocation
                    ? getString(R.string.car_perm_bg_location_summary_ok)
                    : getString(R.string.car_perm_bg_location_summary_missing));
        }

        // Bildirim erisimi durumu
        Preference notificationPref = findPreference("perm_notification");
        if (notificationPref != null) {
            String listeners = android.provider.Settings.Secure.getString(
                    getContext().getContentResolver(), "enabled_notification_listeners");
            boolean hasNotification = listeners != null && listeners.contains(getContext().getPackageName());
            notificationPref.setSummary(hasNotification
                    ? getString(R.string.car_perm_notification_summary_ok)
                    : getString(R.string.car_perm_notification_summary_missing));
        }

        // Medya / Depolama izni durumu
        Preference mediaPref = findPreference("perm_media");
        if (mediaPref != null) {
            boolean hasMedia;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                hasMedia = getContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
            } else {
                hasMedia = getContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
            }
            mediaPref.setSummary(hasMedia
                    ? getString(R.string.car_perm_media_summary_ok)
                    : getString(R.string.car_perm_media_summary_missing));
        }

        Preference microphonePref = findPreference("perm_microphone");
        if (microphonePref != null) {
            boolean hasMicrophone = androidx.core.content.ContextCompat.checkSelfPermission(
                    getContext(), android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
            microphonePref.setSummary(hasMicrophone
                    ? getString(R.string.car_perm_microphone_summary_ok)
                    : getString(R.string.car_perm_microphone_summary_missing));
        }
    }

    private void startPermissionGuide() {
        if (getContext() == null) return;
        permissionGuideActive = true;
        overlayStepVisited = false;
        notificationStepVisited = false;
        backgroundLocationStepVisited = false;

        List<String> permissions = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(permissions, android.Manifest.permission.READ_MEDIA_AUDIO);
            addMissingPermission(permissions, android.Manifest.permission.POST_NOTIFICATIONS);
        } else {
            addMissingPermission(permissions, android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        addMissingPermission(permissions, android.Manifest.permission.RECORD_AUDIO);
        addMissingPermission(permissions, android.Manifest.permission.ACCESS_FINE_LOCATION);
        addMissingPermission(permissions, android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (!permissions.isEmpty()) {
            runtimePermissionRequestInFlight = true;
            requestPermissions(permissions.toArray(new String[0]),
                    REQUEST_ALL_RUNTIME_PERMISSIONS);
        } else {
            openNextSpecialPermission();
        }
    }

    private void addMissingPermission(List<String> permissions, String permission) {
        if (getContext() != null
                && androidx.core.content.ContextCompat.checkSelfPermission(getContext(), permission)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void openNextSpecialPermission() {
        if (!permissionGuideActive || getContext() == null || !isAdded()) return;
        updatePermissionSummaries();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && !android.provider.Settings.canDrawOverlays(getContext())
                && !overlayStepVisited) {
            overlayStepVisited = true;
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getContext().getPackageName())));
            return;
        }

        String listeners = android.provider.Settings.Secure.getString(
                getContext().getContentResolver(), "enabled_notification_listeners");
        boolean hasNotificationListener = listeners != null
                && listeners.contains(getContext().getPackageName());
        if (!hasNotificationListener && !notificationStepVisited) {
            notificationStepVisited = true;
            startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return;
        }

        boolean hasBackgroundLocation = android.os.Build.VERSION.SDK_INT
                < android.os.Build.VERSION_CODES.Q
                || androidx.core.content.ContextCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasBackgroundLocation && !backgroundLocationStepVisited) {
            backgroundLocationStepVisited = true;
            android.content.Intent intent = new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getContext().getPackageName()));
            startActivity(intent);
            return;
        }

        permissionGuideActive = false;
        Toast.makeText(getContext(), getString(R.string.car_perm_all_runtime_done),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionSummaries();
        if (requestCode == REQUEST_ALL_RUNTIME_PERMISSIONS && permissionGuideActive) {
            runtimePermissionRequestInFlight = false;
            Toast.makeText(getContext(), getString(R.string.car_perm_opening_special),
                    Toast.LENGTH_SHORT).show();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(this::openNextSpecialPermission);
        }
    }

    private void applyScreenOrientation(String mode) {
        if (getActivity() == null) return;
        int orientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if ("portrait".equals(mode)) {
            orientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if ("sensor".equals(mode)) {
            orientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        }
        getActivity().setRequestedOrientation(orientation);
    }

    @Override
    public void onDestroy() {
        if (mBackupDialog != null && mBackupDialog.isShowing()) {
            try {
                mBackupDialog.dismiss();
            } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
