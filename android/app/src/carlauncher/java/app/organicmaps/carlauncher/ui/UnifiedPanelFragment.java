package app.organicmaps.carlauncher.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import app.organicmaps.R;
import app.organicmaps.carlauncher.CarLauncherInterface;
import app.organicmaps.carlauncher.music.MusicManager;
import app.organicmaps.carlauncher.widgets.MusicVisualizerView;

// Konum, Hiz ve Saat icin eklenen importlar
import android.location.Location;

import app.organicmaps.MwmApplication;


import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Date;

/**
 * Birlesik Muzik ve Arama/Bildirim Paneli.
 * Normal modda sag taraftaki panelde gosterilir.
 */
public class UnifiedPanelFragment extends Fragment 
        implements MusicManager.MusicUIListener, MusicManager.MusicVisualizerListener {

    private MusicManager musicManager;
    private MwmApplication mApplication;

    // Arayuz Elemanlari
    private ImageView albumArtBg;
    private View cardNotification;
    private ImageView notifIcon;
    private TextView notifTitle;
    private TextView notifMessage;
    private ImageButton btnNotifClose;

    // Ust Panel Menu Butonu (Turkce karakter yok)
    private ImageButton panelMenuBtn;



    private View musicArea;
    private float musicTouchDownY;
    private View musicControls;
    private ImageView musicMiniArt;
    private TextView musicPanelClock;
    private TextView musicTrackTitle;
    private TextView musicTrackArtist;
    private MusicVisualizerView musicVisualizer;
    private ImageButton musicBtnPrev;
    private ImageButton musicBtnPlay;
    private ImageButton musicBtnNext;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (musicPanelClock != null) {
                musicPanelClock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(new Date()));
            }
            clockHandler.postDelayed(this, 30_000L);
        }
    };

    // Bildirim Alicisi (BroadcastReceiver)
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            String showAction = context.getPackageName() + ".SHOW_NOTIFICATION";
            String hideAction = context.getPackageName() + ".HIDE_NOTIFICATION";
            if (showAction.equals(action)) {
                String title = intent.getStringExtra("title");
                String message = intent.getStringExtra("message");
                String type = intent.getStringExtra("type"); // "call" veya "notification"

                showNotificationCard(title, message, type);
            } else if (hideAction.equals(action)) {
                hideNotificationCard();
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            musicManager = MusicManager.getInstance(getContext());
            mApplication = (MwmApplication) getContext().getApplicationContext();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_unified_panel, container, false);

        // Arka plan ve genel yapilar
        albumArtBg = root.findViewById(R.id.unified_album_art);
        cardNotification = root.findViewById(R.id.card_notification);
        notifIcon = root.findViewById(R.id.notif_icon);
        notifTitle = root.findViewById(R.id.notif_title);
        notifMessage = root.findViewById(R.id.notif_message);
        btnNotifClose = root.findViewById(R.id.btn_notif_close);

        // Ust panel saat ve hiz gostergeleri
        panelMenuBtn = root.findViewById(R.id.panel_menu_btn);

        // Muzik alani yapilari
        musicArea = root.findViewById(R.id.music_area);
        musicControls = root.findViewById(R.id.music_controls);
        musicMiniArt = root.findViewById(R.id.music_mini_art);
        musicPanelClock = root.findViewById(R.id.music_panel_clock);
        musicTrackTitle = root.findViewById(R.id.music_track_title);
        musicTrackArtist = root.findViewById(R.id.music_track_artist);
        musicVisualizer = root.findViewById(R.id.music_visualizer);
        musicBtnPrev = root.findViewById(R.id.music_btn_prev);
        musicBtnPlay = root.findViewById(R.id.music_btn_play);
        musicBtnNext = root.findViewById(R.id.music_btn_next);

        setupListeners();

        return root;
    }

    private void setupListeners() {
        // Muzik alani tiklandiginda buyuk oynaticiyi ac
        View.OnClickListener openMusicPlayer = v -> {
            if (getActivity() instanceof CarLauncherInterface) {
                ((CarLauncherInterface) getActivity()).openMusicPlayer();
            }
        };
        if (musicArea != null) {
            musicArea.setOnClickListener(null);
            musicArea.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    musicTouchDownY = event.getY();
                    return true;
                }
                if (event.getAction() != android.view.MotionEvent.ACTION_UP) return true;
                int touchSlop = android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                if (Math.abs(event.getY() - musicTouchDownY) > touchSlop) return true;
                if (musicControls != null && event.getY() >= musicControls.getTop()) return true;
                openMusicPlayer.onClick(v);
                return true;
            });
        }
        if (musicPanelClock != null) musicPanelClock.setOnClickListener(openMusicPlayer);
        if (musicMiniArt != null) musicMiniArt.setOnClickListener(openMusicPlayer);
        if (musicTrackTitle != null) musicTrackTitle.setOnClickListener(openMusicPlayer);
        if (musicTrackArtist != null) musicTrackArtist.setOnClickListener(openMusicPlayer);
        if (musicVisualizer != null) musicVisualizer.setOnClickListener(openMusicPlayer);

        // Ayarlari acan 3 nokta butonu
        if (panelMenuBtn != null) {
            panelMenuBtn.setOnClickListener(v -> showPanelPopupMenu(v));
        }

        // Medya oynatma tuslari
        if (musicBtnPlay != null) {
            musicBtnPlay.setOnClickListener(v -> {
                if (musicManager != null) musicManager.togglePlayPause();
            });
        }
        if (musicBtnPrev != null) {
            musicBtnPrev.setOnClickListener(v -> {
                if (musicManager != null) musicManager.skipToPrevious();
            });
        }
        if (musicBtnNext != null) {
            musicBtnNext.setOnClickListener(v -> {
                if (musicManager != null) musicManager.skipToNext();
            });
        }

        // Bildirim kapatma butonu
        if (btnNotifClose != null) {
            btnNotifClose.setOnClickListener(v -> hideNotificationCard());
        }
    }

    private void showPanelPopupMenu(View anchor) {
        if (getContext() == null || getActivity() == null) return;
        
        android.widget.PopupMenu popup = new android.widget.PopupMenu(getContext(), anchor);
        
        popup.getMenu().add(0, 1, 0, getContext().getString(R.string.car_panel_map_mode));
        popup.getMenu().add(0, 2, 1, getContext().getString(R.string.car_panel_desktop_mode));
        popup.getMenu().add(0, 3, 2, getContext().getString(R.string.car_panel_settings));
        popup.getMenu().add(0, 4, 3, getContext().getString(R.string.car_panel_turn_off_screen));
        popup.getMenu().add(0, 5, 4, getContext().getString(R.string.car_panel_clear_ram));
        
        popup.setOnMenuItemClickListener(item -> {
            if (getActivity() instanceof app.organicmaps.carlauncher.CarLauncherActivity) {
                app.organicmaps.carlauncher.CarLauncherActivity activity =
                        (app.organicmaps.carlauncher.CarLauncherActivity) getActivity();
                switch (item.getItemId()) {
                    case 1:
                        activity.onLayoutModeToggle();
                        return true;
                    case 2:
                        activity.onDesktopModeToggle();
                        return true;
                    case 3:
                        activity.openCarLauncherSettings();
                        return true;
                    case 4:
                        app.organicmaps.carlauncher.hardware.CarHardwareManager.getInstance(getContext())
                            .turnOffScreen();
                        return true;
                    case 5:
                        app.organicmaps.carlauncher.hardware.CarHardwareManager.getInstance(getContext())
                            .cleanRam();
                        return true;
                }
            }
            return false;
        });
        
        popup.show();
    }

    private void showNotificationCard(String title, String message, String type) {
        if (cardNotification == null) return;
        
        if (notifTitle != null) notifTitle.setText(title != null ? title : getContext().getString(R.string.car_panel_notification));
        if (notifMessage != null) notifMessage.setText(message != null ? message : "");
        
        if (notifIcon != null) {
            if ("call".equalsIgnoreCase(type)) {
                notifIcon.setImageResource(android.provider.ContactsContract.QuickContact.class != null ? 
                    android.R.drawable.stat_sys_phone_call : android.R.drawable.ic_menu_call);
                if (getContext() != null) {
                    notifIcon.setColorFilter(getContext().getResources().getColor(R.color.cl_primary));
                }
            } else {
                notifIcon.setImageResource(android.R.drawable.stat_notify_chat);
                notifIcon.clearColorFilter();
            }
        }

        cardNotification.setVisibility(View.VISIBLE);
    }

    private void hideNotificationCard() {
        if (cardNotification != null) {
            cardNotification.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        clockHandler.removeCallbacks(clockRunnable);
        clockHandler.post(clockRunnable);
        if (musicManager != null) {
            musicManager.addListener(this);
            musicManager.addVisualizerListener(this);
        }

        // BroadcastReceiver kaydi (Android 14+ icin RECEIVER_EXPORTED zorunlu - Turkce karakter yok)
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(getContext().getPackageName() + ".SHOW_NOTIFICATION");
            filter.addAction(getContext().getPackageName() + ".HIDE_NOTIFICATION");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(notificationReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                getContext().registerReceiver(notificationReceiver, filter);
            }
        }
    }

    @Override
    public void onPause() {
        clockHandler.removeCallbacks(clockRunnable);
        super.onPause();
        if (musicManager != null) {
            musicManager.removeListener(this);
            musicManager.removeVisualizerListener(this);
        }

        // BroadcastReceiver kaydini kaldir (Turkce karakter yok)
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(notificationReceiver);
            } catch (Exception e) {
                // Kayit zaten yoksa hata vermesini onle
            }
        }
    }

    // --- MusicUIListener Geri Bildirimleri ---

    @Override
    public void onTrackChanged(String title, String artist, Bitmap albumArt, String packageName) {
        if (musicTrackTitle != null) musicTrackTitle.setText(title != null ? title : getContext().getString(R.string.car_panel_select_track));
        if (musicTrackArtist != null) musicTrackArtist.setText(artist != null ? artist : "");

        if (albumArt != null) {
            if (albumArtBg != null) albumArtBg.setImageBitmap(albumArt);
            if (musicMiniArt != null) {
                musicMiniArt.setBackground(null);
                musicMiniArt.setPadding(0, 0, 0, 0);
                musicMiniArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                musicMiniArt.setImageBitmap(albumArt);
                musicMiniArt.setVisibility(View.GONE);
            }
        } else {
            if (albumArtBg != null) albumArtBg.setImageResource(R.drawable.bg_default_music_art);
            if (musicMiniArt != null) {
                musicMiniArt.setBackgroundResource(R.drawable.bg_track_art_placeholder);
                if (getContext() != null) {
                    int padding = (int) (12 * getContext().getResources().getDisplayMetrics().density);
                    musicMiniArt.setPadding(padding, padding, padding, padding);
                }
                musicMiniArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                musicMiniArt.setImageResource(R.drawable.ic_default_album_art);
                musicMiniArt.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (musicBtnPlay != null) {
            musicBtnPlay.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        }
    }

    @Override
    public void onSourceChanged(boolean isInternal) {
        // Kaynak degisimi gerekirse burada islenebilir
    }

    // --- MusicVisualizerListener Geri Bildirimleri ---

    @Override
    public void onFftDataCapture(byte[] fft) {
        if (musicVisualizer != null) {
            musicVisualizer.updateVisualizer(fft);
        }
    }

}


