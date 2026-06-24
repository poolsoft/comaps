package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import app.organicmaps.carlauncher.music.PlaylistManager;
import app.organicmaps.carlauncher.dock.AppPickerDialog;
import app.organicmaps.activities.MapActivity;

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
    private ImageButton btnDockPlaylist;
    private ImageView appIcon;
    private View appSelectorLaunch;
    private ImageButton btnPlaylist, btnClose, btnEqualizer;
    private ImageView nowPlayingArt;
    private ImageView nowPlayingArtBlur;
    private TextView nowPlayingTitle, nowPlayingArtist;
    private SeekBar seekbar;
    private TextView timeCurrent, timeTotal;
    private ImageButton btnShuffle, btnPrev, btnPlay, btnNext, btnRepeat;
    private Spinner playlistSpinner;
    private EditText searchInput;
    private View searchBarContainer;
    private View searchClearBtn;

    // New Tab Views
    private TextView tabAllTracks, tabRecent, tabPlaylistLabel, appName;
    private View tabPlaylistsContainer;
    private ImageButton tabBtnSearch;
    private TextView tabFavorites;
    
    // YENI CAR RADIO UIs
    private TextView tabFolders;
    private TextView tabArtists;
    private View folderHeaderContainer;
    private ImageButton btnBackFolder;
    private TextView folderHeaderTitle;

    // View Modes
    private enum ViewMode {
        ALL_TRACKS, FOLDERS, ARTISTS, RECENT, FAVORITES, PLAYLIST, FOLDER_DETAIL, ARTIST_DETAIL
    }
    private ViewMode currentViewMode = ViewMode.ALL_TRACKS;
    private MusicRepository.AudioFolder currentFolder = null;
    private MusicRepository.AudioArtist currentArtist = null;

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
    private View ambianceGlowLayer;

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
            @Override
            public void onGlobalLayout() {
                int width = root.getWidth();
                if (width != lastWidth && width > 0) {
                    lastWidth = width;
                    updateResponsiveLayout(width);
                }
            }
        });

        recyclerView = root.findViewById(app.organicmaps.R.id.music_recycler);

        tabAllTracks = root.findViewById(app.organicmaps.R.id.tab_all_tracks);
        tabRecent = root.findViewById(app.organicmaps.R.id.tab_recent);
        tabPlaylistsContainer = root.findViewById(app.organicmaps.R.id.tab_playlists_container);
        tabPlaylistLabel = root.findViewById(app.organicmaps.R.id.tab_playlist_label);
        appName = root.findViewById(app.organicmaps.R.id.app_name);
        tabBtnSearch = root.findViewById(app.organicmaps.R.id.tab_btn_search);
        tabFavorites = root.findViewById(app.organicmaps.R.id.tab_favorites);
        
        tabFolders = root.findViewById(app.organicmaps.R.id.tab_folders);
        tabArtists = root.findViewById(app.organicmaps.R.id.tab_artists);
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


        // BaÅŸlangÄ±Ã§ta playlist aÃ§Ä±k olduÄŸundan visualizer gizli olmalÄ±
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
            // Ä°lk dÃ¼ÅŸÃ¼k frekans bantlarÄ± (Bass) hesaplanÄ±yor
            int count = Math.min(10, fft.length / 2);
            for (int i = 0; i < count; i++) {
                byte rfk = fft[i * 2];
                totalMag += Math.abs(rfk);
            }
            float avg = totalMag / count;
            // Ortalama ÅŸiddeti alpha (0.0 - 0.6) aralÄ±ÄŸÄ±na Ã§eviriyoruz
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

        // Yeni Klasor ve Sanatci tab listenerlari
        if (tabFolders != null) {
            tabFolders.setOnClickListener(v -> switchViewMode(ViewMode.FOLDERS));
        }
        if (tabArtists != null) {
            tabArtists.setOnClickListener(v -> switchViewMode(ViewMode.ARTISTS));
        }
        if (tabAllTracks != null) {
            tabAllTracks.setOnClickListener(v -> switchViewMode(ViewMode.ALL_TRACKS));
        }
        if (btnBackFolder != null) {
            btnBackFolder.setOnClickListener(v -> {
                if (currentViewMode == ViewMode.FOLDER_DETAIL) {
                    switchViewMode(ViewMode.FOLDERS);
                } else if (currentViewMode == ViewMode.ARTIST_DETAIL) {
                    switchViewMode(ViewMode.ARTISTS);
                }
            });
        }

        // Close
        if (btnClose != null)
            btnClose.setOnClickListener(v -> closeFragment());

        // Playback Controls (MusicManager metod isimleri dÃ¼zeltildi)
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
                
                btnDockPlaylist.setColorFilter(isPlaylistVisible ? 0xFFFFFFFF : 0xFF00FFFF);
            });
        }
        
        if (btnPlaylist != null)
            btnPlaylist.setOnClickListener(v -> {
                isExternalMode = !isExternalMode;
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

        // Playlist Spinner
        setupPlaylistSpinner();

        // Tab Listeners
        if (tabAllTracks != null)
            tabAllTracks.setOnClickListener(v -> {
                selectTab(0);
                checkPermissionsAndLoadTracks();
            });

        if (tabRecent != null)
            tabRecent.setOnClickListener(v -> {
                selectTab(1);
                loadRecentlyPlayed();
            });

        if (tabFavorites != null)
            tabFavorites.setOnClickListener(v -> {
                selectTab(2);
                loadFavorites();
            });

        if (tabPlaylistsContainer != null)
            tabPlaylistsContainer.setOnClickListener(v -> {
                selectTab(3);
                if (playlistSpinner != null)
                    playlistSpinner.performClick();
            });

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

    private void selectTab(int index) {
        // 0: All, 1: Recent, 2: Favorites, 3: Playlist (Listeler)
        int selectedColor = 0xFFFFFFFF;
        int unselectedColor = 0xFF888888;

        if (tabAllTracks != null) {
            tabAllTracks.setTextColor(index == 0 ? selectedColor : unselectedColor);
            tabAllTracks.setBackgroundResource(index == 0 ? app.organicmaps.R.drawable.bg_tab_active : 0);
        }
        if (tabRecent != null) {
            tabRecent.setTextColor(index == 1 ? selectedColor : unselectedColor);
            tabRecent.setBackgroundResource(index == 1 ? app.organicmaps.R.drawable.bg_tab_active : 0);
        }
        if (tabFavorites != null) {
            tabFavorites.setTextColor(index == 2 ? selectedColor : unselectedColor);
            tabFavorites.setBackgroundResource(index == 2 ? app.organicmaps.R.drawable.bg_tab_active : 0);
        }
        if (tabPlaylistsContainer != null) {
            tabPlaylistsContainer.setBackgroundResource(index == 3 ? app.organicmaps.R.drawable.bg_tab_active : 0);
        }
        if (tabPlaylistLabel != null) {
            tabPlaylistLabel.setTextColor(index == 3 ? selectedColor : unselectedColor);
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
            case ALL_TRACKS:
                if (searchBarContainer != null) searchBarContainer.setVisibility(View.VISIBLE);
                showTracks(allTracks);
                break;
            case RECENT:
                showTracks(getRecentTracks());
                break;
            case FAVORITES:
                showTracks(getFavoriteTracks());
                break;
            case FOLDERS:
                showFolders(musicManager.getRepository().getCachedFolders());
                break;
            case ARTISTS:
                showArtists(musicManager.getRepository().getCachedArtists());
                break;
            case FOLDER_DETAIL:
                if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                if (folderHeaderTitle != null && currentFolder != null) folderHeaderTitle.setText(currentFolder.getName());
                if (currentFolder != null) showTracks(currentFolder.getTracks());
                break;
            case ARTIST_DETAIL:
                if (folderHeaderContainer != null) folderHeaderContainer.setVisibility(View.VISIBLE);
                if (folderHeaderTitle != null && currentArtist != null) folderHeaderTitle.setText(currentArtist.getName());
                if (currentArtist != null) showTracks(currentArtist.getTracks());
                break;
            case PLAYLIST:
                // Spinner yonetiyor
                break;
        }
    }

    private void updateTabUIForMode(ViewMode mode) {
        int selectedColor = 0xFFFFFFFF;
        int unselectedColor = 0xFF888888;
        int activeBg = app.organicmaps.R.drawable.bg_tab_active;
        
        if (tabAllTracks != null) {
            tabAllTracks.setTextColor(mode == ViewMode.ALL_TRACKS ? selectedColor : unselectedColor);
            tabAllTracks.setBackgroundResource(mode == ViewMode.ALL_TRACKS ? activeBg : 0);
        }
        if (tabFolders != null) {
            tabFolders.setTextColor((mode == ViewMode.FOLDERS || mode == ViewMode.FOLDER_DETAIL) ? selectedColor : unselectedColor);
            tabFolders.setBackgroundResource((mode == ViewMode.FOLDERS || mode == ViewMode.FOLDER_DETAIL) ? activeBg : 0);
        }
        if (tabArtists != null) {
            tabArtists.setTextColor((mode == ViewMode.ARTISTS || mode == ViewMode.ARTIST_DETAIL) ? selectedColor : unselectedColor);
            tabArtists.setBackgroundResource((mode == ViewMode.ARTISTS || mode == ViewMode.ARTIST_DETAIL) ? activeBg : 0);
        }
        if (tabRecent != null) {
            tabRecent.setTextColor(mode == ViewMode.RECENT ? selectedColor : unselectedColor);
            tabRecent.setBackgroundResource(mode == ViewMode.RECENT ? activeBg : 0);
        }
        if (tabFavorites != null) {
            tabFavorites.setTextColor(mode == ViewMode.FAVORITES ? selectedColor : unselectedColor);
            tabFavorites.setBackgroundResource(mode == ViewMode.FAVORITES ? activeBg : 0);
        }
    }

    private List<MusicRepository.AudioTrack> getRecentTracks() {
        return allTracks; // TODO: Implement real recent logic
    }

    private List<MusicRepository.AudioTrack> getFavoriteTracks() {
        List<String> favPaths = playlistManager.getFavorites();
        List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
        for (String path : favPaths) {
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    favTracks.add(t);
                    break;
                }
            }
        }
        return favTracks;
    }

    private void showFolders(List<MusicRepository.AudioFolder> folders) {
        if (recyclerView != null) {
            FolderAdapter folderAdapter = new FolderAdapter(folders, folder -> {
                currentFolder = folder;
                switchViewMode(ViewMode.FOLDER_DETAIL);
            });
            recyclerView.setAdapter(folderAdapter);
        }
    }

    private void showArtists(List<MusicRepository.AudioArtist> artists) {
        if (recyclerView != null) {
            ArtistAdapter artistAdapter = new ArtistAdapter(artists, artist -> {
                currentArtist = artist;
                switchViewMode(ViewMode.ARTIST_DETAIL);
            });
            recyclerView.setAdapter(artistAdapter);
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
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new FolderViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            MusicRepository.AudioFolder folder = folders.get(position);
            holder.text1.setText(folder.getName());
            holder.text1.setTextColor(android.graphics.Color.WHITE);
            holder.text1.setTextSize(18);
            holder.text2.setText(folder.getTracks().size() + " Parca");
            holder.text2.setTextColor(android.graphics.Color.GRAY);
            holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
            holder.itemView.setPadding(32, 24, 32, 24);
        }

        @Override
        public int getItemCount() {
            return folders != null ? folders.size() : 0;
        }

        class FolderViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            public FolderViewHolder(@NonNull View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
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

                selectTab(3);

                if (position == 1) {
                    // Favorites
                    List<String> favPaths = playlistManager.getFavorites();
                    List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
                    for (String path : favPaths) {
                        for (MusicRepository.AudioTrack t : allTracks) {
                            if (t.getPath().equals(path)) {
                                favTracks.add(t);
                                break;
                            }
                        }
                    }
                    if (favTracks.isEmpty()) {
                        Toast.makeText(getContext(), "Favori listeniz bos", Toast.LENGTH_SHORT).show();
                    }
                    showTracks(favTracks);
                } else {
                    // Playlists
                    int playlistIndex = position - 2;
                    if (playlistIndex >= 0 && playlistIndex < playlists.size()) {
                        List<MusicRepository.AudioTrack> playlistTracks = getPlaylistTracks(
                                playlists.get(playlistIndex));
                        showTracks(playlistTracks);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }
    private void updateResponsiveLayout(int widthPx) {
        if (getContext() == null || getView() == null) return;
        float widthDp = widthPx / getResources().getDisplayMetrics().density;
        
        android.view.View visualizer = getView().findViewById(app.organicmaps.R.id.player_visualizer);
        android.view.View bottomArt = getView().findViewById(app.organicmaps.R.id.card_album_art);
        android.view.View centerCard = getView().findViewById(app.organicmaps.R.id.now_playing_center_art_card);
        androidx.constraintlayout.widget.ConstraintLayout centerPanel = getView().findViewById(app.organicmaps.R.id.now_playing_center_panel);
        
        if (centerPanel == null || centerCard == null) return;
        
        if (widthDp < 500) { // Dar Ekran (Split Screen) Modu
            if (visualizer != null) visualizer.setVisibility(android.view.View.GONE);
            if (bottomArt != null) bottomArt.setVisibility(android.view.View.GONE);
            
            androidx.constraintlayout.widget.ConstraintSet set = new androidx.constraintlayout.widget.ConstraintSet();
            set.clone(centerPanel);
            set.constrainPercentWidth(app.organicmaps.R.id.now_playing_center_art_card, 0.70f);
            set.connect(app.organicmaps.R.id.now_playing_center_art_card, androidx.constraintlayout.widget.ConstraintSet.END, androidx.constraintlayout.widget.ConstraintSet.PARENT_ID, androidx.constraintlayout.widget.ConstraintSet.END);
            set.applyTo(centerPanel);
        } else { // Genis Ekran (Normal) Modu
            if (visualizer != null && !isExternalMode && !isPlaylistVisible) visualizer.setVisibility(android.view.View.VISIBLE);
            if (bottomArt != null) bottomArt.setVisibility(android.view.View.VISIBLE);
            
            androidx.constraintlayout.widget.ConstraintSet set = new androidx.constraintlayout.widget.ConstraintSet();
            set.clone(centerPanel);
            set.constrainPercentWidth(app.organicmaps.R.id.now_playing_center_art_card, 0.45f);
            set.clear(app.organicmaps.R.id.now_playing_center_art_card, androidx.constraintlayout.widget.ConstraintSet.END);
            set.applyTo(centerPanel);
        }
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
        } else {
            if (btnPlay != null) btnPlay.setVisibility(View.VISIBLE);
            if (btnNext != null) btnNext.setVisibility(View.VISIBLE);
            if (btnPrev != null) btnPrev.setVisibility(View.VISIBLE);
            
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
        
        if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(android.Manifest.permission.RECORD_AUDIO);
        }

        if (!perms.isEmpty()) {
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAllTracks();
            } else {
                Toast.makeText(getContext(), "Muzik taramak icin izin gerekli!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadAllTracks() {
        if (musicManager != null && musicManager.getRepository() != null) {
            List<MusicRepository.AudioFolder> folders = musicManager.getRepository().getCachedFolders();
            if (folders == null || folders.isEmpty()) {
                musicManager.getRepository().scanMusic((tracks, f, a) -> {
                    allTracks.clear();
                    for (MusicRepository.AudioFolder folder : f) {
                        allTracks.addAll(folder.getTracks());
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (allTracks.isEmpty()) {
                                Toast.makeText(getContext(), "Cihazda muzik bulunamadi", Toast.LENGTH_SHORT).show();
                            }
                            showTracks(allTracks);
                        });
                    }
                });
            } else {
                allTracks.clear();
                for (MusicRepository.AudioFolder folder : folders) {
                    allTracks.addAll(folder.getTracks());
                }
                showTracks(allTracks);
            }
        }
    }

    private void showTracks(List<MusicRepository.AudioTrack> tracks) {
        filteredTracks = new ArrayList<>(tracks);
        adapter = new MusicAdapter(filteredTracks, new MusicAdapter.OnTrackClickListener() {
            @Override
            public void onClick(MusicRepository.AudioTrack track) {
                // Switch to internal mode
                if (isExternalMode) {
                    isExternalMode = false;
                    // Harici oynatÄ±cÄ±yÄ± durdur
                    if (musicManager.getActiveExternalController() != null) {
                        try {
                            musicManager.getActiveExternalController().getTransportControls().pause();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    // Force MusicManager to drop external preference so it picks up internal player
                    musicManager.setPreferredPackage(null);
                    
                    updateModeUI();
                }

                // Internal Player Logic
                if (musicManager.getInternalPlayer() == null) {
                    return;
                }

                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                if (current != null && current.getPath().equals(track.getPath())) {
                    // Toggle Logic
                    if (musicManager.getInternalPlayer().isPlaying()) {
                        musicManager.getInternalPlayer().pause();
                    } else {
                        musicManager.getInternalPlayer().play();
                    }
                    return;
                }

                // Use current list (filteredTracks) as queue!
                int index = filteredTracks.indexOf(track);
                List<MusicRepository.AudioTrack> queue = isShuffleOn ? shuffleWithFirst(filteredTracks, track)
                        : filteredTracks;
                musicManager.getInternalPlayer().setPlaylist(queue, isShuffleOn ? 0 : index);

                // Add to recently played
                playlistManager.addToRecentlyPlayed(track.getPath());
            }

            @Override
            public void onAddClick(MusicRepository.AudioTrack track) {
                showAddTrackToPlaylistDialog(track);
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
                if (t.getTitle().toLowerCase(Locale.getDefault()).contains(lower) ||
                        t.getArtist().toLowerCase(Locale.getDefault()).contains(lower)) {
                    filtered.add(t);
                }
            }
            showTracks(filtered);
        }
    }

    // --- Playlist & Dialog Logic (KÄ±saltÄ±ldÄ±, orijinal mantÄ±k korundu) ---

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
                        p.tracks.add(track.getPath());
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
                        p.tracks.add(track.getPath());
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
        for (String path : playlist.tracks) {
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    queue.add(t);
                    break;
                }
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
            String path = playlist.tracks.get(i);
            String name = path;
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    name = t.getTitle();
                    break;
                }
            }
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
                    String path = allTracks.get(which).getPath();
                    if (!playlist.tracks.contains(path)) {
                        playlist.tracks.add(path);
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
        for (String path : recentPaths) {
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    recentTracks.add(t);
                    break;
                }
            }
        }
        showTracks(recentTracks);
    }

    private void loadFavorites() {
        if (playlistManager == null) return;
        List<String> favPaths = playlistManager.getFavorites();
        List<MusicRepository.AudioTrack> favTracks = new ArrayList<>();
        for (String path : favPaths) {
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    favTracks.add(t);
                    break;
                }
            }
        }
        showTracks(favTracks);
    }

    private List<MusicRepository.AudioTrack> getPlaylistTracks(PlaylistManager.Playlist playlist) {
        List<MusicRepository.AudioTrack> result = new ArrayList<>();
        for (String path : playlist.tracks) {
            for (MusicRepository.AudioTrack t : allTracks) {
                if (t.getPath().equals(path)) {
                    result.add(t);
                    break;
                }
            }
        }
        return result;
    }

    private void updateAppIcon() {
        if (appIcon == null || getContext() == null)
            return;
        String pkg = musicManager.getPreferredPackage();
        try {
            PackageManager pm = getContext().getPackageManager();
            Drawable icon = (pkg != null) ? pm.getApplicationIcon(pkg) : null;
            CharSequence label = (pkg != null) ? pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)) : "Dahili Muzik";

            if (icon != null)
                appIcon.setImageDrawable(icon);
            else
                appIcon.setImageResource(app.organicmaps.R.drawable.ic_music_play);

            if (appName != null) {
                appName.setText(label);
            }
        } catch (Exception e) {
            appIcon.setImageResource(app.organicmaps.R.drawable.ic_music_play);
            if (appName != null)
                appName.setText("Muzik");
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

    // Calma listesi acikken shuffle ve repeat butonlarini gizle, kapaliyken goster (Turkce karakter yok)
    private void updateShuffleAndRepeatVisibility() {
        if (btnShuffle == null || btnRepeat == null) return;
        boolean isListVisible = trackListPanel != null && trackListPanel.getVisibility() == View.VISIBLE;
        int visibility = isListVisible ? View.GONE : View.VISIBLE;
        btnShuffle.setVisibility(visibility);
        btnRepeat.setVisibility(visibility);
    }

    private void closeFragment() {
        if (getActivity() instanceof MapActivity) {
            ((MapActivity) getActivity()).closeAppDrawer();
        } else if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction().remove(this).commit();
        }
    }
     private void updatePlaylistButtonUI() { }

    // --- Lifecycle & Seek Updater (BÄ°RLEÅTÄ°RÄ°LMÄ°Å) ---

    @Override
    public void onStart() {
        super.onStart();
        startSeekbarUpdater();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopSeekbarUpdater();
    }

    private void startSeekbarUpdater() {
        stopSeekbarUpdater(); // Varsa eskisini durdur
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                updateSeekbar();
                seekHandler.postDelayed(this, 1000); // 1 saniyede bir gÃ¼ncelle
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
        // Dynamic Color Logic
        int color = android.graphics.Color.WHITE;
        if (albumArt != null) {
            color = getDominantColor(albumArt);
        }

        final int finalColor = color;
        if (visualizerView != null) {
            app.organicmaps.carlauncher.CarLauncherSettings clSettings = new app.organicmaps.carlauncher.CarLauncherSettings(getContext());
            // Ambians gorsellestirici ayarina gore dinamik renk veya varsayilan (0) secimi (Turkce karakter yok)
            final int visualizerColor = (albumArt != null && clSettings.isAmbianceVisualizerEnabled()) ? finalColor : 0;
            visualizerView.post(() -> visualizerView.setDominantColor(visualizerColor));
            if (ambianceGlowLayer != null) {
                // Sadece RGB kÄ±smÄ±nÄ± al, Alpha kÄ±smÄ±nÄ± arkaplan iÃ§in tamamen kapat
                int glowColor = (0xFFFFFF & visualizerColor) | 0xFF000000;
                if (visualizerColor == 0) glowColor = 0xFF000000; // Eger ambians kapaliysa siyah yap
                final int fglow = glowColor;
                ambianceGlowLayer.post(() -> ambianceGlowLayer.setBackgroundColor(fglow));
            }
        }
        
        if (getActivity() == null)
            return;
        getActivity().runOnUiThread(() -> {
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
                    nowPlayingCenterArtist.setText(artistText.isEmpty() ? "Hazir" : artistText);
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

            // Update Adapter Highlight
            if (!isExternalMode && musicManager.getInternalPlayer() != null) {
                MusicRepository.AudioTrack current = musicManager.getInternalPlayer().getCurrentTrack();
                String path = current != null ? current.getPath() : null;
                boolean isPlaying = musicManager.getInternalPlayer().isPlaying();
                if (adapter != null) {
                    adapter.updateCurrentTrack(path, isPlaying);
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
            holder.artist.setText(track.getArtist());

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

            // Calan sarkinin arka planini vurgula
            boolean isCurrent = track.getPath().equals(currentTrackPath);
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

            // Favori ve Oynatma Listesi Mantigi
            if (holder.btnFavorite != null && playlistManager != null) {
                boolean isFav = playlistManager.isFavorite(track.getPath());
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
                        playlistManager.removeFromFavorites(track.getPath());
                    } else {
                        playlistManager.addToFavorites(track.getPath());
                    }
                    notifyItemChanged(position);
                });

                // Yildiza uzun basildiginda calma listesi ekleme dialogunu ac
                holder.btnFavorite.setOnLongClickListener(v -> {
                    listener.onAddClick(track);
                    return true;
                });
            }

            holder.itemView.setOnClickListener(v -> listener.onClick(track));
            if (holder.btnAdd != null) {
                holder.btnAdd.setOnClickListener(v -> listener.onAddClick(track));
            }
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

            public Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(app.organicmaps.R.id.music_title);
                artist = itemView.findViewById(app.organicmaps.R.id.music_artist);
                btnFavorite = itemView.findViewById(app.organicmaps.R.id.btn_favorite);
                trackArt = itemView.findViewById(app.organicmaps.R.id.track_art);
            }
        }
    }

    // --- Smart Player Selector Dialog (NEW) ---
    
    private void showAppPicker() {
        if (getContext() == null) return;
        AppPickerDialog dialog = new AppPickerDialog(getContext(), true, (packageName, appName, icon) -> {
            musicManager.setPreferredPackage(packageName);
            musicManager.forceSetActiveController(packageName);
            
            // Arkaplanda play komutu gonder (Uygulamayi ekranin onune zÄ±platma)
            if (packageName != null) {
                // Sadece arkaplanda (servis/media controller uzerinden) calismasini tetikliyoruz
                // Teyplerde (XyAuto vs) Broadcast/MediaSession uzerinden arkada calmaya baslar
                musicManager.play();
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
