package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.organicmaps.carlauncher.music.MusicManager;
import app.organicmaps.carlauncher.music.MusicRepository;
import app.organicmaps.carlauncher.music.MusicTrackIdentity;
import app.organicmaps.carlauncher.music.LocalAlbumArtLoader;
import app.organicmaps.carlauncher.music.OnlineAlbumArtLoader;
import app.organicmaps.carlauncher.music.PlaylistManager;
import app.organicmaps.carlauncher.dock.AppPickerDialog;
import app.organicmaps.carlauncher.CarLauncherInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Modern Muzik Player Fragment.
 * Iki mod destekler: Harici uygulama kontrolu ve Dahili calma.
 */
public class MusicPlayerFragment extends Fragment implements MusicManager.MusicUIListener, MusicManager.MusicVisualizerListener {

    // ...

    // Duplcates removed


    private MusicManager musicManager;
    private PlaylistManager playlistManager;
    private RecyclerView recyclerView;
    private MusicAdapter adapter;

    // UI Elements
    private LinearLayout trackListPanel;
    private View playerPanel;
    private View musicSideDock;
    private ImageButton btnDockPlaylist, btnScanMusic, btnTabScan;
    private final MusicRepository.ScanStateListener scanStateListener = this::handleMusicScanState;
    private ImageView appIcon;
    private View appSelectorLaunch;
    private ImageButton btnPlaylist, btnClose, btnEqualizer;
    private ImageView nowPlayingArt;
    private ImageView nowPlayingArtBlur;
    private TextView nowPlayingTitle, nowPlayingArtist;
    private SeekBar seekbar;
    private TextView timeCurrent, timeTotal;
    private View playerProgressContainer;
    private ImageButton btnShuffle, btnPrev, btnPlay, btnNext, btnRepeat;
    private Spinner playlistSpinner;
    private EditText searchInput;
    private View searchBarContainer;
    private View searchClearBtn;

    // New 4-Tab Views
    private TextView tabQueue, tabAllTracks, tabFolders, tabPlaylists;
    private ImageButton tabBtnSearch, tabBtnMenu;
    private View folderHeaderContainer;
    private ImageButton btnBackFolder;
    private TextView folderHeaderTitle;

    // View Modes & Klasor Hiyerarsisi
    public enum ViewMode {
        QUEUE, ALL_TRACKS, FOLDERS, PLAYLIST, FOLDER_DETAIL
    }
    public enum SortOrder {
        TITLE, ARTIST, ALBUM, RECENTLY_ADDED, MOST_PLAYED
    }
    private enum FolderViewLevel {
        STORAGE_ROOT, FOLDER_LIST, TRACK_LIST
    }
    private ViewMode currentViewMode = ViewMode.QUEUE;
    private FolderViewLevel currentFolderLevel = FolderViewLevel.STORAGE_ROOT;
    private MusicRepository.StorageType selectedStorageType = MusicRepository.StorageType.INTERNAL;
    private MusicRepository.AudioFolder currentFolder = null;
    private PlaylistManager.Playlist currentPlaylist = null;


    // State
    private boolean isExternalMode = true; // Default: external app control
    private boolean isShuffleOn = false;
    private int repeatMode = 0; // 0=off, 1=one, 2=all

    // Seekbar Handler
    private final Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable seekRunnable;

    // Data
    private List<MusicRepository.AudioTrack> allTracks = new ArrayList<>();
    private List<MusicRepository.AudioTrack> filteredTracks = new ArrayList<>();

    // Now Playing Center UI Elements
    private View nowPlayingCenterPanel;
    private ImageView nowPlayingCenterArt;
    private TextView nowPlayingCenterTitle;
    private TextView nowPlayingCenterArtist;
    private boolean isPlaylistVisible = true;
    private boolean mediaPermissionRequestInFlight;
    private boolean mediaPermissionNoticeShown;
    private View ambianceGlowLayer;
    private boolean hasAlbumArtwork;
    private String artworkLookupKey;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            musicManager = MusicManager.getInstance(getContext());
            playlistManager = new PlaylistManager(getContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(app.organicmaps.R.layout.fragment_music_player, container, false);

        // Find Views
        trackListPanel = root.findViewById(app.organicmaps.R.id.track_list_panel);
        playerPanel = root.findViewById(app.organicmaps.R.id.player_panel);
        musicSideDock = root.findViewById(app.organicmaps.R.id.music_side_dock);
        btnDockPlaylist = root.findViewById(app.organicmaps.R.id.btn_dock_playlist);
        btnScanMusic = root.findViewById(app.organicmaps.R.id.btn_scan_music);
        btnTabScan = root.findViewById(app.organicmaps.R.id.tab_btn_scan);
        appIcon = root.findViewById(app.organicmaps.R.id.app_icon);
        appSelectorLaunch = root.findViewById(app.organicmaps.R.id.app_selector_launch);
        // btnPlaylist = root.findViewById(app.organicmaps.R.id.btn_playlist);
        btnEqualizer = root.findViewById(app.organicmaps.R.id.btn_dock_equalizer);
        btnClose = root.findViewById(app.organicmaps.R.id.btn_close);
        nowPlayingArt = root.findViewById(app.organicmaps.R.id.now_playing_art);
        nowPlayingArtBlur = root.findViewById(app.organicmaps.R.id.now_playing_art_blur);
        ambianceGlowLayer = root.findViewById(app.organicmaps.R.id.ambiance_glow_layer);
        nowPlayingTitle = root.findViewById(app.organicmaps.R.id.now_playing_title);
        nowPlayingArtist = root.findViewById(app.organicmaps.R.id.now_playing_artist);

        nowPlayingCenterPanel = root.findViewById(app.organicmaps.R.id.now_playing_center_panel);
        nowPlayingCenterArt = root.findViewById(app.organicmaps.R.id.now_playing_center_art);
        nowPlayingCenterTitle = root.findViewById(app.organicmaps.R.id.now_playing_center_title);
        nowPlayingCenterArtist = root.findViewById(app.organicmaps.R.id.now_playing_center_artist);
        seekbar = root.findViewById(app.organicmaps.R.id.seekbar);
        timeCurrent = root.findViewById(app.organicmaps.R.id.time_current);
        timeTotal = root.findViewById(app.organicmaps.R.id.time_total);
        playerProgressContainer = root.findViewById(app.organicmaps.R.id.player_progress_container);
        btnShuffle = root.findViewById(app.organicmaps.R.id.btn_shuffle);
        btnPrev = root.findViewById(app.organicmaps.R.id.btn_prev);
        btnPlay = root.findViewById(app.organicmaps.R.id.btn_play);
        btnNext = root.findViewById(app.organicmaps.R.id.btn_next);
        btnRepeat = root.findViewById(app.organicmaps.R.id.btn_repeat);
        playlistSpinner = root.findViewById(app.organicmaps.R.id.playlist_spinner);
        searchInput = root.findViewById(app.organicmaps.R.id.search_input);
        searchBarContainer = root.findViewById(app.organicmaps.R.id.search_bar_container);
        searchClearBtn = root.findViewById(app.organicmaps.R.id.search_clear_btn);

        // Responsive Layout Listener
        root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            private int lastWidth = 0;
            private int lastHeight = 0;
            @Override
            public void onGlobalLayout() {
                int width = root.getWidth();
                int height = root.getHeight();
                if ((width != lastWidth || height != lastHeight) && width > 0 && height > 0) {
                    lastWidth = width;
                    lastHeight = height;
                    updateResponsiveLayout(width, height);
                }
            }
        });

        recyclerView = root.findViewById(app.organicmaps.R.id.music_recycler);

        tabQueue = root.findViewById(app.organicmaps.R.id.tab_queue);
        tabAllTracks = root.findViewById(app.organicmaps.R.id.tab_all_tracks);
        tabFolders = root.findViewById(app.organicmaps.R.id.tab_folders);
        tabPlaylists = root.findViewById(app.organicmaps.R.id.tab_playlists);
        tabBtnSearch = root.findViewById(app.organicmaps.R.id.tab_btn_search);
        tabBtnMenu = root.findViewById(app.organicmaps.R.id.tab_btn_menu);

        folderHeaderContainer = root.findViewById(app.organicmaps.R.id.folder_header_container);
        btnBackFolder = root.findViewById(app.organicmaps.R.id.btn_back_folder);
        folderHeaderTitle = root.findViewById(app.organicmaps.R.id.folder_header_title);


        // Marquee
        if (nowPlayingTitle != null)
            nowPlayingTitle.setSelected(true);
        if (nowPlayingArtist != null)
            nowPlayingArtist.setSelected(true);

        setupListeners();
        setupRecyclerView();
        updateModeUI();
        switchViewMode(currentViewMode);

        // Handle Orientation (Turkce karakter yok)
        boolean isPortrait = getResources()
                .getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        applyOrientationLayout(root, isPortrait);

        // Set Icons
        if (btnPlay != null) btnPlay.setImageResource(app.organicmaps.R.drawable.ic_music_play);
        if (btnNext != null) btnNext.setImageResource(app.organicmaps.R.drawable.ic_music_next);
        if (btnPrev != null) btnPrev.setImageResource(app.organicmaps.R.drawable.ic_music_prev);
        if (btnShuffle != null) btnShuffle.setImageResource(app.organicmaps.R.drawable.ic_music_shuffle);
        if (btnRepeat != null) btnRepeat.setImageResource(app.organicmaps.R.drawable.ic_music_repeat);
        if (btnClose != null) btnClose.setImageResource(app.organicmaps.R.drawable.ic_music_close);

        // Find Visualizer
        visualizerView = root.findViewById(app.organicmaps.R.id.player_visualizer);
        if (visualizerView != null) {
            visualizerView.setVisualizerContext(false); // Buyuk panel (Large context)
        }

        View btnChangeVisualizer = root.findViewById(app.organicmaps.R.id.btn_change_visualizer);
        if (btnChangeVisualizer != null) {
            btnChangeVisualizer.setOnClickListener(v -> {
                if (visualizerView != null) {
                    visualizerView.cycleVisualizerType();
                }
            });
        }

        // Gesture ve Double Tap Kontrollerinin Tanimlanmasi (Turkce karakter yok)
        View cardAlbumArt = root.findViewById(app.organicmaps.R.id.card_album_art);
        View playerInfoContainer = root.findViewById(app.organicmaps.R.id.player_info_container);

        android.view.GestureDetector gestureDetector = new android.view.GestureDetector(getContext(), new MusicGestureListener());
        View.OnTouchListener touchListener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };

        if (cardAlbumArt != null) {
            cardAlbumArt.setOnTouchListener(touchListener);
        }
        if (playerInfoContainer != null) {
            playerInfoContainer.setOnTouchListener(touchListener);
        }
        if (playerPanel != null) {
            playerPanel.setOnTouchListener(touchListener);
        }
        if (nowPlayingCenterPanel != null) {
            nowPlayingCenterPanel.setOnTouchListener(touchListener);
        }
        if (nowPlayingCenterArt != null) {
            nowPlayingCenterArt.setOnTouchListener(touchListener);
        }


        // Başlangıçta playlist açık olduğundan visualizer gizli olmalı
        if (visualizerView != null) {
            visualizerView.setVisibility(isPlaylistVisible ? View.GONE : View.VISIBLE);
        }

        return root;
    }

    // --- Visualizer (Centralized) ---
    private app.organicmaps.carlauncher.widgets.MusicVisualizerView visualizerView;

    @Override
    public void onFftDataCapture(byte[] fft) {
        if (visualizerView != null) {
            visualizerView.updateVisualizer(fft);
        }

        if (ambianceGlowLayer != null && fft != null && fft.length > 2) {
            float totalMag = 0;
            // İlk düşük frekans bantları (Bass) hesaplanıyor
            int count = Math.min(10, fft.length / 2);
            for (int i = 0; i < count; i++) {
                byte rfk = fft[i * 2];
                totalMag += Math.abs(rfk);
            }
            float avg = totalMag / count;
            // Ortalama şiddeti alpha (0.0 - 0.6) aralığına çeviriyoruz
            float targetAlpha = (avg / 128f) * 0.7f;
            if (targetAlpha > 0.6f) targetAlpha = 0.6f;
            if (targetAlpha < 0.0f) targetAlpha = 0.0f;

            final float alpha = targetAlpha;
            ambianceGlowLayer.post(() -> ambianceGlowLayer.setAlpha(alpha));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (visualizerView != null) {
            visualizerView.reloadSettings();
        }
        if (musicManager != null) {
            musicManager.addListener(this);
            musicManager.addVisualizerListener(this); // Centralized Visualizer
            // Update UI state
            updateModeUI();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (musicManager != null) {
            musicManager.removeListener(this);
            musicManager.removeVisualizerListener(this);
        }
    }

    private void setupListeners() {
        // Dikey Dock Buton Tiklama Dinleyicileri (Turkce karakter yok)
        if (btnDockPlaylist != null) {
            btnDockPlaylist.setOnClickListener(v -> {
                if (trackListPanel != null) {
                    trackListPanel.setVisibility(trackListPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                }
                updateDockButtonsUI();
                updateShuffleAndRepeatVisibility();
            });
        }

        if (btnScanMusic != null) {
            btnScanMusic.setOnClickListener(v -> rescanMusic());
        }
        if (btnTabScan != null) {
            btnTabScan.setOnClickListener(v -> rescanMusic());
        }

        // 4 Ana Sekme Listener'ları (Sıra, Parçalar, Klasörler, Listeler)
        if (tabQueue != null) {
            tabQueue.setOnClickListener(v -> switchViewMode(ViewMode.QUEUE));
        }
        if (tabAllTracks != null) {
            tabAllTracks.setOnClickListener(v -> switchViewMode(ViewMode.ALL_TRACKS));
        }
        if (tabFolders != null) {
            tabFolders.setOnClickListener(v -> switchViewMode(ViewMode.FOLDERS));
        }
        if (tabPlaylists != null) {
            tabPlaylists.setOnClickListener(v -> switchViewMode(ViewMode.PLAYLIST));
        }
        if (tabBtnMenu != null) {
            tabBtnMenu.setOnClickListener(this::showTopOverflowMenu);
        }
        if (btnBackFolder != null) {
            btnBackFolder.setOnClickListener(v -> {
                if (currentViewMode == ViewMode.PLAYLIST) {
                    currentPlaylist = null;
                    showPlaylistsList();
                } else if (currentViewMode == ViewMode.FOLDER_DETAIL || currentFolderLevel == FolderViewLevel.TRACK_LIST) {
                    currentFolderLevel = FolderViewLevel.FOLDER_LIST;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) {
                        folderHeaderTitle.setText(selectedStorageType == MusicRepository.StorageType.USB ? "💾 USB Depolama" : "📱 Dahili Hafıza");
                    }
                    showFolders(musicManager.getRepository().getFoldersByStorage(selectedStorageType));
                } else if (currentFolderLevel == FolderViewLevel.FOLDER_LIST) {
                    currentFolderLevel = FolderViewLevel.STORAGE_ROOT;
                    showStorageRoots();
                } else {
                    switchViewMode(ViewMode.FOLDERS);
                }
            });
        }
        View fragmentView = getView();
        View btnFolderPlayAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_play_all) : null;
        View btnFolderShuffleAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_shuffle_all) : null;

        if (btnFolderPlayAll != null) {
            btnFolderPlayAll.setOnClickListener(v -> {
                Log.d("MusicPlayer", "Folder Play All clicked. Folder: " + (currentFolder != null ? currentFolder.getName() : "null") + ", Track Count: " + (filteredTracks != null ? filteredTracks.size() : 0));
                if (filteredTracks != null && !filteredTracks.isEmpty() && musicManager != null && musicManager.getInternalPlayer() != null) {
                    isShuffleOn = false;
                    if (musicManager != null) musicManager.setShuffleOn(false);
                    playTrackFromCollection(filteredTracks, filteredTracks.get(0));
                    Toast.makeText(getContext(), "▶ " + filteredTracks.size() + " Şarkı Çalınıyor", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnFolderShuffleAll != null) {
            btnFolderShuffleAll.setOnClickListener(v -> {
                Log.d("MusicPlayer", "Folder Shuffle clicked. Folder: " + (currentFolder != null ? currentFolder.getName() : "null") + ", Track Count: " + (filteredTracks != null ? filteredTracks.size() : 0));
                if (filteredTracks != null && !filteredTracks.isEmpty() && musicManager != null && musicManager.getInternalPlayer() != null) {
                    isShuffleOn = true;
                    if (musicManager != null) musicManager.setShuffleOn(true);
                    playTrackFromCollection(filteredTracks, filteredTracks.get(0));
                    Toast.makeText(getContext(), "🔀 " + filteredTracks.size() + " Şarkı Karıştırılıyor", Toast.LENGTH_SHORT).show();
                }
            });
        }


        // Close
        if (btnClose != null)
            btnClose.setOnClickListener(v -> closeFragment());

        // Playback Controls (MusicManager metod isimleri düzeltildi)
        // Playback Controls
        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> {
                if (isExternalMode) {
                    musicManager.togglePlayPause();
                } else {
                    if (musicManager.getInternalPlayer() != null) {
                        musicManager.getInternalPlayer().playPause();
                    }
                }
            });
        }
        if (btnNext != null)
            btnNext.setOnClickListener(v -> musicManager.skipToNext());
        if (btnPrev != null)
            btnPrev.setOnClickListener(v -> musicManager.skipToPrevious());

        // Shuffle (Turkce karakter yok)
        if (btnShuffle != null)
            btnShuffle.setOnClickListener(v -> {
                isShuffleOn = !isShuffleOn;
                if (musicManager != null) {
                    musicManager.setShuffleOn(isShuffleOn);
                }
                updateShuffleUI();
                if (currentViewMode == ViewMode.QUEUE) {
                    switchViewMode(ViewMode.QUEUE);
                }
            });

        // Repeat (Turkce karakter yok)
        if (btnRepeat != null)
            btnRepeat.setOnClickListener(v -> {
                repeatMode = (repeatMode + 1) % 3;
                if (musicManager != null) {
                    musicManager.setRepeatMode(repeatMode);
                }
                updateRepeatUI();
            });

        // App Selector Launch (Icon + Name) - Tiklamada direkt Kaynak Secici ac, uzun basmada da ac
        if (appSelectorLaunch != null) {
            appSelectorLaunch.setOnClickListener(v -> {
                showAppPicker(); // Artik direkt secici aciliyor
            });
            appSelectorLaunch.setOnLongClickListener(v -> {
                // Eger uzun basilirsa, o anki uygulamaya gitme fonksiyonu eklendi
                String preferredPkg = musicManager.getPreferredPackage();
                if (preferredPkg != null && !"usage.internal.player".equals(preferredPkg)) {
                    try {
                        android.content.Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(preferredPkg);
                        if (launchIntent != null) {
                            launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            getContext().startActivity(launchIntent);
                        }
                    } catch (Exception e) {}
                }
                return true;
            });
        }



        // Playlist Toggle (Switch Modes) - btnDockPlaylist uzerinden listeyi gizleme
        if (btnDockPlaylist != null) {
            btnDockPlaylist.setOnClickListener(v -> {
                isPlaylistVisible = !isPlaylistVisible;
                if (trackListPanel != null) trackListPanel.setVisibility(isPlaylistVisible ? View.VISIBLE : View.GONE);
                if (nowPlayingCenterPanel != null) nowPlayingCenterPanel.setVisibility(isPlaylistVisible ? View.GONE : View.VISIBLE);

                if (visualizerView != null && !isExternalMode) {
                    int widthPx = getView() != null ? getView().getWidth() : 1000;
                    float widthDp = widthPx / getResources().getDisplayMetrics().density;
                    visualizerView.setVisibility(isPlaylistVisible || widthDp < 500 ? android.view.View.GONE : android.view.View.VISIBLE);
                }

                updateDockButtonsUI();

                if (playerProgressContainer != null && !isExternalMode) {
                    playerProgressContainer.setVisibility(isPlaylistVisible ? View.GONE : View.VISIBLE);
                }
            });
        }

        if (btnPlaylist != null)
            btnPlaylist.setOnClickListener(v -> {
                isExternalMode = !isExternalMode;
                if (!isExternalMode) {
                    musicManager.setPreferredPackage("usage.internal.player");
                }
                updateModeUI();
            });

        // Equalizer
        if (btnEqualizer != null) {
            btnEqualizer.setOnClickListener(v -> openEqualizer());
        }

        // Seekbar
        if (seekbar != null) {
            seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        if (!isExternalMode && musicManager.getInternalPlayer() != null) {
                            int duration = musicManager.getInternalPlayer().getDuration();
                            int newPos = (int) ((progress / 100f) * duration);
                            musicManager.getInternalPlayer().seekTo(newPos);
                        } else if (isExternalMode) {
                            String pkg = musicManager.getPreferredPackage();
                            if ("com.acloud.stub.localmusic".equals(pkg)) {
                                int duration = musicManager.getXyDuration();
                                int newPos = (int) ((progress / 100f) * duration);
                                musicManager.seekXy(newPos);
                            } else if (musicManager.getActiveExternalController() != null) {
                                android.media.session.MediaController controller = musicManager.getActiveExternalController();
                                android.media.MediaMetadata metadata = controller.getMetadata();
                                if (metadata != null) {
                                    long duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
                                    if (duration > 0) {
                                        long newPos = (long) ((progress / 100f) * duration);
                                        controller.getTransportControls().seekTo(newPos);
                                    }
                                }
                            }
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        // Search
        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterTracks(s.toString());
                    if (searchClearBtn != null) {
                        searchClearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }

            });
        }

        if (searchClearBtn != null) {
            searchClearBtn.setOnClickListener(v -> {
                if (searchInput != null) {
                    searchInput.setText("");
                }
            });
        }

        // Tab Listeners
        if (tabAllTracks != null)
            tabAllTracks.setOnClickListener(v -> switchViewMode(ViewMode.ALL_TRACKS));

        if (tabQueue != null)
            tabQueue.setOnClickListener(v -> switchViewMode(ViewMode.QUEUE));

        if (tabFolders != null)
            tabFolders.setOnClickListener(v -> switchViewMode(ViewMode.FOLDERS));

        if (tabPlaylists != null)
            tabPlaylists.setOnClickListener(v -> switchViewMode(ViewMode.PLAYLIST));


        if (tabBtnSearch != null) {
            tabBtnSearch.setOnClickListener(v -> {
                if (searchBarContainer != null) {
                    if (searchBarContainer.getVisibility() == View.VISIBLE) {
                        searchBarContainer.setVisibility(View.GONE);
                        tabBtnSearch.setColorFilter(0xFF888888);
                        if (searchInput != null) {
                            searchInput.setText(""); // Kapatinca aramayi temizle
                        }
                    } else {
                        searchBarContainer.setVisibility(View.VISIBLE);
                        tabBtnSearch.setColorFilter(0xFF00FFFF);
                        if (searchInput != null) {
                            searchInput.requestFocus();
                            try {
                                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    }
                }
            });
        }
    }

    private void setupRecyclerView() {
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }
    }

    // --- YENI: View Mode ve Navigasyon Yonetimi ---
    private void switchViewMode(ViewMode mode) {
        currentViewMode = mode;
        updateTabUIForMode(mode);

        if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.GONE);
        if (searchBarContainer != null) searchBarContainer.setVisibility(View.GONE);

        switch (mode) {
            case QUEUE:
                if (musicManager != null && musicManager.getInternalPlayer() != null) {
                    showTracks(musicManager.getInternalPlayer().getPlayingQueue());
                }
                break;
            case ALL_TRACKS:
                if (searchBarContainer != null) searchBarContainer.setVisibility(View.VISIBLE);
                showTracks(allTracks);
                break;
            case FOLDERS:
                showStorageRoots();
                break;
            case FOLDER_DETAIL:
                if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                if (folderHeaderTitle != null && currentFolder != null) folderHeaderTitle.setText("📁 " + currentFolder.getName());
                if (currentFolder != null) showTracks(currentFolder.getTracks());
                break;
            case PLAYLIST:
                showPlaylistsList();
                break;
        }
    }


    private void updateTabUIForMode(ViewMode mode) {
        int selectedColor = 0xFF00FFFF; // Turkuaz ana vurgu rengi
        int unselectedColor = 0xFF888888;
        int activeBg = app.organicmaps.R.drawable.bg_tab_active;

        // 1. Reset ALL tabs first to prevent double-highlighting
        if (tabQueue != null) { tabQueue.setTextColor(unselectedColor); tabQueue.setBackgroundResource(0); }
        if (tabAllTracks != null) { tabAllTracks.setTextColor(unselectedColor); tabAllTracks.setBackgroundResource(0); }
        if (tabFolders != null) { tabFolders.setTextColor(unselectedColor); tabFolders.setBackgroundResource(0); }
        if (tabPlaylists != null) { tabPlaylists.setTextColor(unselectedColor); tabPlaylists.setBackgroundResource(0); }

        // 2. Highlight ONLY active tab
        switch (mode) {
            case QUEUE:
                if (tabQueue != null) { tabQueue.setTextColor(selectedColor); tabQueue.setBackgroundResource(activeBg); }
                break;
            case ALL_TRACKS:
                if (tabAllTracks != null) { tabAllTracks.setTextColor(selectedColor); tabAllTracks.setBackgroundResource(activeBg); }
                break;
            case FOLDERS:
            case FOLDER_DETAIL:
                if (tabFolders != null) { tabFolders.setTextColor(selectedColor); tabFolders.setBackgroundResource(activeBg); }
                break;
            case PLAYLIST:
                if (tabPlaylists != null) { tabPlaylists.setTextColor(selectedColor); tabPlaylists.setBackgroundResource(activeBg); }
                break;
        }
    }


    private List<MusicRepository.AudioTrack> getRecentTracks() {
        if (playlistManager == null) return new ArrayList<>();
        List<String> recentPaths = playlistManager.getRecentlyPlayed();
        List<MusicRepository.AudioTrack> recentTracks = new ArrayList<>();
        for (String path : recentPaths) {
            MusicRepository.AudioTrack t = musicManager != null && musicManager.getRepository() != null
                    ? musicManager.getRepository().findTrackPortAgnostic(path) : null;
            if (t != null) {
                recentTracks.add(t);
            }
        }
        return recentTracks;
    }

    private List<MusicRepository.AudioTrack> getFavoriteTracks() {
        List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
        for (String reference : playlistManager.getFavorites()) {
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            if (track != null) {
                favTracks.add(track);
            }
        }
        return favTracks;
    }

    private void showStorageRoots() {
        if (recyclerView == null) return;
        if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
        if (folderHeaderTitle != null) folderHeaderTitle.setText("Klasör Kaynağı Seçin");
        currentFolderLevel = FolderViewLevel.STORAGE_ROOT;

        View fragmentView = getView();
        View btnFolderPlayAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_play_all) : null;
        View btnFolderShuffleAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_shuffle_all) : null;
        if (btnFolderPlayAll != null) btnFolderPlayAll.setVisibility(View.GONE);
        if (btnFolderShuffleAll != null) btnFolderShuffleAll.setVisibility(View.GONE);

        List<StorageRootItem> roots = new ArrayList<>();
        roots.add(new StorageRootItem(MusicRepository.StorageType.INTERNAL, "📱 Dahili Hafıza", "Cihaz üzerindeki müzik klasörleri"));

        List<MusicRepository.AudioFolder> usbFolders = musicManager != null && musicManager.getRepository() != null ?
                musicManager.getRepository().getFoldersByStorage(MusicRepository.StorageType.USB) : new ArrayList<>();
        if (!usbFolders.isEmpty()) {
            roots.add(new StorageRootItem(MusicRepository.StorageType.USB, "💾 USB Depolama", usbFolders.size() + " Klasör Bulundu"));
        } else {
            roots.add(new StorageRootItem(MusicRepository.StorageType.USB, "💾 USB Depolama (Takılı Değil)", "Takılı USB bellek bulunamadı"));
        }

        StorageRootAdapter rootAdapter = new StorageRootAdapter(roots, item -> {
            if (item.type == MusicRepository.StorageType.USB && usbFolders.isEmpty()) {
                Toast.makeText(getContext(), "Takılı USB bellek bulunamadı!", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedStorageType = item.type;
            currentFolderLevel = FolderViewLevel.FOLDER_LIST;
            if (folderHeaderTitle != null) {
                folderHeaderTitle.setText(item.type == MusicRepository.StorageType.USB ? "💾 USB Depolama Klasörleri" : "📱 Dahili Hafıza Klasörleri");
            }
            showFolders(musicManager.getRepository().getFoldersByStorage(item.type));
        });
        recyclerView.setAdapter(rootAdapter);
    }

    private static class StorageRootItem {
        MusicRepository.StorageType type;
        String title;
        String subtitle;
        StorageRootItem(MusicRepository.StorageType type, String title, String subtitle) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private class StorageRootAdapter extends RecyclerView.Adapter<StorageRootAdapter.ViewHolder> {
        private final List<StorageRootItem> items;
        private final StorageRootClickListener listener;

        interface StorageRootClickListener {
            void onClick(StorageRootItem item);
        }

        public StorageRootAdapter(List<StorageRootItem> items, StorageRootClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StorageRootItem item = items.get(position);
            holder.text1.setText(item.title);
            holder.text1.setTextColor(0xFFFFFFFF);
            holder.text1.setTextSize(18);
            holder.text1.setTypeface(null, android.graphics.Typeface.BOLD);
            holder.text2.setText(item.subtitle);
            holder.text2.setTextColor(0xFF888888);
            holder.itemView.setPadding(32, 28, 32, 28);
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    private void showFolders(List<MusicRepository.AudioFolder> folders) {
        View fragmentView = getView();
        View btnFolderPlayAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_play_all) : null;
        View btnFolderShuffleAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_shuffle_all) : null;
        if (btnFolderPlayAll != null) btnFolderPlayAll.setVisibility(View.VISIBLE);
        if (btnFolderShuffleAll != null) btnFolderShuffleAll.setVisibility(View.VISIBLE);

        if (recyclerView != null) {
            FolderAdapter folderAdapter = new FolderAdapter(folders, folder -> {
                currentFolder = folder;
                currentFolderLevel = FolderViewLevel.TRACK_LIST;
                if (getContext() != null) {
                    getContext().getSharedPreferences("music_prefs", Context.MODE_PRIVATE).edit().putString("key_last_folder_path", folder.getPath()).apply();
                }
                switchViewMode(ViewMode.FOLDER_DETAIL);
            });
            recyclerView.setAdapter(folderAdapter);
        }
    }


    private void showTopOverflowMenu(View anchor) {
        if (getContext() == null) return;
        List<String> optionsList = new ArrayList<>();
        optionsList.add("🔀 Akıllı Karıştır (Smart Shuffle)");
        optionsList.add("🔄 Müzikleri Yeniden Tara");
        optionsList.add("🔀 Sıralama: Parça Adına Göre");
        optionsList.add("🎙️ Sıralama: Sanatçıya Göre");
        optionsList.add("💿 Sıralama: Albüme Göre");
        optionsList.add("📅 Sıralama: Son Eklenenler");
        optionsList.add("🔥 Sıralama: En Çok Çalınanlar");

        String[] options = optionsList.toArray(new String[0]);
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Müzik Çalar Seçenekleri")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.contains("Akıllı Karıştır")) {
                        playQuickMix();
                    } else if (selected.contains("Yeniden Tara")) {
                        rescanMusic();
                    } else if (selected.contains("Parça Adına Göre")) {
                        sortFilteredTracks(SortOrder.TITLE);
                    } else if (selected.contains("Sanatçıya Göre")) {
                        sortFilteredTracks(SortOrder.ARTIST);
                    } else if (selected.contains("Albüme Göre")) {
                        sortFilteredTracks(SortOrder.ALBUM);
                    } else if (selected.contains("Son Eklenenler")) {
                        sortFilteredTracks(SortOrder.RECENTLY_ADDED);
                    } else if (selected.contains("En Çok Çalınanlar")) {
                        sortFilteredTracks(SortOrder.MOST_PLAYED);
                    }
                })
                .setNegativeButton("Kapat", null)
                .show();
    }

    private void sortFilteredTracks(SortOrder order) {
        if (filteredTracks == null || filteredTracks.isEmpty()) return;
        switch (order) {
            case TITLE:
                filteredTracks.sort((t1, t2) -> t1.getTitle().compareToIgnoreCase(t2.getTitle()));
                break;
            case ARTIST:
                filteredTracks.sort((t1, t2) -> (t1.getArtist() != null ? t1.getArtist() : "").compareToIgnoreCase(t2.getArtist() != null ? t2.getArtist() : ""));
                break;
            case ALBUM:
                filteredTracks.sort((t1, t2) -> (t1.getAlbum() != null ? t1.getAlbum() : "").compareToIgnoreCase(t2.getAlbum() != null ? t2.getAlbum() : ""));
                break;
            case RECENTLY_ADDED:
                filteredTracks.sort((t1, t2) -> Long.compare(t2.getDateAdded(), t1.getDateAdded()));
                break;
            case MOST_PLAYED:
                filteredTracks.sort((t1, t2) -> Integer.compare(
                        playlistManager.getPlayCount(t2), playlistManager.getPlayCount(t1)));
                break;
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static class PlaylistItem {
        String id;
        String title;
        String subtitle;
        boolean isAction;
        PlaylistManager.Playlist playlist;

        PlaylistItem(String id, String title, String subtitle, boolean isAction) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.isAction = isAction;
        }

        PlaylistItem(String id, String title, String subtitle, boolean isAction, PlaylistManager.Playlist playlist) {
            this(id, title, subtitle, isAction);
            this.playlist = playlist;
        }
    }

    private void showPlaylistsList() {
        if (recyclerView == null) return;
        if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.GONE);

        List<PlaylistItem> items = new ArrayList<>();
        items.add(new PlaylistItem("create_new", "➕ Yeni Çalma Listesi Oluştur", "Yeni liste eklemek için dokunun", true));
        items.add(new PlaylistItem("favorites", "⭐ Favoriler", getFavoriteTracks().size() + " Parça", false));
        items.add(new PlaylistItem("recent", "🕒 Son Çalınanlar", getRecentTracks().size() + " Parça", false));
        items.add(new PlaylistItem("most_played", "🔥 En Çok Çalınanlar", allTracks.size() + " Parça", false));
        items.add(new PlaylistItem("recently_added", "📅 Son Eklenenler", allTracks.size() + " Parça", false));

        if (playlistManager != null) {
            List<PlaylistManager.Playlist> userPlaylists = playlistManager.getAllPlaylists();
            for (PlaylistManager.Playlist p : userPlaylists) {
                items.add(new PlaylistItem("user_playlist:" + p.id, "📜 " + p.name, p.tracks.size() + " Parça", false, p));
            }
        }

        PlaylistsAdapter playlistsAdapter = new PlaylistsAdapter(items, new PlaylistsAdapter.PlaylistClickListener() {
            @Override
            public void onClick(PlaylistItem item) {
                if ("create_new".equals(item.id)) {
                    showCreatePlaylistDialog();
                } else if ("favorites".equals(item.id)) {
                    currentPlaylist = null;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("⭐ Favoriler");
                    showTracks(getFavoriteTracks());
                } else if ("recent".equals(item.id)) {
                    currentPlaylist = null;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("🕒 Son Çalınanlar");
                    showTracks(getRecentTracks());
                } else if ("most_played".equals(item.id)) {
                    currentPlaylist = null;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("🔥 En Çok Çalınanlar");
                    List<MusicRepository.AudioTrack> mostPlayed = new ArrayList<>(allTracks);
                    mostPlayed.sort((t1, t2) -> Integer.compare(
                            playlistManager.getPlayCount(t2), playlistManager.getPlayCount(t1)));
                    showTracks(mostPlayed);
                } else if ("recently_added".equals(item.id)) {
                    currentPlaylist = null;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("📅 Son Eklenenler");
                    List<MusicRepository.AudioTrack> recentlyAdded = new ArrayList<>(allTracks);
                    recentlyAdded.sort((t1, t2) -> Long.compare(t2.getDateAdded(), t1.getDateAdded()));
                    showTracks(recentlyAdded);
                } else if (item.id.startsWith("user_playlist:") && item.playlist != null) {
                    currentPlaylist = item.playlist;
                    if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("📜 " + item.playlist.name);

                    List<MusicRepository.AudioTrack> playlistTracks = new ArrayList<>();
                    for (String path : item.playlist.tracks) {
                        MusicRepository.AudioTrack t = musicManager != null && musicManager.getRepository() != null
                                ? musicManager.getRepository().findTrackPortAgnostic(path) : null;
                        if (t != null) playlistTracks.add(t);
                    }
                    showTracks(playlistTracks);
                }
            }

            @Override
            public void onLongClick(PlaylistItem item) {
                if (!item.isAction) showPlaylistPlaybackMenu(item);
            }

            @Override
            public void onPlay(PlaylistItem item) {
                if (!item.isAction) playCollectionNow(getTracksForPlaylistItem(item));
            }
        });
        recyclerView.setAdapter(playlistsAdapter);
    }

    private void showCreatePlaylistDialog() {
        if (getContext() == null || playlistManager == null) return;
        final EditText input = new EditText(getContext());
        input.setHint("Liste Adı (Örn: Yolculuk)");
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Yeni Çalma Listesi")
                .setView(input)
                .setPositiveButton("Oluştur", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        PlaylistManager.Playlist p = new PlaylistManager.Playlist(name);
                        playlistManager.savePlaylist(p);
                        Toast.makeText(getContext(), "Liste oluşturuldu: " + name, Toast.LENGTH_SHORT).show();
                        showPlaylistsList();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showUserPlaylistOptionsMenu(PlaylistManager.Playlist playlist) {
        if (getContext() == null || playlist == null) return;
        String[] options = {"✏️ Adını Değiştir", "🗑️ Listeyi Sil"};
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(playlist.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        final EditText input = new EditText(getContext());
                        input.setText(playlist.name);
                        new android.app.AlertDialog.Builder(getContext())
                                .setTitle("Liste Adını Değiştir")
                                .setView(input)
                                .setPositiveButton("Kaydet", (d, w) -> {
                                    String name = input.getText().toString().trim();
                                    if (!name.isEmpty()) {
                                        playlist.name = name;
                                        playlistManager.savePlaylist(playlist);
                                        showPlaylistsList();
                                    }
                                })
                                .setNegativeButton("İptal", null)
                                .show();
                    } else if (which == 1) {
                        playlistManager.deletePlaylist(playlist.id);
                        Toast.makeText(getContext(), "Liste silindi: " + playlist.name, Toast.LENGTH_SHORT).show();
                        showPlaylistsList();
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private List<MusicRepository.AudioTrack> getTracksForPlaylistItem(PlaylistItem item) {
        if (item == null) return new ArrayList<>();
        if ("favorites".equals(item.id)) return getFavoriteTracks();
        if ("recent".equals(item.id)) return getRecentTracks();
        if ("most_played".equals(item.id)) {
            List<MusicRepository.AudioTrack> tracks = new ArrayList<>(allTracks);
            tracks.sort((t1, t2) -> Integer.compare(
                    playlistManager.getPlayCount(t2), playlistManager.getPlayCount(t1)));
            return tracks;
        }
        if ("recently_added".equals(item.id)) {
            List<MusicRepository.AudioTrack> tracks = new ArrayList<>(allTracks);
            tracks.sort((t1, t2) -> Long.compare(t2.getDateAdded(), t1.getDateAdded()));
            return tracks;
        }
        List<MusicRepository.AudioTrack> tracks = new ArrayList<>();
        if (item.playlist != null) {
            for (String reference : item.playlist.tracks) {
                MusicRepository.AudioTrack track = findTrackByReference(reference);
                if (track != null) tracks.add(track);
            }
        }
        return tracks;
    }

    private void showPlaylistPlaybackMenu(PlaylistItem item) {
        List<MusicRepository.AudioTrack> tracks = getTracksForPlaylistItem(item);
        showCollectionPlaybackMenu(item.title, tracks,
                item.playlist != null ? () -> showUserPlaylistOptionsMenu(item.playlist) : null);
    }

    private void playCollectionNow(List<MusicRepository.AudioTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            if (getContext() != null) Toast.makeText(getContext(),
                    app.organicmaps.R.string.car_music_list_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        playTrackFromCollection(tracks, tracks.get(0));
    }

    private void showCollectionPlaybackMenu(String title, List<MusicRepository.AudioTrack> tracks,
                                            @Nullable Runnable editAction) {
        if (getContext() == null || musicManager == null
                || musicManager.getInternalPlayer() == null) return;
        List<MusicRepository.AudioTrack> collectionTracks = tracks != null
                ? tracks : java.util.Collections.emptyList();
        List<String> options = new ArrayList<>();
        options.add(getString(app.organicmaps.R.string.car_music_play_list_now));
        options.add(getString(app.organicmaps.R.string.car_music_add_list_to_queue_front));
        options.add(getString(app.organicmaps.R.string.car_music_add_list_to_queue_end));
        if (editAction != null) {
            options.add(getString(app.organicmaps.R.string.car_music_edit_playlist));
        }
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(title)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (collectionTracks.isEmpty() && which < 3) {
                        Toast.makeText(getContext(), app.organicmaps.R.string.car_music_list_empty,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (which == 0) {
                        playCollectionNow(collectionTracks);
                    } else if (which == 1) {
                        musicManager.getInternalPlayer().addTracksToQueue(collectionTracks, true);
                        Toast.makeText(getContext(), getString(
                                app.organicmaps.R.string.car_music_tracks_added_queue_front,
                                collectionTracks.size()), Toast.LENGTH_SHORT).show();
                    } else if (which == 2) {
                        musicManager.getInternalPlayer().addTracksToQueue(collectionTracks, false);
                        Toast.makeText(getContext(), getString(
                                app.organicmaps.R.string.car_music_tracks_added_queue_end,
                                collectionTracks.size()), Toast.LENGTH_SHORT).show();
                    } else if (which == 3 && editAction != null) {
                        editAction.run();
                    }
                })
                .setNegativeButton(app.organicmaps.R.string.car_cancel, null)
                .show();
    }

    private class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {
        private final List<PlaylistItem> items;
        private final PlaylistClickListener listener;

        interface PlaylistClickListener {
            void onClick(PlaylistItem item);
            void onLongClick(PlaylistItem item);
            void onPlay(PlaylistItem item);
        }

        PlaylistsAdapter(List<PlaylistItem> items, PlaylistClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    app.organicmaps.R.layout.item_car_music_playlist, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PlaylistItem item = items.get(position);
            holder.text1.setText(item.title);
            holder.text1.setTextColor(item.isAction ? 0xFF00FFFF : 0xFFFFFFFF);
            holder.text1.setTextSize(18);
            holder.text1.setTypeface(null, item.isAction ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            holder.text2.setText(item.subtitle);
            holder.text2.setTextColor(0xFF888888);
            holder.itemView.setOnClickListener(v -> listener.onClick(item));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onLongClick(item);
                return true;
            });
            holder.menu.setVisibility(item.isAction ? View.INVISIBLE : View.VISIBLE);
            holder.menu.setOnClickListener(item.isAction ? null : v -> listener.onPlay(item));
            holder.menu.setOnLongClickListener(item.isAction ? null : v -> {
                listener.onLongClick(item);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ImageButton menu;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(app.organicmaps.R.id.playlist_item_title);
                text2 = itemView.findViewById(app.organicmaps.R.id.playlist_item_subtitle);
                menu = itemView.findViewById(app.organicmaps.R.id.playlist_item_menu);
            }
        }
    }

    // --- Adaptorler ---
    private class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {
        private final List<MusicRepository.AudioFolder> folders;
        private final FolderClickListener listener;

        public FolderAdapter(List<MusicRepository.AudioFolder> folders, FolderClickListener listener) {
            this.folders = folders;
            this.listener = listener;
        }

        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    app.organicmaps.R.layout.item_car_music_playlist, parent, false);
            return new FolderViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            MusicRepository.AudioFolder folder = folders.get(position);
            String prefix = folder.isUsb() ? "🔌 [USB Bellek] " : "📱 [Dahili Hafıza] ";
            holder.text1.setText(prefix + folder.getName());
            holder.text1.setTextColor(folder.isUsb() ? 0xFF00E5FF : android.graphics.Color.WHITE);
            holder.text1.setTextSize(18);
            holder.text2.setText(folder.getTracks().size() + " Parça • " + folder.getPath());
            holder.text2.setTextColor(android.graphics.Color.GRAY);
            holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
            holder.itemView.setOnLongClickListener(v -> {
                showCollectionPlaybackMenu(folder.getName(), folder.getTracks(), null);
                return true;
            });
            holder.play.setVisibility(View.VISIBLE);
            holder.play.setOnClickListener(v -> playCollectionNow(folder.getTracks()));
            holder.play.setOnLongClickListener(v -> {
                showCollectionPlaybackMenu(folder.getName(), folder.getTracks(), null);
                return true;
            });
        }


        @Override
        public int getItemCount() {
            return folders != null ? folders.size() : 0;
        }

        class FolderViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ImageButton play;
            public FolderViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(app.organicmaps.R.id.playlist_item_title);
                text2 = itemView.findViewById(app.organicmaps.R.id.playlist_item_subtitle);
                play = itemView.findViewById(app.organicmaps.R.id.playlist_item_menu);
            }
        }
    }

    private interface FolderClickListener {
        void onFolderClick(MusicRepository.AudioFolder folder);
    }

    private class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder> {
        private final List<MusicRepository.AudioArtist> artists;
        private final ArtistClickListener listener;

        public ArtistAdapter(List<MusicRepository.AudioArtist> artists, ArtistClickListener listener) {
            this.artists = artists;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ArtistViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
            MusicRepository.AudioArtist artist = artists.get(position);
            holder.text1.setText(artist.getName());
            holder.text1.setTextColor(android.graphics.Color.WHITE);
            holder.text1.setTextSize(18);
            holder.text2.setText(artist.getTracks().size() + " Parca");
            holder.text2.setTextColor(android.graphics.Color.GRAY);
            holder.itemView.setOnClickListener(v -> listener.onArtistClick(artist));
            holder.itemView.setPadding(32, 24, 32, 24);
        }

        @Override
        public int getItemCount() {
            return artists != null ? artists.size() : 0;
        }

        class ArtistViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            public ArtistViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    private interface ArtistClickListener {
        void onArtistClick(MusicRepository.AudioArtist artist);
    }

    private void setupPlaylistSpinner() {
        if (playlistSpinner == null || getContext() == null)
            return;

        List<String> options = new ArrayList<>();
        options.add("Seciniz..."); // 0
        options.add("Favoriler"); // 1

        List<PlaylistManager.Playlist> playlists = playlistManager.getAllPlaylists();
        for (PlaylistManager.Playlist p : playlists) {
            options.add(p.name);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getContext(),
                app.organicmaps.R.layout.item_spinner, options);
        spinnerAdapter.setDropDownViewResource(app.organicmaps.R.layout.item_spinner_dropdown);
        playlistSpinner.setAdapter(spinnerAdapter);

        playlistSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0)
                    return;

                switchViewMode(ViewMode.PLAYLIST);
                if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);

                View fragmentView = getView();
                View btnFolderPlayAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_play_all) : null;
                View btnFolderShuffleAll = fragmentView != null ? fragmentView.findViewById(app.organicmaps.R.id.btn_folder_shuffle_all) : null;
                if (btnFolderPlayAll != null) btnFolderPlayAll.setVisibility(View.VISIBLE);
                if (btnFolderShuffleAll != null) btnFolderShuffleAll.setVisibility(View.VISIBLE);

                if (position == 1) {
                    // Favorites
                    currentPlaylist = null;
                    if (folderHeaderTitle != null) folderHeaderTitle.setText("⭐ Favoriler");
                    List<String> favPaths = playlistManager.getFavorites();
                    List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
                    for (String path : favPaths) {
                        MusicRepository.AudioTrack t = musicManager != null && musicManager.getRepository() != null
                                ? musicManager.getRepository().findTrackPortAgnostic(path) : null;
                        if (t != null) {
                            favTracks.add(t);
                        } else {
                            // Ghost track (Port degismis veya USB sökülmüs)
                            favTracks.add(new MusicRepository.AudioTrack(-1, new java.io.File(path).getName(), "Bilinmeyen", "USB", 0, path, android.net.Uri.EMPTY, android.net.Uri.EMPTY, MusicRepository.StorageType.USB, false));
                        }
                    }
                    if (favTracks.isEmpty()) {
                        Toast.makeText(getContext(), "Favori listeniz boş", Toast.LENGTH_SHORT).show();
                    }
                    showTracks(favTracks);
                } else {
                    // User Playlists
                    int playlistIndex = position - 2;
                    if (playlistIndex >= 0 && playlistIndex < playlists.size()) {
                        PlaylistManager.Playlist pl = playlists.get(playlistIndex);
                        currentPlaylist = pl;
                        if (folderHeaderTitle != null) folderHeaderTitle.setText("📜 Playlist: " + pl.name);
                        List<MusicRepository.AudioTrack> playlistTracks = new ArrayList<>();
                        for (String path : pl.tracks) {
                            MusicRepository.AudioTrack t = musicManager != null && musicManager.getRepository() != null
                                    ? musicManager.getRepository().findTrackPortAgnostic(path) : null;
                            if (t != null) {
                                playlistTracks.add(t);
                            } else {
                                // Ghost track (Port degismis veya USB sökülmüs)
                                playlistTracks.add(new MusicRepository.AudioTrack(-1, new java.io.File(path).getName(), "Bilinmeyen", "USB", 0, path, android.net.Uri.EMPTY, android.net.Uri.EMPTY, MusicRepository.StorageType.USB, false));
                            }
                        }
                        showTracks(playlistTracks);
                    }
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    private enum PlayerPanelSize { COMPACT, STANDARD, WIDE }

    private void updateResponsiveLayout(int widthPx, int heightPx) {
        if (getContext() == null || getView() == null) return;
        float density = getResources().getDisplayMetrics().density;
        View visualizer = getView().findViewById(app.organicmaps.R.id.player_visualizer);
        View bottomArt = getView().findViewById(app.organicmaps.R.id.card_album_art);
        View centerCard = getView().findViewById(app.organicmaps.R.id.now_playing_center_art_card);
        View centerInfo = getView().findViewById(app.organicmaps.R.id.now_playing_center_info);
        TextView eyebrow = getView().findViewById(app.organicmaps.R.id.now_playing_center_eyebrow);
        androidx.constraintlayout.widget.ConstraintLayout centerPanel = getView().findViewById(app.organicmaps.R.id.now_playing_center_panel);
        if (centerPanel == null || centerCard == null || centerInfo == null || visualizer == null) return;

        View bottomBar = getView().findViewById(app.organicmaps.R.id.bottom_playback_bar);
        int panelWidth = centerPanel.getWidth();
        int panelHeight = centerPanel.getHeight();
        if (panelWidth <= 0) {
            panelWidth = Math.max(1, widthPx - (musicSideDock != null ? musicSideDock.getWidth() : 0));
        }
        if (panelHeight <= 0) {
            panelHeight = Math.max(1, heightPx - (bottomBar != null ? bottomBar.getHeight() : 0));
        }

        float panelWidthDp = panelWidth / density;
        float panelHeightDp = panelHeight / density;
        PlayerPanelSize panelSize = panelWidthDp < 460 || panelHeightDp < 360
                ? PlayerPanelSize.COMPACT
                : panelWidthDp < 680 || panelHeightDp < 460
                ? PlayerPanelSize.STANDARD : PlayerPanelSize.WIDE;
        if (visualizerView != null) {
            float reflectionRatio = hasAlbumArtwork ? 0f
                    : panelSize == PlayerPanelSize.WIDE ? 0.24f
                    : panelSize == PlayerPanelSize.STANDARD ? 0.20f : 0.16f;
            visualizerView.setMirrorReflectionRatio(reflectionRatio);
        }

        float artworkWidthFraction = panelSize == PlayerPanelSize.WIDE ? 0.32f
                : panelSize == PlayerPanelSize.STANDARD ? 0.40f : 0.36f;
        float artworkHeightFraction = panelSize == PlayerPanelSize.WIDE ? 0.62f
                : panelSize == PlayerPanelSize.STANDARD ? 0.66f : 0.52f;
        int minArtworkDp = panelSize == PlayerPanelSize.WIDE ? 140
                : panelSize == PlayerPanelSize.STANDARD ? 110 : 88;
        int artworkSize = Math.max(dp(minArtworkDp), Math.round(Math.min(
                panelWidth * artworkWidthFraction, panelHeight * artworkHeightFraction)));
        artworkSize = Math.min(artworkSize, Math.min(panelWidth - dp(24), panelHeight - dp(16)));

        androidx.constraintlayout.widget.ConstraintSet set = new androidx.constraintlayout.widget.ConstraintSet();
        set.clone(centerPanel);
        int infoId = app.organicmaps.R.id.now_playing_center_info;
        int artId = app.organicmaps.R.id.now_playing_center_art_card;
        int visualizerId = app.organicmaps.R.id.player_visualizer;
        set.constrainWidth(artId, artworkSize);
        set.constrainHeight(artId, artworkSize);
        set.setDimensionRatio(artId, null);
        centerCard.setVisibility(hasAlbumArtwork ? View.VISIBLE : View.GONE);
        centerPanel.setBackgroundResource(hasAlbumArtwork ? 0 : app.organicmaps.R.drawable.bg_music_no_art_panel);
        if (bottomArt != null) bottomArt.setVisibility(hasAlbumArtwork && panelSize != PlayerPanelSize.COMPACT ? View.VISIBLE : View.GONE);
        if (eyebrow != null) eyebrow.setVisibility(!hasAlbumArtwork && panelSize != PlayerPanelSize.COMPACT ? View.VISIBLE : View.GONE);
        if (nowPlayingCenterTitle != null) nowPlayingCenterTitle.setTextSize(panelSize == PlayerPanelSize.WIDE ? 24 : panelSize == PlayerPanelSize.STANDARD ? 20 : 18);
        if (nowPlayingCenterArtist != null) nowPlayingCenterArtist.setTextSize(panelSize == PlayerPanelSize.WIDE ? 16 : panelSize == PlayerPanelSize.STANDARD ? 15 : 14);
        centerInfo.setBackgroundResource(hasAlbumArtwork ? app.organicmaps.R.drawable.bg_card_rounded_black : 0);
        int horizontalPadding = dp(panelSize == PlayerPanelSize.COMPACT ? 10 : 16);
        int verticalPadding = hasAlbumArtwork ? dp(6) : dp(panelSize == PlayerPanelSize.WIDE ? 10 : 6);
        centerInfo.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        int startTarget = hasAlbumArtwork ? artId : androidx.constraintlayout.widget.ConstraintSet.PARENT_ID;
        int startSide = hasAlbumArtwork ? androidx.constraintlayout.widget.ConstraintSet.END : androidx.constraintlayout.widget.ConstraintSet.START;
        int sideMargin = dp(panelSize == PlayerPanelSize.WIDE ? 20 : panelSize == PlayerPanelSize.STANDARD ? 14 : 8);
        set.connect(infoId, androidx.constraintlayout.widget.ConstraintSet.START, startTarget, startSide);
        set.connect(infoId, androidx.constraintlayout.widget.ConstraintSet.END,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END);
        set.setMargin(infoId, androidx.constraintlayout.widget.ConstraintSet.START, hasAlbumArtwork ? dp(8) : sideMargin);
        set.setMargin(infoId, androidx.constraintlayout.widget.ConstraintSet.END, sideMargin);
        set.connect(infoId, androidx.constraintlayout.widget.ConstraintSet.TOP,
                hasAlbumArtwork ? artId : androidx.constraintlayout.widget.ConstraintSet.PARENT_ID,
                androidx.constraintlayout.widget.ConstraintSet.TOP);
        set.setMargin(infoId, androidx.constraintlayout.widget.ConstraintSet.TOP,
                hasAlbumArtwork ? dp(4) : dp(panelSize == PlayerPanelSize.WIDE ? 16 : 6));

        set.connect(visualizerId, androidx.constraintlayout.widget.ConstraintSet.START, startTarget, startSide);
        set.connect(visualizerId, androidx.constraintlayout.widget.ConstraintSet.END,
                androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END);
        set.setMargin(visualizerId, androidx.constraintlayout.widget.ConstraintSet.START, hasAlbumArtwork ? dp(8) : sideMargin);
        set.setMargin(visualizerId, androidx.constraintlayout.widget.ConstraintSet.END, sideMargin);
        set.connect(visualizerId, androidx.constraintlayout.widget.ConstraintSet.TOP, infoId,
                androidx.constraintlayout.widget.ConstraintSet.BOTTOM);
        set.setMargin(visualizerId, androidx.constraintlayout.widget.ConstraintSet.TOP, dp(panelSize == PlayerPanelSize.COMPACT ? 4 : 10));
        if (hasAlbumArtwork) {
            set.constrainHeight(visualizerId, 0);
            set.connect(visualizerId, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, artId,
                    androidx.constraintlayout.widget.ConstraintSet.BOTTOM);
        } else {
            int maxVisualizerDp = panelSize == PlayerPanelSize.WIDE ? 240
                    : panelSize == PlayerPanelSize.STANDARD ? 180 : 120;
            int minVisualizerDp = panelSize == PlayerPanelSize.COMPACT ? 72 : 80;
            float heightFraction = panelSize == PlayerPanelSize.WIDE ? 0.42f
                    : panelSize == PlayerPanelSize.STANDARD ? 0.40f : 0.36f;
            int visualizerHeight = Math.max(dp(minVisualizerDp), Math.min(dp(maxVisualizerDp),
                    Math.round(panelHeight * heightFraction)));
            set.constrainHeight(visualizerId, visualizerHeight);
            set.connect(visualizerId, androidx.constraintlayout.widget.ConstraintSet.BOTTOM,
                    androidx.constraintlayout.widget.ConstraintSet.PARENT_ID,
                    androidx.constraintlayout.widget.ConstraintSet.BOTTOM);
            set.setVerticalBias(visualizerId, 0.32f);
        }
        visualizer.setVisibility(!isExternalMode && !isPlaylistVisible ? View.VISIBLE : View.GONE);
        set.applyTo(centerPanel);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setAlbumArtworkVisible(boolean visible) {
        hasAlbumArtwork = visible;
        if (getView() == null || nowPlayingCenterPanel == null) return;
        View artCard = getView().findViewById(app.organicmaps.R.id.now_playing_center_art_card);
        if (artCard != null) artCard.setVisibility(visible ? View.VISIBLE : View.GONE);
        View root = getView();
        root.post(() -> {
            if (getView() == root) updateResponsiveLayout(root.getWidth(), root.getHeight());
        });
    }

    private void updateModeUI() {
        // Dahili moddaysa veya liste bossa (izin varsa yukle, yoksa iste)
        if (allTracks.isEmpty()) {
            checkPermissionsAndLoadTracks();
        }

        // Uygulama ikonunu guncelle
        updateAppIcon();

        // Seekbar gorunurlugu (Opsiyonel: External modda seekbar calismayabilir)
        if (seekbar != null) {
            seekbar.setEnabled(true);
        }

        // Kaynak durumuna gore panellerin gorunurlugunu dinamik yonet
        if (playerPanel != null) {
            playerPanel.setVisibility(View.VISIBLE);
        }

        if (isExternalMode) {
            isPlaylistVisible = false;
            if (trackListPanel != null) trackListPanel.setVisibility(View.GONE);
            if (nowPlayingCenterPanel != null) nowPlayingCenterPanel.setVisibility(View.VISIBLE);
            if (btnDockPlaylist != null) btnDockPlaylist.setColorFilter(0xFFFFFFFF);
            if (playerProgressContainer != null) playerProgressContainer.setVisibility(View.VISIBLE);
        } else {
            if (btnPlay != null) btnPlay.setVisibility(View.VISIBLE);
            if (btnNext != null) btnNext.setVisibility(View.VISIBLE);
            if (btnPrev != null) btnPrev.setVisibility(View.VISIBLE);
            if (playerProgressContainer != null) playerProgressContainer.setVisibility(isPlaylistVisible ? View.GONE : View.VISIBLE);

            if (musicManager != null && musicManager.getInternalPlayer() != null) {
                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                if (current != null) {
                    musicManager.notifyTrackChanged();
                }
                onPlaybackStateChanged(musicManager.getInternalPlayer().isPlaying());
            }
        }

        // Dock butonlarini guncelle
        updateDockButtonsUI();

        // Shuffle ve repeat butonlarinin gorunurlugunu calma listesine gore guncelle (Turkce karakter yok)
        updateShuffleAndRepeatVisibility();

        // Merkezi oynaticidan Shuffle ve Repeat durumunu esitle (Turkce karakter yok)
        if (musicManager != null) {
            isShuffleOn = musicManager.isShuffleOn();
            repeatMode = musicManager.getRepeatMode();
            updateShuffleUI();
            updateRepeatUI();
        }
    }

    private void updateDockButtonsUI() {
        if (btnDockPlaylist == null) return;

        // Calma listesi butonu her zaman aktif kalmalidir
        btnDockPlaylist.setEnabled(true);
        btnDockPlaylist.setAlpha(1.0f);

        if (trackListPanel != null && trackListPanel.getVisibility() == View.VISIBLE) {
            // Panel aciksa buton cyan rengine boyanir ve yari saydam beyaz arka plan verilir
            btnDockPlaylist.setColorFilter(0xFF00FFFF);
            btnDockPlaylist.setBackgroundResource(app.organicmaps.R.drawable.bg_circle_translucent_white);
        } else {
            // Panel kapaliysa buton beyaz renge boyanir ve arka plan temizlenir
            btnDockPlaylist.setColorFilter(0xFFFFFFFF);
            btnDockPlaylist.setBackgroundResource(0);
        }
    }

    private void checkPermissionsAndLoadTracks() {
        if (getContext() == null || mediaPermissionRequestInFlight) return;
        List<String> perms = new ArrayList<>();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             if (getContext().checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                 perms.add(android.Manifest.permission.READ_MEDIA_AUDIO);
             }
        } else {
             if (getContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                 perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
             }
        }

        if (!perms.isEmpty()) {
            mediaPermissionRequestInFlight = true;
            requestPermissions(perms.toArray(new String[0]), 100);
        } else {
            loadAllTracks();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            mediaPermissionRequestInFlight = false;
            boolean mediaGranted = musicManager != null
                    && musicManager.getRepository() != null
                    && musicManager.getRepository().hasMusicReadPermission();
            if (mediaGranted) {
                mediaPermissionNoticeShown = false;
                loadAllTracks();
            } else if (musicManager != null && musicManager.getRepository() != null) {
                mediaPermissionNoticeShown = true;
                musicManager.getRepository().scanMusic(null,
                        MusicRepository.ScanReason.USER_REQUEST);
            }
        }
    }

    private void loadAllTracks() {
        if (musicManager != null && musicManager.getRepository() != null) {
            List<MusicRepository.AudioFolder> folders = musicManager.getRepository().getCachedFolders();
            if (folders == null || folders.isEmpty()) {
                musicManager.getRepository().scanMusic((tracks, f, a) -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    allTracks.clear();
                    for (MusicRepository.AudioFolder folder : f) {
                        allTracks.addAll(folder.getTracks());
                    }
                    if (allTracks.isEmpty()) {
                        Toast.makeText(getContext(), "Cihazda muzik bulunamadi", Toast.LENGTH_SHORT).show();
                    }
                    switchViewMode(currentViewMode);
                });
            } else {
                allTracks.clear();
                for (MusicRepository.AudioFolder folder : folders) {
                    allTracks.addAll(folder.getTracks());
                }
                switchViewMode(currentViewMode);
            }
        }
    }

    private void rescanMusic() {
        if (musicManager == null || musicManager.getRepository() == null) return;
        if (!musicManager.getRepository().hasMusicReadPermission()) {
            checkPermissionsAndLoadTracks();
            return;
        }

        if (btnScanMusic != null) {
            btnScanMusic.setEnabled(false);
            btnScanMusic.setAlpha(0.45f);
        }
        if (btnTabScan != null) {
            btnTabScan.setEnabled(false);
            btnTabScan.setAlpha(0.45f);
        }
        musicManager.getRepository().scanMusic((tracks, folders, artists) -> {
            if (!isAdded() || getView() == null) return;

            allTracks.clear();
            allTracks.addAll(tracks);
            switchViewMode(currentViewMode);

            if (!tracks.isEmpty() && musicManager.getInternalPlayer() != null
                    && musicManager.getInternalPlayer().getCurrentTrack() == null) {
                musicManager.getInternalPlayer().setPlaylist(tracks, 0, false);
            }

            if (btnScanMusic != null) {
                btnScanMusic.setEnabled(true);
                btnScanMusic.setAlpha(1.0f);
            }
            if (btnTabScan != null) {
                btnTabScan.setEnabled(true);
                btnTabScan.setAlpha(1.0f);
            }
        });
    }

    private void showTracks(List<MusicRepository.AudioTrack> tracks) {
        filteredTracks = new ArrayList<>(tracks);
        adapter = new MusicAdapter(filteredTracks, new MusicAdapter.OnTrackClickListener() {
            @Override
            public void onClick(MusicRepository.AudioTrack track) {
                // Switch to internal mode
                if (isExternalMode) {
                    isExternalMode = false;
                    // Harici oynatıcıyı durdur
                    if (musicManager.getActiveExternalController() != null) {
                        try {
                            musicManager.getActiveExternalController().getTransportControls().pause();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    // Force MusicManager to drop external preference so it picks up internal player
                    musicManager.setPreferredPackage("usage.internal.player");

                    updateModeUI();
                }

                // Internal Player Logic
                if (musicManager.getInternalPlayer() == null) {
                    return;
                }

                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                if (MusicTrackIdentity.matches(current, track)) {
                    // Toggle Logic
                    if (musicManager.getInternalPlayer().isPlaying()) {
                        musicManager.getInternalPlayer().pause();
                    } else {
                        musicManager.getInternalPlayer().play();
                    }
                    return;
                }

                // Centralized track playback call
                playTrackFromCollection(filteredTracks, track);
            }

            @Override
            public void onAddClick(MusicRepository.AudioTrack track) {
                showTrackOptionsMenu(track);
            }
        }, playlistManager);

        // Sync Adapter State immediately
        if (!isExternalMode && musicManager != null && musicManager.getInternalPlayer() != null) {
            MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
            String path = current != null ? current.getPath() : null;
            boolean isPlaying = musicManager.getInternalPlayer().isPlaying();
            adapter.updateCurrentTrack(path, isPlaying);
        }

        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }
    }

    private void playTrackFromCollection(List<MusicRepository.AudioTrack> collection, MusicRepository.AudioTrack selectedTrack) {
        if (musicManager == null || musicManager.getInternalPlayer() == null || selectedTrack == null) return;

        if (isExternalMode) {
            isExternalMode = false;
            if (musicManager.getActiveExternalController() != null) {
                try {
                    musicManager.getActiveExternalController().getTransportControls().pause();
                } catch (Exception e) {}
            }
            musicManager.setPreferredPackage("usage.internal.player");
            updateModeUI();
        }

        // Special case: If user is in QUEUE view mode, navigate inside existing queue
        if (currentViewMode == ViewMode.QUEUE) {
            int qIndex = -1;
            List<MusicRepository.AudioTrack> queue = musicManager.getInternalPlayer().getPlayingQueue();
            for (int i = 0; i < queue.size(); i++) {
                if (MusicTrackIdentity.matches(queue.get(i), selectedTrack)) {
                    qIndex = i;
                    break;
                }
            }
            if (qIndex != -1) {
                musicManager.getInternalPlayer().playTrackInQueue(qIndex);
            } else {
                musicManager.getInternalPlayer().playTrackFromCollection(collection, selectedTrack, true);
            }
        } else {
            // Any other list (Tüm Parçalar, Klasörler, Sanatçılar, Favoriler, Son Çalınanlar, Playlistler)
            musicManager.getInternalPlayer().playTrackFromCollection(collection, selectedTrack, true);
        }

        updateAdapterHighlightAndScroll(selectedTrack.getPath());
    }

    private void updateAdapterHighlightAndScroll(String playingTrackPath) {
        if (adapter != null && filteredTracks != null) {
            boolean isPlaying = musicManager != null && musicManager.getInternalPlayer() != null && musicManager.getInternalPlayer().isPlaying();
            adapter.updateCurrentTrack(playingTrackPath, isPlaying);

            if (currentViewMode == ViewMode.QUEUE
                    && playingTrackPath != null && recyclerView != null) {
                for (int i = 0; i < filteredTracks.size(); i++) {
                    if (filteredTracks.get(i).getPath().equals(playingTrackPath)) {
                        final int pos = i;
                        recyclerView.post(() -> recyclerView.smoothScrollToPosition(pos));
                        break;
                    }
                }
            }
        }
    }

    private List<MusicRepository.AudioTrack> shuffleWithFirst(List<MusicRepository.AudioTrack> tracks,
            MusicRepository.AudioTrack first) {
        List<MusicRepository.AudioTrack> result = new ArrayList<>(tracks);
        result.remove(first);
        Collections.shuffle(result);
        result.add(0, first);
        return result;
    }

    private void filterTracks(String query) {
        if (query == null || query.isEmpty()) {
            showTracks(allTracks);
        } else {
            String lower = query.toLowerCase(Locale.getDefault());
            List<MusicRepository.AudioTrack> filtered = new ArrayList<>();
            for (MusicRepository.AudioTrack t : allTracks) {
                if ((t.getTitle() != null && t.getTitle().toLowerCase(Locale.getDefault()).contains(lower)) ||
                        (t.getArtist() != null && t.getArtist().toLowerCase(Locale.getDefault()).contains(lower)) ||
                        (t.getAlbum() != null && t.getAlbum().toLowerCase(Locale.getDefault()).contains(lower))) {
                    filtered.add(t);
                }
            }
            showTracks(filtered);
        }
    }

    // --- Track Options Menu ---

    private void showTrackOptionsMenu(MusicRepository.AudioTrack track) {
        if (getContext() == null || track == null) return;

        boolean isFav = playlistManager.isFavorite(track);
        String favOption = isFav ? "♥ Favorilerden Çıkar" : "♡ Favorilere Ekle";

        List<String> optionsList = new ArrayList<>();
        optionsList.add("⏭ Sonraki Çal (Play Next)");
        optionsList.add("➕ Kuyruğa Ekle (Add to Queue)");
        optionsList.add("📑 Playliste Ekle");
        optionsList.add(favOption);

        if (currentViewMode == ViewMode.QUEUE) {
            optionsList.add("🗑️ Sıradan Çıkar");
        } else if (currentViewMode == ViewMode.PLAYLIST) {
            optionsList.add("🗑️ Listeden Çıkar");
        }

        optionsList.add("ℹ️ Şarkı Bilgisi");

        if (track.isUsb()) {
            optionsList.add("📥 Cihaza Kopyala (Offline Yap)");
        }

        String[] options = optionsList.toArray(new String[0]);

        new android.app.AlertDialog.Builder(getContext())
                .setTitle(track.getTitle() + (track.getArtist() != null && !track.getArtist().isEmpty() ? " - " + track.getArtist() : ""))
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.contains("Şarkı Bilgisi")) {
                        showTrackInfoDialog(track);
                    } else if (selected.contains("Sonraki Çal")) {
                        if (musicManager != null && musicManager.getInternalPlayer() != null) {
                            boolean added = musicManager.getInternalPlayer().playNextInQueue(track);
                            if (added) {
                                Toast.makeText(getContext(), "Sonraki şarkı olarak eklendi: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else if (selected.contains("Kuyruğa Ekle")) {
                        if (musicManager != null && musicManager.getInternalPlayer() != null) {
                            boolean added = musicManager.getInternalPlayer().addToQueue(track);
                            if (added) {
                                Toast.makeText(getContext(), "Kuyruğun sonuna eklendi: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    } else if (selected.contains("Playliste Ekle")) {
                        showAddTrackToPlaylistDialog(track);
                    } else if (selected.contains("Favori")) {
                        if (isFav) {
                            playlistManager.removeFromFavorites(track);
                            Toast.makeText(getContext(), "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show();
                        } else {
                            playlistManager.addToFavorites(track);
                            Toast.makeText(getContext(), "Favorilere eklendi", Toast.LENGTH_SHORT).show();
                        }
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    } else if (selected.contains("Sıradan Çıkar")) {
                        if (musicManager != null && musicManager.getInternalPlayer() != null) {
                            musicManager.getInternalPlayer().removeFromQueue(track);
                            if (currentViewMode == ViewMode.QUEUE) {
                                switchViewMode(ViewMode.QUEUE);
                            }
                        }
                    } else if (selected.contains("Listeden Çıkar")) {
                        if (currentPlaylist != null) {
                            currentPlaylist.tracks.removeIf(reference ->
                                    MusicTrackIdentity.matchesReference(reference, track));
                            playlistManager.savePlaylist(currentPlaylist);
                            Toast.makeText(getContext(), "Listeden çıkarıldı: " + track.getTitle(), Toast.LENGTH_SHORT).show();
                            // Refresh playlist view
                            List<MusicRepository.AudioTrack> playlistTracks = new ArrayList<>();
                            for (String path : currentPlaylist.tracks) {
                                MusicRepository.AudioTrack t = musicManager != null && musicManager.getRepository() != null
                                        ? musicManager.getRepository().findTrackPortAgnostic(path) : null;
                                if (t != null) playlistTracks.add(t);
                            }
                            showTracks(playlistTracks);
                        } else {
                            // Favorites view
                            playlistManager.removeFromFavorites(track);
                            Toast.makeText(getContext(), "Favorilerden çıkarıldı", Toast.LENGTH_SHORT).show();
                            showTracks(getFavoriteTracks());
                        }
                    } else if (selected.contains("Cihaza Kopyala")) {
                        if (musicManager != null && musicManager.getRepository() != null) {
                            Toast.makeText(getContext(), "Cihaz hafızasına kopyalanıyor...", Toast.LENGTH_SHORT).show();
                            musicManager.getRepository().copyTrackToInternalStorage(track, (success, messageOrPath) -> {
                                if (getActivity() == null) return;
                                getActivity().runOnUiThread(() -> {
                                    if (success) {
                                        Toast.makeText(requireContext(), "Kopyalandı: " + track.getTitle(), Toast.LENGTH_LONG).show();
                                        rescanMusic();
                                    } else {
                                        Toast.makeText(requireContext(), "Kopyalama başarısız: " + messageOrPath, Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                        }
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void showTrackInfoDialog(MusicRepository.AudioTrack track) {
        if (getContext() == null || track == null) return;
        StringBuilder info = new StringBuilder();
        info.append("🎵 Başlık: ").append(track.getTitle()).append("\n");
        info.append("🎙️ Sanatçı: ").append(track.getArtist() != null ? track.getArtist() : "Bilinmiyor").append("\n");
        info.append("💿 Albüm: ").append(track.getAlbum() != null ? track.getAlbum() : "Bilinmiyor").append("\n");
        info.append("⏱️ Süre: ").append(formatTime(track.getDuration())).append("\n");
        info.append("💾 Kaynak: ").append(track.isUsb() ? "USB Depolama" : "Dahili Hafıza").append("\n");
        info.append("📁 Yol: ").append(track.getPath());

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Şarkı Bilgisi")
                .setMessage(info.toString())
                .setPositiveButton("Tamam", null)
                .show();
    }

    // --- Akıllı Karıştır (Quick Mix) ---
    private void playQuickMix() {
        List<MusicRepository.AudioTrack> physicalTracks = musicManager != null && musicManager.getRepository() != null ?
                musicManager.getRepository().getCachedTracks() : allTracks;
        if (physicalTracks == null || physicalTracks.isEmpty() || musicManager == null || musicManager.getInternalPlayer() == null) {
            Toast.makeText(getContext(), "Çalınacak müzik bulunamadı!", Toast.LENGTH_SHORT).show();
            return;
        }
        List<MusicRepository.AudioTrack> mix = new ArrayList<>(physicalTracks);
        Collections.shuffle(mix);

        // Karıştırılan listeyi doğrudan ekrandaki RecyclerView üzerinde göster!
        showTracks(mix);

        musicManager.getInternalPlayer().setPlaylist(mix, 0);
        Toast.makeText(getContext(), "⚡ Akıllı Karıştır: " + mix.size() + " Parça Listelendi", Toast.LENGTH_SHORT).show();
    }


    // --- Faz 2: Kıyıda Kalanlar (Unplayed Tracks) ---
    private void loadForgottenTracks() {
        if (allTracks == null || playlistManager == null) return;
        List<String> recentReferences = playlistManager.getRecentlyPlayed();
        List<MusicRepository.AudioTrack> forgotten = new ArrayList<>();

        for (MusicRepository.AudioTrack t : allTracks) {
            boolean played = false;
            for (String reference : recentReferences) {
                if (MusicTrackIdentity.matchesReference(reference, t)) {
                    played = true;
                    break;
                }
            }
            if (!played) {
                forgotten.add(t);
            }
        }

        if (forgotten.isEmpty()) {
            Toast.makeText(getContext(), "Tüm şarkılar dinlenmiş!", Toast.LENGTH_SHORT).show();
            showTracks(allTracks);
        } else {
            Toast.makeText(getContext(), "📻 Kıyıda Kalanlar: " + forgotten.size() + " Parça", Toast.LENGTH_SHORT).show();
            showTracks(forgotten);
        }
    }


    private void showAddTrackToPlaylistDialog(MusicRepository.AudioTrack track) {
        if (getContext() == null)
            return;
        List<PlaylistManager.Playlist> playlists = playlistManager.getAllPlaylists();
        String[] options = new String[playlists.size() + 1];
        options[0] = "+ Yeni Playlist Olustur";
        for (int i = 0; i < playlists.size(); i++) {
            options[i + 1] = playlists.get(i).name;
        }

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Playliste Ekle: " + track.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showCreatePlaylistAndAddTrackDialog(track);
                    } else {
                        PlaylistManager.Playlist p = playlists.get(which - 1);
                        p.tracks.add(track.getMediaId());
                        playlistManager.savePlaylist(p);
                        Toast.makeText(getContext(), "Eklendi: " + p.name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Iptal", null)
                .show();
    }

    private void showCreatePlaylistAndAddTrackDialog(MusicRepository.AudioTrack track) {
        if (getContext() == null)
            return;
        EditText input = new EditText(getContext());
        input.setHint("Playlist adi");
        input.setTextColor(0xFFFFFFFF);
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Yeni Playlist ve Ekle")
                .setView(input)
                .setPositiveButton("Olustur", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        PlaylistManager.Playlist p = new PlaylistManager.Playlist(name);
                        p.tracks.add(track.getMediaId());
                        playlistManager.savePlaylist(p);
                        Toast.makeText(getContext(), "Playlist olusturuldu ve sarki eklendi", Toast.LENGTH_SHORT)
                                .show();
                        setupPlaylistSpinner();
                    }
                })
                .setNegativeButton("Iptal", null).show();
    }

    private void showPlaylistOptionsDialog(PlaylistManager.Playlist playlist) {
        if (getContext() == null)
            return;
        String[] options = { "Cal", "Parcalari Gor/Duzenle", "Sil" };
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(playlist.name)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            playPlaylist(playlist);
                            break;
                        case 1:
                            showPlaylistTracksDialog(playlist);
                            break;
                        case 2:
                            confirmDeletePlaylist(playlist);
                            break;
                    }
                })
                .setNegativeButton("Kapat", null).show();
    }

    private void playPlaylist(PlaylistManager.Playlist playlist) {
        if (playlist.tracks.isEmpty()) {
            Toast.makeText(getContext(), "Playlist bos!", Toast.LENGTH_SHORT).show();
            return;
        }
        List<MusicRepository.AudioTrack> queue = new ArrayList<>();
        for (String reference : playlist.tracks) {
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            if (track != null) {
                queue.add(track);
            }
        }
        if (!queue.isEmpty()) {
            if (isShuffleOn)
                Collections.shuffle(queue);
            musicManager.getInternalPlayer().setPlaylist(queue, 0);
        }
    }

    private void showPlaylistTracksDialog(PlaylistManager.Playlist playlist) {
        if (getContext() == null)
            return;
        String[] options = new String[playlist.tracks.size() + 1];
        options[0] = "+ Parcha Ekle";
        for (int i = 0; i < playlist.tracks.size(); i++) {
            String reference = playlist.tracks.get(i);
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            String name = track != null ? track.getTitle() : reference;
            options[i + 1] = name;
        }
        new android.app.AlertDialog.Builder(getContext())
                .setTitle(playlist.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0)
                        showAddTrackToPlaylistDialog(playlist);
                    else
                        showRemoveTrackDialog(playlist, which - 1);
                })
                .setNegativeButton("Kapat", null).show();
    }

    private void showAddTrackToPlaylistDialog(PlaylistManager.Playlist playlist) {
        if (getContext() == null || allTracks.isEmpty())
            return;
        String[] trackNames = new String[allTracks.size()];
        for (int i = 0; i < allTracks.size(); i++)
            trackNames[i] = allTracks.get(i).getTitle();

        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Parcha Ekle")
                .setItems(trackNames, (dialog, which) -> {
                    MusicRepository.AudioTrack track = allTracks.get(which);
                    boolean exists = false;
                    for (String reference : playlist.tracks) {
                        if (MusicTrackIdentity.matchesReference(reference, track)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        playlist.tracks.add(track.getMediaId());
                        playlistManager.savePlaylist(playlist);
                        Toast.makeText(getContext(), "Eklendi!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Iptal", null).show();
    }

    private void showRemoveTrackDialog(PlaylistManager.Playlist playlist, int index) {
        if (getContext() == null)
            return;
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Kaldir?")
                .setPositiveButton("Kaldir", (dialog, which) -> {
                    playlist.tracks.remove(index);
                    playlistManager.savePlaylist(playlist);
                    showPlaylistTracksDialog(playlist);
                })
                .setNegativeButton("Iptal", null).show();
    }

    private void confirmDeletePlaylist(PlaylistManager.Playlist playlist) {
        if (getContext() == null)
            return;
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Sil?")
                .setPositiveButton("Sil", (dialog, which) -> {
                    playlistManager.deletePlaylist(playlist.id);
                    Toast.makeText(getContext(), "Silindi!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Iptal", null).show();
    }

    private void openEqualizer() {
        if (getContext() == null)
            return;
        // Evrensel EQ Kontrolu (Turkce karakter yok)
        try {
            app.organicmaps.carlauncher.hardware.CarHardwareManager.getInstance(getContext())
                .openEqualizer(getContext());
        } catch (Exception e) {
            Toast.makeText(getContext(), "Equalizer acilamadi", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRecentlyPlayed() {

        List<String> recentPaths = playlistManager.getRecentlyPlayed();
        List<MusicRepository.AudioTrack> recentTracks = new ArrayList<>();
        for (String reference : recentPaths) {
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            if (track != null) {
                recentTracks.add(track);
            }
        }
        showTracks(recentTracks);
    }

    private void loadFavorites() {
        if (playlistManager == null) return;
        List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
        for (String reference : playlistManager.getFavorites()) {
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            if (track != null) {
                favTracks.add(track);
            }
        }
        showTracks(favTracks);
    }

    private List<MusicRepository.AudioTrack> getPlaylistTracks(PlaylistManager.Playlist playlist) {
        List<MusicRepository.AudioTrack> result = new ArrayList<>();
        for (String reference : playlist.tracks) {
            MusicRepository.AudioTrack track = findTrackByReference(reference);
            if (track != null) {
                result.add(track);
            }
        }
        return result;
    }

    @Nullable
    private MusicRepository.AudioTrack findTrackByReference(@Nullable String reference) {
        if (reference == null || musicManager == null || musicManager.getRepository() == null) {
            return null;
        }
        return musicManager.getRepository().findTrackByReference(reference);
    }

    private void updateAppIcon() {
        if (appIcon == null || getContext() == null)
            return;
        String pkg = musicManager.getPreferredPackage();
        try {
            PackageManager pm = getContext().getPackageManager();
            Drawable icon = (pkg != null) ? pm.getApplicationIcon(pkg) : null;

            if (icon != null)
                appIcon.setImageDrawable(icon);
            else
                appIcon.setImageResource(app.organicmaps.R.drawable.ic_music_play);

        } catch (Exception e) {
            appIcon.setImageResource(app.organicmaps.R.drawable.ic_music_play);
        }
    }

    private void updateShuffleUI() {
        if (btnShuffle != null) {
            btnShuffle.setColorFilter(isShuffleOn ? 0xFF00FFFF : 0xFF666666);
        }
    }

    private void updateRepeatUI() {
        if (btnRepeat == null)
            return;
        switch (repeatMode) {
            case 0:
                btnRepeat.setColorFilter(0xFF666666);
                btnRepeat.setImageResource(app.organicmaps.R.drawable.ic_music_repeat);
                break;
            case 1:
                btnRepeat.setColorFilter(0xFF00FFFF);
                btnRepeat.setImageResource(app.organicmaps.R.drawable.ic_music_repeat_one);
                break; // Repeat One
            case 2:
                btnRepeat.setColorFilter(0xFF00FF00);
                btnRepeat.setImageResource(app.organicmaps.R.drawable.ic_music_repeat);
                break; // Repeat All
        }
    }

    // Shuffle ve repeat butonlarinin her zaman gorunur olmasi saglanir
    private void updateShuffleAndRepeatVisibility() {
        if (btnShuffle == null || btnRepeat == null) return;
        btnShuffle.setVisibility(View.VISIBLE);
        btnRepeat.setVisibility(View.VISIBLE);
    }

    private void closeFragment() {
        if (getActivity() instanceof CarLauncherInterface) {
            ((CarLauncherInterface) getActivity()).closeAppDrawer();
        } else if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction().remove(this).commit();
        }
    }
     private void updatePlaylistButtonUI() { }

    // --- Lifecycle & Seek Updater (BİRLEŞTİRİLMİŞ) ---

    @Override
    public void onStart() {
        super.onStart();
        startSeekbarUpdater();
        if (musicManager != null && musicManager.getRepository() != null) {
            musicManager.getRepository().addScanStateListener(scanStateListener);
        }
    }

    @Override
    public void onStop() {
        if (musicManager != null && musicManager.getRepository() != null) {
            musicManager.getRepository().removeScanStateListener(scanStateListener);
        }
        super.onStop();
        stopSeekbarUpdater();
    }

    private void handleMusicScanState(MusicRepository.ScanState state) {
        if (!isAdded() || state == null) return;
        if (!state.scanning && (state.reason == MusicRepository.ScanReason.USB_MOUNTED
                || state.reason == MusicRepository.ScanReason.USB_REMOVED)) {
            allTracks.clear();
            allTracks.addAll(musicManager.getRepository().getCachedTracks());
            switchViewMode(currentViewMode);
        }
    }

    @Override
    public void onDestroyView() {
        stopSeekbarUpdater();
        if (musicManager != null) {
            musicManager.removeListener(this);
            musicManager.removeVisualizerListener(this);
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        adapter = null;
        recyclerView = null;
        super.onDestroyView();
    }

    private void startSeekbarUpdater() {
        stopSeekbarUpdater(); // Varsa eskisini durdur
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                updateSeekbar();
                seekHandler.postDelayed(this, 1000); // 1 saniyede bir güncelle
            }
        };
        seekHandler.post(seekRunnable);
    }

    private void stopSeekbarUpdater() {
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
            seekRunnable = null;
        }
    }

    private void updateSeekbar() {
        if (musicManager == null) return;

        long current = 0;
        long duration = 0;

        if (!isExternalMode) {
            // Dahili Calar
            if (musicManager.getInternalPlayer() != null) {
                current = musicManager.getInternalPlayer().getCurrentPosition();
                duration = musicManager.getInternalPlayer().getDuration();
            }
        } else {
            // Harici Calar Modu
            String pkg = musicManager.getPreferredPackage();
            if ("com.acloud.stub.localmusic".equals(pkg)) {
                // XYAuto Yerel Muzik AIDL baglantisi
                current = musicManager.getXyPosition();
                duration = musicManager.getXyDuration();
            } else if (musicManager.getActiveExternalController() != null) {
                // Standart MediaController (Spotify, Youtube vb.)
                android.media.session.MediaController controller = musicManager.getActiveExternalController();
                android.media.MediaMetadata metadata = controller.getMetadata();
                android.media.session.PlaybackState state = controller.getPlaybackState();

                if (metadata != null) {
                    duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION);
                }

                if (state != null) {
                    current = state.getPosition();
                    if (state.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                        long timeDiff = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
                        if (timeDiff > 0) {
                            current += (long) (timeDiff * state.getPlaybackSpeed());
                        }
                    }
                }
            }
        }

        // Sure sinirlamalarini denetle
        if (current < 0) current = 0;
        if (duration < 0) duration = 0;
        if (current > duration) current = duration;

        if (duration > 0 && seekbar != null && !seekbar.isPressed()) {
            int progress = (int) ((current * 100) / duration);
            seekbar.setProgress(progress);
        }

        if (timeCurrent != null)
            timeCurrent.setText(formatTime(current));
        if (timeTotal != null)
            timeTotal.setText(formatTime(duration));
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds);
    }

    // --- MusicUIListener ---

    @Override
    public void onTrackChanged(String title, String artist, Bitmap albumArt, String packageName) {
        String lookupKey = (title == null ? "" : title) + "\n" + (artist == null ? "" : artist);
        artworkLookupKey = albumArt == null ? lookupKey : null;
        // Dynamic Color Logic
        int color = android.graphics.Color.WHITE;
        if (albumArt != null) {
            color = getDominantColor(albumArt);
        }

        final int finalColor = color;
        if (visualizerView != null) {
            app.organicmaps.carlauncher.CarLauncherSettings clSettings =
                    new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
            // Ambians gorsellestirici ayarina gore dinamik renk veya varsayilan (0) secimi (Turkce karakter yok)
            final int visualizerColor = (albumArt != null && clSettings.isAmbianceVisualizerEnabled()) ? finalColor : 0;
            // Lambda gecikmeli calisir, bu surede view detach/null olabilir. Null kontrolu zorunlu.
            visualizerView.post(() -> {
                if (visualizerView != null) {
                    visualizerView.setDominantColor(visualizerColor);
                }
            });
            if (ambianceGlowLayer != null) {
                // Sadece RGB kısmını al, Alpha kısmını arkaplan için tamamen kapat
                int glowColor = (0xFFFFFF & visualizerColor) | 0xFF000000;
                if (visualizerColor == 0) glowColor = 0xFF000000; // Eger ambians kapaliysa siyah yap
                final int fglow = glowColor;
                // Post gecikmeli calisir, lambda tetiklendiginde view null olabilir. Null kontrolu zorunlu.
                ambianceGlowLayer.post(() -> {
                    if (ambianceGlowLayer != null) {
                        ambianceGlowLayer.setBackgroundColor(fglow);
                    }
                });
            }
        }


        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            setAlbumArtworkVisible(albumArt != null);
            if (nowPlayingTitle != null)
                nowPlayingTitle.setText(title != null ? title : "Muzik Secin");
            if (nowPlayingCenterTitle != null)
                nowPlayingCenterTitle.setText(title != null ? title : "Car Launcher Ses Sistemi");

            if (nowPlayingArtist != null) {
                String artistText = artist;
                if (artistText != null) {
                    String clean = artistText.trim().toLowerCase(java.util.Locale.ROOT);
                    if (clean.equals("unknown") || clean.equals("<unknown>") || clean.equals("bilinmeyen") || clean.isEmpty()) {
                        artistText = "";
                    }
                } else {
                    artistText = "";
                }
                nowPlayingArtist.setText(artistText);
                if (nowPlayingCenterArtist != null) {
                    nowPlayingCenterArtist.setText(artistText);
                }
            }

            if (nowPlayingArt != null) {
                if (albumArt != null) {
                    nowPlayingArt.setPadding(0, 0, 0, 0);
                    nowPlayingArt.setImageBitmap(albumArt);
                    nowPlayingArt.setColorFilter(null);
                    if (nowPlayingCenterArt != null) {
                        nowPlayingCenterArt.setImageBitmap(albumArt);
                        nowPlayingCenterArt.setColorFilter(null);
                        nowPlayingCenterArt.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    }
                    if (nowPlayingArtBlur != null) {
                        nowPlayingArtBlur.setVisibility(android.view.View.VISIBLE);
                        nowPlayingArtBlur.setImageBitmap(albumArt);
                    }

                    // Dinamik renk analizi ve yumusak renk gecisi (Turkce karakter yok)
                    int dominantColor = getDominantColor(albumArt);
                    int visibleColor = ensureVisibleColor(dominantColor);
                    animateThemeColorChange(visibleColor);
                } else {
                    int p = (int) (8 * getContext().getResources().getDisplayMetrics().density);
                    nowPlayingArt.setPadding(p, p, p, p);
                    nowPlayingArt.setImageResource(app.organicmaps.R.drawable.ic_default_album_art);
                    nowPlayingArt.setColorFilter(0x88FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);

                    if (nowPlayingCenterArt != null) {
                        nowPlayingCenterArt.setImageResource(app.organicmaps.R.drawable.ic_default_album_art);
                        nowPlayingCenterArt.setColorFilter(0x88FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
                        nowPlayingCenterArt.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    }

                    if (nowPlayingArtBlur != null) {
                        nowPlayingArtBlur.setVisibility(android.view.View.GONE);
                    }

                    // Varsayilan Cyan temaya yumusak gecis (Turkce karakter yok)
                    animateThemeColorChange(0xFF00FFFF);
                }
            }

            if (albumArt == null && getContext() != null) {
                MusicRepository.AudioTrack current = "usage.internal.player".equals(packageName) && musicManager != null
                        && musicManager.getInternalPlayer() != null
                        ? musicManager.getInternalPlayer().getCurrentTrack() : null;
                android.net.Uri localArtUri = current != null ? current.getAlbumArtUri() : null;
                LocalAlbumArtLoader.getInstance(getContext()).load(localArtUri, localArt -> {
                    if (!lookupKey.equals(artworkLookupKey) || !isAdded()) return;
                    if (localArt != null) {
                        onTrackChanged(title, artist, localArt, packageName);
                    } else {
                        OnlineAlbumArtLoader.getInstance(requireContext()).load(title, artist, downloaded -> {
                            if (downloaded != null && lookupKey.equals(artworkLookupKey) && isAdded()) {
                                onTrackChanged(title, artist, downloaded, packageName);
                            }
                        });
                    }
                });
            }

            // Update Adapter Highlight & Auto-scroll
            if (!isExternalMode && musicManager != null && musicManager.getInternalPlayer() != null) {
                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                String path = current != null ? current.getPath() : null;
                boolean isPlaying = musicManager.getInternalPlayer().isPlaying();
                if (adapter != null) {
                    adapter.updateCurrentTrack(path, isPlaying);
                    if (currentViewMode == ViewMode.QUEUE
                            && path != null && filteredTracks != null && recyclerView != null) {
                        for (int i = 0; i < filteredTracks.size(); i++) {
                            if (path.equals(filteredTracks.get(i).getPath())) {
                                final int targetIndex = i;
                                recyclerView.post(() -> recyclerView.smoothScrollToPosition(targetIndex));
                                break;
                            }
                        }
                    }
                }
            }



        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
            if (btnPlay != null) {
                btnPlay.setImageResource(
                        isPlaying ? app.organicmaps.R.drawable.ic_music_pause : app.organicmaps.R.drawable.ic_music_play);
                btnPlay.setColorFilter(0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            if (!isPlaying && visualizerView != null) {
                visualizerView.clear();
            }

            // Update Adapter Icon
            if (!isExternalMode && musicManager.getInternalPlayer() != null) {
                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                String path = current != null ? current.getPath() : null;
                if (adapter != null) {
                    adapter.updateCurrentTrack(path, isPlaying);
                }
            }

            // Visualizer Control
            // Centralized in MusicManager
        });
    }

    @Override
    public void onSourceChanged(boolean isInternal) {
        if (getActivity() == null)
            return;
        boolean newMode = !isInternal;
        if (isExternalMode != newMode) {
            isExternalMode = newMode;
            getActivity().runOnUiThread(this::updateModeUI);
        }
    }

    // --- Adapter ---

    private static class MusicAdapter extends RecyclerView.Adapter<MusicAdapter.Holder> {
        private final List<MusicRepository.AudioTrack> tracks;
        private final OnTrackClickListener listener;
        private final app.organicmaps.carlauncher.music.PlaylistManager playlistManager;
        private String currentTrackPath;
        private boolean isPlaying;

        public interface OnTrackClickListener {
            void onClick(MusicRepository.AudioTrack track);

            void onAddClick(MusicRepository.AudioTrack track);
        }

        public MusicAdapter(List<MusicRepository.AudioTrack> tracks, OnTrackClickListener listener,
                app.organicmaps.carlauncher.music.PlaylistManager playlistManager) {
            this.tracks = tracks;
            this.listener = listener;
            this.playlistManager = playlistManager;
        }

        public void updateCurrentTrack(String path, boolean playing) {
            this.currentTrackPath = path;
            this.isPlaying = playing;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(app.organicmaps.R.layout.item_music_track, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MusicRepository.AudioTrack track = tracks.get(position);
            holder.title.setText(track.getTitle());
            String trackArtist = track.getArtist();
            if (trackArtist != null) {
                String cleanArtist = trackArtist.trim().toLowerCase(java.util.Locale.ROOT);
                if (cleanArtist.equals("unknown") || cleanArtist.equals("<unknown>") || cleanArtist.equals("bilinmeyen") || cleanArtist.isEmpty()) {
                    trackArtist = "";
                }
            } else {
                trackArtist = "";
            }
            holder.artist.setText(trackArtist);
            holder.artist.setVisibility(trackArtist.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

            if (holder.trackArt != null) {
                if (track.getAlbumArtUri() != null) {
                    com.squareup.picasso.Picasso.get()
                            .load(track.getAlbumArtUri())
                            .placeholder(app.organicmaps.R.drawable.bg_playlist_default_art)
                            .error(app.organicmaps.R.drawable.bg_playlist_default_art)
                            .into(holder.trackArt);
                } else {
                    holder.trackArt.setImageResource(app.organicmaps.R.drawable.bg_playlist_default_art);
                }
            }

            // Çalan Şarkı Belirteci Mantığı
            boolean isCurrent = track.getPath().equals(currentTrackPath);
            if (holder.trackPlayingIndicator != null) {
                if (isCurrent && isPlaying) {
                    holder.trackPlayingIndicator.setVisibility(View.VISIBLE);
                    if (holder.trackArt != null) holder.trackArt.setColorFilter(0x88000000); // Darken art
                } else {
                    holder.trackPlayingIndicator.setVisibility(View.GONE);
                    if (holder.trackArt != null) holder.trackArt.clearColorFilter();
                }
            }

            // Calan sarkinin arka planini vurgula
            if (holder.icon != null) {
                if (isCurrent) {
                    holder.icon.setVisibility(View.VISIBLE);
                    holder.icon.setImageResource(
                            isPlaying ? app.organicmaps.R.drawable.ic_music_pause : app.organicmaps.R.drawable.ic_music_play);
                } else {
                    holder.icon.setVisibility(View.INVISIBLE);
                    holder.icon.setImageResource(0);
                }
            }

            if (isCurrent) {
                holder.itemView.setBackgroundColor(0x3300FFFF); // Highlight
            } else {
                holder.itemView.setBackgroundResource(android.R.drawable.list_selector_background);
            }

            // Kayıp/Pasif Dosya (Ghost File) veya USB Görselleştirmesi
            boolean isAvailable = track.isAvailable();
            if (!isAvailable) {
                holder.itemView.setAlpha(0.40f); // Soluk/Gri gösterim
                holder.title.setText("[USB Takılı Değil] " + track.getTitle());
            } else {
                holder.itemView.setAlpha(1.0f);
            }

            // Favori ve Oynatma Listesi Mantigi
            if (holder.btnFavorite != null && playlistManager != null) {
                boolean isFav = playlistManager.isFavorite(track);
                holder.btnFavorite.setImageResource(isFav ? android.R.drawable.star_on : android.R.drawable.star_off);

                // Tint: Favori ise Altin, degilse Gri
                if (isFav) {
                    holder.btnFavorite.setColorFilter(0xFFFFD700); // Gold
                } else {
                    holder.btnFavorite.setColorFilter(0xFF888888); // Gray
                }

                // Yildiza kisa basildiginda favori durumunu degistir
                holder.btnFavorite.setOnClickListener(v -> {
                    if (isFav) {
                        playlistManager.removeFromFavorites(track);
                    } else {
                        playlistManager.addToFavorites(track);
                    }
                    notifyItemChanged(position);
                });

                // Yildiza uzun basildiginda veya öğeye uzun basıldığında menüyü aç
                holder.btnFavorite.setOnLongClickListener(v -> {
                    listener.onAddClick(track);
                    return true;
                });
            }

            holder.itemView.setOnClickListener(v -> {
                if (!track.isAvailable()) {
                    Toast.makeText(v.getContext(), "USB Bellek takılı değil! Lütfen USB sürücüsünü bağlayın.", Toast.LENGTH_SHORT).show();
                    return;
                }
                listener.onClick(track);
            });

            holder.itemView.setOnLongClickListener(v -> {
                listener.onAddClick(track);
                return true;
            });
        }


        @Override
        public int getItemCount() {
            return tracks != null ? tracks.size() : 0;
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView title, artist;
            ImageButton btnAdd = null;
            ImageButton btnFavorite;
            ImageView icon = null;
            ImageView trackArt;
            ImageView trackPlayingIndicator;

            public Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(app.organicmaps.R.id.music_title);
                artist = itemView.findViewById(app.organicmaps.R.id.music_artist);
                btnFavorite = itemView.findViewById(app.organicmaps.R.id.btn_favorite);
                trackArt = itemView.findViewById(app.organicmaps.R.id.track_art);
                trackPlayingIndicator = itemView.findViewById(app.organicmaps.R.id.track_playing_indicator);
            }
        }
    }

    // --- Smart Player Selector Dialog (NEW) ---

    private void showAppPicker() {
        if (getContext() == null) return;
        AppPickerDialog dialog = new AppPickerDialog(getContext(), true, (packageName, appName, icon) -> {
            musicManager.setPreferredPackage(packageName);
            musicManager.forceSetActiveController(packageName);

            // Arkaplanda play komutu gonder (Uygulamayi ekranin onune zıplatma)
            if (packageName != null) {
                // Sadece arkaplanda (servis/media controller uzerinden) calismasini tetikliyoruz
                // Teyplerde (XyAuto vs) Broadcast/MediaSession uzerinden arkada calmaya baslar
                musicManager.playFocusedSource();
            }

            updateAppIcon();
        });

        // Aktif paketi dialoga bildir (Highlight icin)
        dialog.setActivePackage(musicManager.getPreferredPackage());
        dialog.show();
    }

    // --- Dinamik Renk Temasi Metotlari (Turkce karakter yok) ---
    private int currentThemeColor = 0xFF00FFFF;

    private int getDominantColor(Bitmap bitmap) {
        if (bitmap == null) return 0xFF00FFFF;

        // Performans icin bitmap'i cok kucuk boyutlara indirip piksellerini analiz edelim (Turkce karakter yok)
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 16, 16, false);
        int width = resized.getWidth();
        int height = resized.getHeight();

        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int color = resized.getPixel(x, y);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;

                // Cok karanlik veya cok parlak olan pikselleri analiz disi birakalim ki (Turkce karakter yok)
                // daha canli ve belirgin bir renk yakalayalim
                int luminance = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                if (luminance > 30 && luminance < 225) {
                    sumR += r;
                    sumG += g;
                    sumB += b;
                    count++;
                }
            }
        }

        resized.recycle();

        if (count == 0) return 0xFF00FFFF;

        int avgR = (int) (sumR / count);
        int avgG = (int) (sumG / count);
        int avgB = (int) (sumB / count);

        return 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
    }

    private int ensureVisibleColor(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        // Eger renk cok karanliksa parlakligini (Value) artiralim (Turkce karakter yok)
        if (hsv[2] < 0.5f) {
            hsv[2] = 0.7f;
        }
        // Doygunlugu (Saturation) da canli gorunmesi icin yuksek tutalim
        if (hsv[1] < 0.4f) {
            hsv[1] = 0.6f;
        }
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void animateThemeColorChange(int targetColor) {
        int startColor = currentThemeColor;
        android.animation.ValueAnimator colorAnimation = android.animation.ValueAnimator.ofObject(
            new android.animation.ArgbEvaluator(), startColor, targetColor);
        colorAnimation.setDuration(400); // 400ms yumusak gecis
        colorAnimation.addUpdateListener(animator -> {
            int animatedColor = (int) animator.getAnimatedValue();
            applyThemeColor(animatedColor);
        });
        colorAnimation.start();
        currentThemeColor = targetColor;
    }

    private void applyThemeColor(int color) {
        int alphaColor = (color & 0x00FFFFFF) | 0x33000000;

        // 1. Play Butonu (bg_circle_cyan)
        if (btnPlay != null) {
            android.graphics.drawable.Drawable bg = btnPlay.getBackground();
            if (bg instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) bg;
                android.graphics.drawable.Drawable mainPart = ld.findDrawableByLayerId(app.organicmaps.R.id.play_button_main);
                android.graphics.drawable.Drawable glowPart = ld.findDrawableByLayerId(app.organicmaps.R.id.play_button_glow);
                if (mainPart != null) {
                    mainPart.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                }
                if (glowPart != null) {
                    glowPart.setColorFilter(alphaColor, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            } else if (bg != null) {
                bg.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            }
            btnPlay.setColorFilter(0xFFFFFFFF);
        }

        // 2. Next ve Prev Butonlari (bg_circle_translucent_white)
        android.view.View[] controlButtons = {btnNext, btnPrev};
        for (android.view.View btn : controlButtons) {
            if (btn instanceof ImageButton) {
                ImageButton ib = (ImageButton) btn;
                ib.setColorFilter(color);
                android.graphics.drawable.Drawable bg = ib.getBackground();
                if (bg instanceof android.graphics.drawable.LayerDrawable) {
                    android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) bg;
                    android.graphics.drawable.Drawable mainPart = ld.findDrawableByLayerId(app.organicmaps.R.id.control_button_main);
                    android.graphics.drawable.Drawable glowPart = ld.findDrawableByLayerId(app.organicmaps.R.id.control_button_glow);
                    if (mainPart != null) {
                        mainPart.setColorFilter(0x66FFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                    if (glowPart != null) {
                        glowPart.setColorFilter(alphaColor, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                } else if (bg != null) {
                    bg.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            }
        }

        // 3. SeekBar (seekbar_progress_glow ve seekbar_thumb_glow)
        if (seekbar != null) {
            android.graphics.drawable.Drawable progressDrawable = seekbar.getProgressDrawable();
            if (progressDrawable instanceof android.graphics.drawable.LayerDrawable) {
                android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) progressDrawable;
                android.graphics.drawable.Drawable progressClip = ld.findDrawableByLayerId(android.R.id.progress);
                if (progressClip instanceof android.graphics.drawable.ClipDrawable) {
                    android.graphics.drawable.Drawable clipChild = ((android.graphics.drawable.ClipDrawable) progressClip).getDrawable();
                    if (clipChild instanceof android.graphics.drawable.LayerDrawable) {
                        android.graphics.drawable.LayerDrawable cld = (android.graphics.drawable.LayerDrawable) clipChild;
                        android.graphics.drawable.Drawable mainProg = cld.findDrawableByLayerId(app.organicmaps.R.id.seekbar_main_progress);
                        android.graphics.drawable.Drawable glowProg = cld.findDrawableByLayerId(app.organicmaps.R.id.seekbar_glow_progress);
                        if (mainProg != null) {
                            mainProg.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                        }
                        if (glowProg != null) {
                            glowProg.setColorFilter(alphaColor, android.graphics.PorterDuff.Mode.SRC_IN);
                        }
                    }
                } else if (progressClip != null) {
                    progressClip.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                }
            } else if (progressDrawable != null) {
                progressDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.graphics.drawable.Drawable thumb = seekbar.getThumb();
                if (thumb instanceof android.graphics.drawable.LayerDrawable) {
                    android.graphics.drawable.LayerDrawable ld = (android.graphics.drawable.LayerDrawable) thumb;
                    android.graphics.drawable.Drawable mainThumb = ld.findDrawableByLayerId(app.organicmaps.R.id.seekbar_thumb_main_layer);
                    android.graphics.drawable.Drawable glowThumb = ld.findDrawableByLayerId(app.organicmaps.R.id.seekbar_thumb_glow_layer);
                    if (mainThumb != null) {
                        mainThumb.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                    if (glowThumb != null) {
                        glowThumb.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
                    }
                } else if (thumb != null) {
                    thumb.setTint(color);
                }
            }
        }
    }

    /**
     * Muzik calar icin kaydirma (Gesture) ve cift tiklama (Double Tap) dinleyicisi (Turkce karakter yok)
     */
    private class MusicGestureListener extends android.view.GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(android.view.MotionEvent e) {
            return true;
        }

        @Override
        public boolean onDoubleTap(android.view.MotionEvent e) {
            if (isExternalMode) {
                musicManager.togglePlayPause();
            } else {
                if (musicManager.getInternalPlayer() != null) {
                    musicManager.getInternalPlayer().playPause();
                }
            }
            return true;
        }

        @Override
        public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Saga kaydirma -> Onceki sarki (Turkce karakter yok)
                        musicManager.skipToPrevious();
                    } else {
                        // Sola kaydirma -> Sonraki sarki (Turkce karakter yok)
                        musicManager.skipToNext();
                    }
                    return true;
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (getContext() != null) {
                        android.media.AudioManager audioManager = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
                        if (audioManager != null) {
                            if (diffY > 0) {
                                // Asagi kaydirma -> Sesi azalt (Turkce karakter yok)
                                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                                    android.media.AudioManager.ADJUST_LOWER,
                                    android.media.AudioManager.FLAG_SHOW_UI);
                            } else {
                                // Yukari kaydirma -> Sesi artir (Turkce karakter yok)
                                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                                    android.media.AudioManager.ADJUST_RAISE,
                                    android.media.AudioManager.FLAG_SHOW_UI);
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean isPortrait = newConfig.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        applyOrientationLayout(getView(), isPortrait);
    }

    private void applyOrientationLayout(View root, boolean isPortrait) {
        if (root == null) return;
        View mainContent = root.findViewById(app.organicmaps.R.id.main_content_container);
        if (mainContent instanceof LinearLayout) {
            ((LinearLayout) mainContent).setOrientation(isPortrait ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

            if (playerPanel != null && trackListPanel != null) {
                LinearLayout.LayoutParams p1 = (LinearLayout.LayoutParams) playerPanel.getLayoutParams();
                LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) trackListPanel.getLayoutParams();

                if (isPortrait) {
                    p1.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    p1.height = 0;
                    p1.weight = 5;
                    p2.width = LinearLayout.LayoutParams.MATCH_PARENT;
                    p2.height = 0;
                    p2.weight = 5;
                } else {
                    p1.width = 0;
                    p1.height = LinearLayout.LayoutParams.MATCH_PARENT;
                    p1.weight = 4;
                    p2.width = 0;
                    p2.height = LinearLayout.LayoutParams.MATCH_PARENT;
                    p2.weight = 6;
                }
                playerPanel.setLayoutParams(p1);
                trackListPanel.setLayoutParams(p2);
            }
        }
    }
}
