package app.organicmaps.carlauncher.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.carlauncher.dock.AppDockAdapter;
import app.organicmaps.carlauncher.dock.AppDockManager;
import app.organicmaps.carlauncher.dock.AppPickerDialog;
import app.organicmaps.carlauncher.CarLauncherInterface;
import app.organicmaps.carlauncher.dock.AppShortcut;
import app.organicmaps.carlauncher.dock.LaunchMode;
import app.organicmaps.carlauncher.overlay.OverlayWindowManager;
import app.organicmaps.carlauncher.music.MusicManager;

/**
 * App Dock fragment.
 * <<<<<<< HEAD
 * Uygulama kisayollarini ve Mini Player'i gosterir.
 * =======
 * >>>>>>> 32c2ee2e47809bba5011d01849fb227eaed926a9
 */
public class AppDockFragment extends Fragment
        implements AppDockAdapter.OnShortcutListener, MusicManager.MusicUIListener {

    public static final String TAG = "AppDockFragment";
    // Sync Fix: Ensure remote matches local
    private static final String PREFS_NAME = "app_dock_settings";
    private static final String KEY_ORIENTATION = "orientation";
    private static final int ORIENTATION_HORIZONTAL = LinearLayoutManager.HORIZONTAL;
    private static final int ORIENTATION_VERTICAL = LinearLayoutManager.VERTICAL;

    private RecyclerView recyclerView;
    private ImageButton addButton;
    private ImageButton orientationButton;
    private AppDockAdapter adapter;
    private AppDockManager dockManager;
    private OverlayWindowManager overlayManager;
    private SharedPreferences prefs;
    private boolean isEditMode = false;
    private int currentOrientation = ORIENTATION_HORIZONTAL; // Varsayilan yatay
    private boolean isVerticalMode = false;
    private int currentLayoutId = 0;

    private OnAppDockListener listener;
    private ImageButton menuButton;
    private ImageButton layoutButton;
    private ImageButton appListButton;
    private ImageButton btnDesktopMode;

    // New Views
    private TextView clockView;
    private android.os.Handler clockHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable clockRunnable;

    // Mini Music Player
    private LinearLayout miniMusicContainer;
    private TextView miniMusicTitle;
    private ImageButton miniBtnPlay;
    private ImageButton miniBtnNext;
    private ImageView miniMusicIcon;
    private MusicManager musicManager;

    // New Containers & Assistant Button
    private LinearLayout leftContainer;
    private LinearLayout rightContainer;
    private LinearLayout centerContainer;
    private ImageButton btnAssistant;

    public interface OnAppDockListener {
        void onLayoutModeToggle();
        void onAppDrawerOpen();
        void onDesktopModeToggle();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            dockManager = new AppDockManager(getContext());
            dockManager.loadShortcuts();
            overlayManager = new OverlayWindowManager(getContext());
            prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            currentOrientation = prefs.getInt(KEY_ORIENTATION, ORIENTATION_HORIZONTAL);

            // Music Manager
            musicManager = MusicManager.getInstance(getContext());

            // Register Dock Update Receiver
            dockUpdateReceiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    refreshDock();
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter(
                    getContext().getPackageName() + ".DOCK_UPDATED");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(dockUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(dockUpdateReceiver, filter);
            }
        }
        
        // Register Local Broadcast Receiver
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                .registerReceiver(dockUpdateReceiver, new android.content.IntentFilter(getContext().getPackageName() + ".DOCK_UPDATED"));
    }

    private android.content.BroadcastReceiver dockUpdateReceiver;

    /**
     * Dock'u yeniden yukle ve UI'i guncelle.
     */
    public void refreshDock() {
        if (dockManager != null && adapter != null && getActivity() != null) {
            dockManager.loadShortcuts();
            getActivity().runOnUiThread(() -> {
                adapter.setShortcuts(dockManager.getShortcuts());
                adapter.notifyDataSetChanged();
            });
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnAppDockListener) {
            listener = (OnAppDockListener) context;
        } else {
            android.util.Log.w(TAG, "Host activity does not implement OnAppDockListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        
        app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
        boolean isPortrait = CarLayoutManager.isPortraitWindow(requireActivity());
        String dockPos = settings.getEffectiveDockPosition(isPortrait);
        this.isVerticalMode = ("left".equals(dockPos) || "right".equals(dockPos)) && !isPortrait;

        int layoutId;
        if (isPortrait) {
            layoutId = app.organicmaps.R.layout.fragment_app_dock_portrait;
        } else if (isVerticalMode) {
            layoutId = app.organicmaps.R.layout.fragment_app_dock_sidebar;
        } else {
            layoutId = app.organicmaps.R.layout.fragment_app_dock_horizontal;
        }
        this.currentLayoutId = layoutId;
        View root = inflater.inflate(layoutId, container, false);
        root.post(() -> {
            ViewGroup.LayoutParams lp = root.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                root.setLayoutParams(lp);
            }
        });

        // Find Views
        appListButton = root.findViewById(app.organicmaps.R.id.btn_app_list);
        layoutButton = root.findViewById(app.organicmaps.R.id.btn_layout_toggle);
        recyclerView = root.findViewById(app.organicmaps.R.id.dock_recycler);

        // Setup Containers & Assistant Button
        leftContainer = root.findViewById(app.organicmaps.R.id.left_buttons_container);
        rightContainer = root.findViewById(app.organicmaps.R.id.right_buttons_container);
        centerContainer = root.findViewById(app.organicmaps.R.id.center_content_container);
        btnAssistant = root.findViewById(app.organicmaps.R.id.btn_assistant);

        // Setup New Views
        clockView = root.findViewById(app.organicmaps.R.id.dock_clock);
        miniMusicContainer = root.findViewById(app.organicmaps.R.id.mini_music_container);
        miniMusicTitle = root.findViewById(app.organicmaps.R.id.mini_music_title);
        miniBtnPlay = root.findViewById(app.organicmaps.R.id.mini_btn_play);
        miniBtnNext = root.findViewById(app.organicmaps.R.id.mini_btn_next);
        miniMusicIcon = root.findViewById(app.organicmaps.R.id.mini_music_icon);

        if (btnAssistant != null) {
            btnAssistant.setOnClickListener(v -> checkVoicePermissionAndToggle());
            btnAssistant.setOnLongClickListener(v -> {
                openAssistantSettings();
                return true;
            });
        }

        if (clockView != null) {
            clockView.setOnClickListener(v -> openSettings());
            try {
                Typeface digitalFont = Typeface.createFromAsset(requireContext().getAssets(), "fonts/Cross Boxed.ttf");
                clockView.setTypeface(digitalFont);
            } catch (Exception e) {
                Log.e(TAG, "Font bulunamadi: " + e.getMessage());
                clockView.setTypeface(Typeface.DEFAULT_BOLD);
            }
        }

        // Dikey ve yatay ekran durumlarina gore mini player tasarimini ozellestir
        adjustMiniPlayerLayout();

        if (miniBtnPlay != null) {
            miniBtnPlay.setOnClickListener(v -> musicManager.togglePlayPause());
        }

        if (miniBtnNext != null) {
            miniBtnNext.setOnClickListener(v -> musicManager.skipToNext());
        }

        View.OnClickListener musicDrawerOpener = v -> {
            // Use both global and local broadcast for compatibility (Turkce karakter yok)
            Intent intent = new Intent(getContext().getPackageName() + ".OPEN_MUSIC_DRAWER");
            intent.setPackage(getContext().getPackageName());
            getContext().sendBroadcast(intent);

            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext())
                    .sendBroadcast(new Intent(getContext().getPackageName() + ".OPEN_MUSIC_DRAWER"));
        };

        if (miniMusicTitle != null) {
            miniMusicTitle.setOnClickListener(musicDrawerOpener);
        }

        if (miniMusicIcon != null) {
            miniMusicIcon.setOnClickListener(musicDrawerOpener);
        }

        // Start Clock
        startClock();

        // Setup Buttons
        // Listeners
        btnDesktopMode = root.findViewById(app.organicmaps.R.id.btn_desktop_mode);
        if (btnDesktopMode != null) {
            // Yuze buton ile ayni simgeyi ve renk filtresini ata (Turkce karakter yok)
            btnDesktopMode.setImageResource(app.organicmaps.R.drawable.ic_desktop_mode);
            btnDesktopMode.setColorFilter(0xFFFFFFFF);
            
            // XML'deki varsayilan arkaplan korunsun (Daire silindi)
            
            // Tiklama: Desktop Mode Toggle
            btnDesktopMode.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDesktopModeToggle();
                }
            });
            
            // Uzun basma: Ayarlari ac
            btnDesktopMode.setOnLongClickListener(v -> {
                openSettings();
                return true;
            });
        }

        if (appListButton != null) {
            appListButton.setOnClickListener(v -> {
                if (listener != null)
                    listener.onAppDrawerOpen();
            });
        }

        // Butun dock butonlarinin boyutunu kisayollar ile (AppDockAdapter) esitle
        adjustButtonSizes();

        // Sag Buton: 3-nokta Menu (Tiklandiginda acilir menu gosterir)
        if (layoutButton != null) {
            layoutButton.setOnClickListener(v -> {
                showDockPopupMenu(v);
            });
            // Uzun basildiginda da dogrudan ayarlari acar
            layoutButton.setOnLongClickListener(v -> {
                openSettings();
                return true;
            });
        }

        // Clock container (Removes generic click to avoid confusion with layout toggle)
        View clockContainer = root.findViewById(app.organicmaps.R.id.clock_settings_container);
        if (clockContainer != null) {
            // Optional: Clicking clock could open clock app, but removing settings link for
            // now
            clockContainer.setOnClickListener(null);
        }

        // Setup RecyclerView
        updateRecyclerViewOrientation(root);

        // Responsive visibility listener based on available dock width
        View dockContainer = root.findViewById(app.organicmaps.R.id.dock_content_container);
        if (dockContainer != null) {
            dockContainer.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int width = right - left;
                    if (width > 0) {
                        adjustVisibilityByAvailableWidth(width);
                    }
                }
            });
        }

        // Long Press on Root & Recycler to add apps
        View.OnLongClickListener longClickListener = v -> {
            showAppPickerDialog();
            return true;
        };
        root.setOnLongClickListener(longClickListener);
        recyclerView.setOnLongClickListener(longClickListener);

        // Drag and Drop
        androidx.recyclerview.widget.ItemTouchHelper.Callback callback = new androidx.recyclerview.widget.ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder) {
                int dragFlags = androidx.recyclerview.widget.ItemTouchHelper.LEFT
                        | androidx.recyclerview.widget.ItemTouchHelper.RIGHT
                        | androidx.recyclerview.widget.ItemTouchHelper.UP
                        | androidx.recyclerview.widget.ItemTouchHelper.DOWN;
                return makeMovementFlags(dragFlags, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();

                // Update Adapter
                if (adapter != null) {
                    adapter.onItemMove(from, to);
                }
                // Update Manager
                if (dockManager != null) {
                    dockManager.moveShortcut(from, to);
                }
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        };
        new androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(recyclerView);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Ensure orientation is correct based on global settings before creating adapter
        app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
        String dockPos = settings.getDockPosition();
        boolean isPortrait = CarLayoutManager.isPortraitWindow(requireActivity());
        this.isVerticalMode = ("left".equals(dockPos) || "right".equals(dockPos)) && !isPortrait;
        
        adapter = new AppDockAdapter(getContext(), this);
        adapter.setVerticalMode(isVerticalMode); // Set it before attaching to recycler
        recyclerView.setAdapter(adapter);
        
        if (dockManager != null) {
            adapter.setShortcuts(dockManager.getShortcuts());
        }
        
        // Force apply UI constraints
        applyOrientationState(view, isVerticalMode);
        
        // Sync Desktop Mode color filter on launch
        if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
            updateDesktopModeState(
                    ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                            .isDesktopMode());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (musicManager != null) {
            musicManager.addListener(this);
        }
        updateMiniMusicUI(); // Ilk yuklemede durum guncelle (Turkce karakter yok)
        updateAssistantButtonUI(); // Asistan buton durumunu guncelle
    }

    @Override
    public void onPause() {
        super.onPause();
        if (musicManager != null) {
            musicManager.removeListener(this);
        }
    }

    private void updateMiniMusicUI() {
        if (musicManager == null) return;
        
        // Calan sarkiyi merkezi kaynaga gore oku ve set et (Turkce karakter yok)
        boolean isExternal = musicManager.useExternal();
        if (isExternal && musicManager.getActiveExternalController() != null && musicManager.getActiveExternalController().getMetadata() != null) {
            android.media.MediaMetadata metadata = musicManager.getActiveExternalController().getMetadata();
            String title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE);
            if (miniMusicTitle != null) {
                miniMusicTitle.post(() -> miniMusicTitle.setText(title != null ? title : "Muzik"));
            }
        } else {
            app.organicmaps.carlauncher.music.MusicRepository.AudioTrack track = 
                musicManager.getInternalPlayer().getCurrentTrack();
            if (miniMusicTitle != null) {
                miniMusicTitle.post(() -> miniMusicTitle.setText(track != null ? track.getTitle() : "Muzik Secin"));
            }
        }
        
        // Play/Pause buton ikonunu guncelle (Turkce karakter yok)
        if (miniBtnPlay != null) {
            boolean isPlaying = false;
            if (isExternal) {
                isPlaying = musicManager.getActiveExternalController() != null && 
                    musicManager.getActiveExternalController().getPlaybackState() != null && 
                    musicManager.getActiveExternalController().getPlaybackState().getState() == android.media.session.PlaybackState.STATE_PLAYING;
            } else {
                isPlaying = musicManager.getInternalPlayer().isPlaying();
            }
            final boolean finalIsPlaying = isPlaying;
            miniBtnPlay.post(() -> miniBtnPlay.setImageResource(
                    finalIsPlaying ? app.organicmaps.R.drawable.ic_music_pause : app.organicmaps.R.drawable.ic_music_play));
        }
    }

    // --- MusicUIListener ---

    @Override
    public void onTrackChanged(String title, String artist, Bitmap albumArt, String packageName) {
        updateMiniMusicUI(); // Sarki ismi ve play ikonunu guncelle (Turkce karakter yok)
        
        // Dynamic Color Logic
        int color = android.graphics.Color.WHITE;
        if (albumArt != null) {
            color = getDominantColor(albumArt);
        }

        final int finalColor = color;
        if (miniMusicIcon != null) miniMusicIcon.post(() -> miniMusicIcon.setColorFilter(finalColor));
        if (miniBtnPlay != null) miniBtnPlay.post(() -> miniBtnPlay.setColorFilter(finalColor));
        if (miniBtnNext != null) miniBtnNext.post(() -> miniBtnNext.setColorFilter(finalColor));
    }

    private int getDominantColor(Bitmap bitmap) {
        if (bitmap == null) return android.graphics.Color.WHITE;
        try {
            // Sample 1x1 pixel for average
            Bitmap small = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
            int color = small.getPixel(0, 0);
            small.recycle();

            // Check Luminance (if too dark, return White)
            int r = android.graphics.Color.red(color);
            int g = android.graphics.Color.green(color);
            int b = android.graphics.Color.blue(color);
            double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

            if (lum < 0.5) return android.graphics.Color.WHITE; // Ensure visibility on dark background
            return color;
        } catch (Exception e) {
            return android.graphics.Color.WHITE;
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        updateMiniMusicUI(); // Oynatma durumunu guncelle (Turkce karakter yok)
    }

    @Override
    public void onSourceChanged(boolean isInternal) {
        // Kaynak degistiginde yapilacaklar (Ornegin ikon degisimi)
        if (miniMusicIcon != null) {
            miniMusicIcon.post(() -> {
                miniMusicIcon.setImageResource(isInternal ? app.organicmaps.R.drawable.ic_music_play : // Internal icon
                        android.R.drawable.stat_sys_headset); // External icon placeholder
                miniMusicIcon.setColorFilter(android.graphics.Color.WHITE);
            });
        }
    }

    @Override
    public void onShortcutClick(AppShortcut shortcut) {
        if (getContext() == null)
            return;
        String packageName = shortcut.getPackageName();
        if (app.organicmaps.carlauncher.dock.InternalApp.isInternalApp(packageName)) {
            app.organicmaps.carlauncher.dock.InternalAppLauncher.launch(getContext(), packageName);
            return;
        }
        LaunchMode mode = shortcut.getLaunchMode();
        try {
            switch (mode) {
                case FULL_SCREEN:
                    launchAppStandard(packageName);
                    break;
                case OVERLAY:
                    if (overlayManager != null)
                        try {
                            overlayManager.showOverlay(packageName);
                        } catch (Exception e) {
                            launchAppStandard(packageName);
                        }
                    break;
                case SPLIT_SCREEN:
                    launchAppSplitScreen(packageName);
                    break;
                case WIDGET_ONLY:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching app: " + packageName, e);
        }
    }



    private void launchAppStandard(String packageName) {
        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Launch failed", e);
        }
    }

    private void launchAppSplitScreen(String packageName) {
        try {
            Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                }
                getContext().startActivity(intent);
            }
        } catch (Exception e) {
            launchAppStandard(packageName);
        }
    }

    @Override
    public void onShortcutLongClick(AppShortcut shortcut) {
        if (getContext() == null || dockManager == null) return;
        
        String[] options = {
            "Uygulamayi Ac (Tam Ekran)",
            "Uygulamayi Yan Yana Ac (Bolunmus Ekran / Coolwalk)",
            "Varsayilan Acilis Modu: Tam Ekran Yap",
            "Varsayilan Acilis Modu: Yan Yana (Bolunmus) Yap",
            "Kisayolu Kaldir"
        };
        
        new AlertDialog.Builder(getContext())
                .setTitle(shortcut.getAppName() + " Secenekleri")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Tam Ekran Baslat
                            launchAppWithMode(shortcut, LaunchMode.FULL_SCREEN);
                            break;
                        case 1: // Yan Yana / Split-Screen Baslat
                            launchAppWithMode(shortcut, LaunchMode.SPLIT_SCREEN);
                            break;
                        case 2: // Varsayilan modu Tam Ekran yap
                            shortcut.setLaunchMode(LaunchMode.FULL_SCREEN);
                            dockManager.saveShortcuts();
                            if (adapter != null) {
                                adapter.setShortcuts(dockManager.getShortcuts());
                            }
                            break;
                        case 3: // Varsayilan modu Split-Screen yap
                            shortcut.setLaunchMode(LaunchMode.SPLIT_SCREEN);
                            dockManager.saveShortcuts();
                            if (adapter != null) {
                                adapter.setShortcuts(dockManager.getShortcuts());
                            }
                            break;
                        case 4: // Kaldir
                            onRemoveClick(shortcut);
                            break;
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void launchAppWithMode(AppShortcut shortcut, LaunchMode mode) {
        try {
            Intent intent = getContext().getPackageManager()
                    .getLaunchIntentForPackage(shortcut.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (mode == LaunchMode.SPLIT_SCREEN) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    }
                }
                getContext().startActivity(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onRemoveClick(AppShortcut shortcut) {
        new AlertDialog.Builder(getContext())
                .setTitle("Kisayol Kaldir")
                .setMessage(shortcut.getAppName() + " kisayolunu kaldirmak istiyor musunuz?")
                .setPositiveButton("Kaldir", (dialog, which) -> {
                    if (dockManager != null) {
                        dockManager.removeShortcut(shortcut);
                        adapter.setShortcuts(dockManager.getShortcuts());
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void showAppPickerDialog() {
        if (getContext() == null || dockManager == null)
            return;
        AppPickerDialog dialog = new AppPickerDialog(getContext(), (packageName, appName, icon) -> {
            AppShortcut newShortcut = new AppShortcut(packageName, appName, icon, dockManager.getShortcuts().size(),
                    LaunchMode.FULL_SCREEN);
            if (dockManager.addShortcut(newShortcut)) {
                if (adapter != null && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.setShortcuts(dockManager.getShortcuts());
                        adapter.notifyDataSetChanged();
                    });
                }
            }
        });
        dialog.show();
    }

    private void openSettings() {
        if (getContext() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
            ((app.organicmaps.carlauncher.CarLauncherInterface) getContext())
                    .openCarLauncherSettings();
        }
    }

    // --- Clock Logic ---
    private void startClock() {
        clockRunnable = new Runnable() {
            @Override
            public void run() {
                if (clockView != null) {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                            isVerticalMode ? "HH\nmm" : "HH:mm",
                            java.util.Locale.getDefault());
                    clockView.setText(sdf.format(new java.util.Date()));
                }
                clockHandler.postDelayed(this, 1000);
            }
        };
        clockHandler.post(clockRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (clockRunnable != null)
            clockHandler.removeCallbacks(clockRunnable);

        // Unregister dock update receiver
        if (dockUpdateReceiver != null && getContext() != null) {
            try {
                getContext().unregisterReceiver(dockUpdateReceiver);
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(dockUpdateReceiver);
            } catch (Exception e) {
                // Ignore if already unregistered
            }
        }
    }

    public void updateLayoutIcon(int mode) {
        ImageButton button = layoutButton;
        if (button == null) return;

        button.post(() -> {
            if (!isAdded() || getView() == null || layoutButton != button
                    || !button.isAttachedToWindow()) {
                return;
            }
            switch (mode) {
                case 0: // Normal (Widgets Visible)
                    button.setImageResource(app.organicmaps.R.drawable.ic_layout_full);
                    break;
                case 2: // Full Screen (Map Only)
                    button.setImageResource(app.organicmaps.R.drawable.ic_layout_split);
                    break;
                default: 
                    button.setImageResource(app.organicmaps.R.drawable.ic_layout_split);
                    break;
            }
        });
    }

    public void updateDesktopModeState(boolean active) {
        ImageButton desktopButton = btnDesktopMode;
        if (desktopButton == null) return;

        desktopButton.post(() -> {
            Context context = getContext();
            View fragmentView = getView();
            if (!isAdded() || context == null || fragmentView == null
                    || btnDesktopMode != desktopButton
                    || !desktopButton.isAttachedToWindow()) {
                return;
            }
            if (active) {
                // Aktifken premium primary brand rengiyle vurgula
                desktopButton.setColorFilter(androidx.core.content.ContextCompat.getColor(
                        context, app.organicmaps.R.color.cl_primary));
            } else {
                // Pasifken beyaz / yari transparan hint rengi
                if (isVerticalMode) {
                    desktopButton.setColorFilter(0x88FFFFFF); // Dikey mod pasif rengi
                } else {
                    desktopButton.setColorFilter(androidx.core.content.ContextCompat.getColor(
                            context, app.organicmaps.R.color.cl_text_hint));
                }
            }

            // Mini muzik calarin gorunurlugunu desktop/harita moduna gore guncelle
            LinearLayout musicContainer = miniMusicContainer;
            if (musicContainer != null) {
                boolean isScreenPortrait = getActivity() != null
                        && CarLayoutManager.isPortraitWindow(requireActivity());
                int layoutMode = 0;
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    layoutMode =
                            ((app.organicmaps.carlauncher.CarLauncherInterface) getActivity())
                                    .getLayoutMode();
                }
                boolean shouldShow = false;
                if (!isScreenPortrait && !isVerticalMode) {
                    if (active || layoutMode == 2) {
                        shouldShow = true;
                    }
                }
                musicContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
                if (shouldShow) {
                    adjustMiniPlayerLayout();
                }
            }
        });
    }


    private int getScaledIconSize() {
        if (getContext() == null) return dpToPx(48);
        int baseSize = (int) getContext().getResources().getDimension(app.organicmaps.R.dimen.dock_icon_size);
        app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
        int dockSizePercent = settings.getDockSize();
        float scale = 0.3f + (dockSizePercent / 100.0f) * 1.4f;
        return (int) (baseSize * scale);
    }

    private void adjustButtonSizes() {
        int iconSize = getScaledIconSize();
        int itemSize = iconSize + dpToPx(16); // Sabit dokunma alani, AppDockAdapter ile ayni

        View[] buttons = {btnDesktopMode, appListButton, btnAssistant};
        for (View btn : buttons) {
            if (btn != null) {
                ViewGroup.LayoutParams lp = btn.getLayoutParams();
                if (lp != null) {
                    lp.width = itemSize;
                    lp.height = itemSize;
                    btn.setLayoutParams(lp);
                }
                int padding = dpToPx(8); // Standart ikon ici bosluk
                btn.setPadding(padding, padding, padding, padding);
                if (btn instanceof ImageView) {
                    ((ImageView)btn).setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
            }
        }
    }

    public void setOrientation(boolean isVertical) {
        this.isVerticalMode = isVertical;
        this.currentOrientation = isVertical ? ORIENTATION_VERTICAL : ORIENTATION_HORIZONTAL;
        
        if (getView() != null) {
            applyOrientationState(getView(), isVertical);
        }
    }

    private void applyOrientationState(View root, boolean isVertical) {
        root.post(() -> {
            if (getContext() == null) return;
            boolean isScreenPortrait = CarLayoutManager.isPortraitWindow(requireActivity());

            ViewGroup.LayoutParams rootLp = root.getLayoutParams();
            if (rootLp != null) {
                rootLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                rootLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                root.setLayoutParams(rootLp);
            }

            // 1. Update Recycler Orientation
            updateRecyclerViewOrientation(root);

            // 2. Conditional Visibility of Mini Music Player
            if (miniMusicContainer != null) {
                boolean isDesktop = false;
                int layoutMode = 0;
                if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
                    app.organicmaps.carlauncher.CarLauncherInterface activity =
                            (app.organicmaps.carlauncher.CarLauncherInterface) getActivity();
                    isDesktop = activity.isDesktopMode();
                    layoutMode = activity.getLayoutMode();
                }

                // Mini muzik calarin gorunurluk kurallari:
                // 1. Ekran dikey portrait ise -> GONE (Zaten XML'de oyle tasarlandi ama garanti edelim)
                // 2. Rihtim dikey sidebar ise -> GONE (Dar alan)
                // 3. Yatay ekranda ve yatay rihtimda -> Sadece isDesktop == true VEYA layoutMode == 2 ise VISIBLE. Diger durumlarda GONE.
                boolean shouldShow = false;
                if (!isScreenPortrait && !isVertical) {
                    if (isDesktop || layoutMode == 2) {
                        shouldShow = true;
                    }
                }
                miniMusicContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
                if (shouldShow) {
                    adjustMiniPlayerLayout(); // (Ici bosaltildi, gelecekte scale vb gerekirse diye birakildi)
                }
            }

            // 3. Dynamic Scaling for Clock & Icons
            if (clockView != null) {
                app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
                int dockSizePercent = settings.getDockSize();
                float scale = 0.3f + (dockSizePercent / 100.0f) * 1.4f;
                float baseTextSize = isVertical ? 18f : 22f;
                clockView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseTextSize * scale);
                
                // Clock format hala java uzerinden guncellenmeli (Thread sebebiyle)
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        isVertical ? "HH\nmm" : "HH:mm",
                        java.util.Locale.getDefault());
                clockView.setText(sdf.format(new java.util.Date()));
            }

            app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
            float scale = 0.3f + (settings.getDockSize() / 100.0f) * 1.4f;
            adjustButtonSizes();
            
            // Adapter ikonlarinin guncellenmesi icin
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            
            // Layout ve Apps butonlarinin aralik/bosluk ayari
            if (leftContainer != null) {
                int topPadding = (int) (dpToPx(6) * scale);
                int bottomPadding = (int) (dpToPx(2) * scale);
                leftContainer.setPadding(isVertical ? 0 : topPadding, isVertical ? topPadding : 0, isVertical ? 0 : bottomPadding, isVertical ? bottomPadding : 0);
            }
            if (appListButton != null && appListButton.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) appListButton.getLayoutParams();
                if (isVertical) {
                    // Dikeyde aradaki boslugu maksimum duzeyde kuculttuk
                    mlp.topMargin = (int) (dpToPx(1) * scale);
                    mlp.leftMargin = 0;
                } else {
                    mlp.leftMargin = (int) (dpToPx(6) * scale);
                    mlp.topMargin = 0;
                }
                appListButton.setLayoutParams(mlp);
            }
            
            if (layoutButton != null) {
                layoutButton.setVisibility(View.GONE);
            }
        });
    }

    private void updateIconSize(View v, float scale) {
        if (v == null) return;
        
        int baseSize = dpToPx(48); // Varsayilan buton boyutu
        int scaledSize = (int) (baseSize * scale);
        
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            lp.width = scaledSize;
            lp.height = scaledSize;
            v.setLayoutParams(lp);
        }
        
        if (v instanceof android.widget.ImageView) {
            ((android.widget.ImageView) v).setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        }
    }

    private void updateRecyclerViewOrientation(View root) {
        if (recyclerView == null) recyclerView = root.findViewById(app.organicmaps.R.id.dock_recycler);
        if (recyclerView != null) {
            int desiredOrientation = isVerticalMode ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL;
            androidx.recyclerview.widget.RecyclerView.LayoutManager currentLayoutManager = recyclerView.getLayoutManager();
            boolean needNewLayoutManager = true;
            
            if (currentLayoutManager instanceof LinearLayoutManager) {
                if (((LinearLayoutManager) currentLayoutManager).getOrientation() == desiredOrientation) {
                    needNewLayoutManager = false;
                }
            }
            
            if (needNewLayoutManager) {
                // Force clear recycled view pool to ensure fresh ViewHolders with correct LayoutParams
                recyclerView.setRecycledViewPool(new androidx.recyclerview.widget.RecyclerView.RecycledViewPool());
                
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), desiredOrientation, false));
                if (adapter != null) {
                    adapter.setVerticalMode(isVerticalMode);
                }
            }
        }
    }

    private void adjustMiniPlayerLayout() {
        // XML mimarisine gecildigi icin mini player layout ayarlarina artik gerek yoktur.
        // Boyutlandirma ve gorsel yerlesimler (Portrait, Sidebar, Bottom) res/layout icindeki
        // ilgili XML dosyalarina tasinmistir.
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    public boolean needsLayoutUpdate() {
        if (getContext() == null) return false;
        app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
        boolean isPortrait = CarLayoutManager.isPortraitWindow(requireActivity());
        String dockPos = settings.getEffectiveDockPosition(isPortrait);
        boolean expectedVerticalMode = ("left".equals(dockPos) || "right".equals(dockPos)) && !isPortrait;

        int expectedLayoutId;
        if (isPortrait) {
            expectedLayoutId = app.organicmaps.R.layout.fragment_app_dock_portrait;
        } else if (expectedVerticalMode) {
            expectedLayoutId = app.organicmaps.R.layout.fragment_app_dock_sidebar;
        } else {
            expectedLayoutId = app.organicmaps.R.layout.fragment_app_dock_horizontal;
        }
        
        return currentLayoutId != 0 && currentLayoutId != expectedLayoutId;
    }

    public void refreshLayout() {
        View root = getView();
        Context context = getContext();
        if (root == null || context == null) return;
        app.organicmaps.carlauncher.CarLauncherSettings settings =
                new app.organicmaps.carlauncher.CarLauncherSettings(context);
        boolean portrait = CarLayoutManager.isPortraitWindow(requireActivity());
        String dockPos = settings.getEffectiveDockPosition(portrait);
        boolean vertical = !portrait
                && ("left".equals(dockPos) || "right".equals(dockPos));
        isVerticalMode = vertical;
        applyOrientationState(root, vertical);
    }

    private void showDockPopupMenu(View anchor) {
        if (getContext() == null) return;
        
        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        
        // Turkce karakter kullanmadan menuyu dinamik olarak dolduruyoruz
        popup.getMenu().add(0, 1, 0, "Gorunumu Degistir (Buyuk/Kucuk Panel)");
        
        if (adapter != null) {
            popup.getMenu().add(0, 2, 1, adapter.isEditMode() ? "Duzenleme Modunu Kapat" : "Kisayollari Duzenle");
        }
        
        popup.getMenu().add(0, 3, 2, "Ayarlar");
        
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (listener != null) {
                        listener.onLayoutModeToggle();
                    }
                    return true;
                case 2:
                    if (adapter != null) {
                        adapter.setEditMode(!adapter.isEditMode());
                    }
                    return true;
                case 3:
                    openSettings();
                    return true;
                default:
                    return false;
            }
        });
        
        popup.show();
    }

    private void adjustVisibilityByAvailableWidth(int totalWidthPx) {
        // XML mimarisi sayesinde ekran yonelimi ve genislik sorunlari 
        // layout-port ve dikey/yatay XML tasarimlari tarafindan cozuldugunden 
        // buradaki programatik gizleme islemine gerek kalmamistir.
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getContext() == null || getView() == null) return;

        app.organicmaps.carlauncher.CarLauncherSettings settings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
        boolean isPortrait = CarLayoutManager.isPortraitWindow(requireActivity());
        String dockPos = settings.getEffectiveDockPosition(isPortrait);
        this.isVerticalMode = ("left".equals(dockPos) || "right".equals(dockPos)) && !isPortrait;

        if (needsLayoutUpdate()) return;
        if (adapter != null) {
            adapter.setVerticalMode(isVerticalMode);
        }

        applyOrientationState(getView(), isVerticalMode);
    }

    private void checkVoicePermissionAndToggle() {
        if (getContext() == null) return;
        if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
        } else {
            toggleVoiceControlService();
        }
    }

    private void toggleVoiceControlService() {
        if (getContext() == null) return;
        Intent intent = new Intent(getContext(), app.organicmaps.carlauncher.voice.VoiceCommandService.class);
        if (app.organicmaps.carlauncher.voice.VoiceCommandService.isServiceRunning) {
            getContext().stopService(intent);
            android.widget.Toast.makeText(getContext(), "Sesli kontrol kapatildi", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            android.widget.Toast.makeText(getContext(), "Sesli kontrol baslatildi", android.widget.Toast.LENGTH_SHORT).show();
        }
        new Handler(Looper.getMainLooper()).postDelayed(this::updateAssistantButtonUI, 300);
    }

    private void updateAssistantButtonUI() {
        if (btnAssistant == null) return;
        boolean isRunning = app.organicmaps.carlauncher.voice.VoiceCommandService.isServiceRunning;
        if (isRunning) {
            btnAssistant.setColorFilter(0xFF00FFFF);
            btnAssistant.setBackgroundResource(app.organicmaps.R.drawable.bg_circle_translucent_white);
        } else {
            btnAssistant.setColorFilter(0xFFFFFFFF);
            btnAssistant.setBackgroundResource(0);
        }
    }

    private void openAssistantSettings() {
        if (getContext() instanceof app.organicmaps.carlauncher.CarLauncherInterface) {
            ((app.organicmaps.carlauncher.CarLauncherInterface) getContext())
                    .openCarLauncherSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                toggleVoiceControlService();
            } else {
                android.widget.Toast.makeText(getContext(), "Ses kaydetme izni verilmedi", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }
}
