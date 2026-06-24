package app.organicmaps.carlauncher.music;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import com.acloud.stub.service.aidl.IPlayService;

public class XyAutoMusicAdapter implements BaseMediaAdapter {

    private static final String TAG = "XyAutoMusicAdapter";
    private static final String PACKAGE_NAME = "com.acloud.stub.localmusic";

    private final Context context;
    private final MusicManager manager;

    // XYAuto yerel muzik durum degiskenleri
    private String xyTrackTitle = null;
    private String xyTrackArtist = null;
    private String xyTrackAlbumArtPath = null;
    private boolean xyIsPlaying = false;
    private int xyDuration = 0;
    private int xyPosition = 0;

    private IPlayService xyPlayService;
    private boolean xyServiceBound = false;

    private final ServiceConnection xyServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            xyPlayService = IPlayService.Stub.asInterface(service);
            xyServiceBound = true;
            Log.d(TAG, "XYPlayService baglantisi kuruldu.");
            manager.notifyStateChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            xyPlayService = null;
            xyServiceBound = false;
            Log.d(TAG, "XYPlayService baglantisi kesildi.");
            manager.notifyStateChanged();
        }
    };

    private final BroadcastReceiver xyAutoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            Log.d(TAG, "XYAuto yayini alindi: " + action);

            boolean changed = false;

            if ("update.widget.playbtnstate".equals(action)) {
                boolean oldPlaying = xyIsPlaying;
                xyIsPlaying = intent.getBooleanExtra("PlayState", false);
                if (xyIsPlaying != oldPlaying) {
                    changed = true;
                    if (xyIsPlaying) {
                        manager.onExternalPlayerStarted(PACKAGE_NAME);
                    }
                }
            } else if ("update.widget.songname".equals(action)) {
                String fullSongName = intent.getStringExtra("curplaysong");
                String oldTitle = xyTrackTitle;
                String oldArtist = xyTrackArtist;

                if (fullSongName != null) {
                    if (fullSongName.contains(" - ")) {
                        String[] parts = fullSongName.split(" - ", 2);
                        xyTrackTitle = parts[0].trim();
                        xyTrackArtist = parts[1].trim();
                    } else if (fullSongName.contains("-")) {
                        String[] parts = fullSongName.split("-", 2);
                        xyTrackTitle = parts[0].trim();
                        xyTrackArtist = parts[1].trim();
                    } else {
                        xyTrackTitle = fullSongName;
                        xyTrackArtist = "";
                    }
                } else {
                    xyTrackTitle = null;
                    xyTrackArtist = null;
                }

                if (intent.hasExtra("artistPicPath")) {
                    xyTrackAlbumArtPath = intent.getStringExtra("artistPicPath");
                    if (TextUtils.isEmpty(xyTrackArtist) && !TextUtils.isEmpty(xyTrackAlbumArtPath)) {
                        try {
                            java.io.File file = new java.io.File(xyTrackAlbumArtPath);
                            String name = file.getName();
                            int dot = name.lastIndexOf('.');
                            if (dot > 0) {
                                xyTrackArtist = name.substring(0, dot);
                            }
                        } catch (Exception e) {
                            // Hata durumunda yoksay
                        }
                    }
                }

                boolean playState = intent.getBooleanExtra("PlayState", false);
                if (playState) {
                    xyIsPlaying = true;
                    manager.onExternalPlayerStarted(PACKAGE_NAME);
                }

                if (!TextUtils.equals(oldTitle, xyTrackTitle) || !TextUtils.equals(oldArtist, xyTrackArtist)) {
                    changed = true;
                }
            } else if ("update.widget.update_proBar".equals(action)) {
                xyDuration = intent.getIntExtra("proBarmax", 0);
                xyPosition = intent.getIntExtra("proBarvalue", 0);

                String song = intent.getStringExtra("curplaysong");
                if (song != null && !TextUtils.equals(xyTrackTitle, song)) {
                    xyTrackTitle = song;
                    changed = true;
                }
                if (intent.hasExtra("artistPicPath")) {
                    xyTrackAlbumArtPath = intent.getStringExtra("artistPicPath");
                }
            }

            if (changed) {
                manager.notifyTrackChanged();
            }
            manager.notifyStateChanged();
        }
    };

    public XyAutoMusicAdapter(Context context, MusicManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;

        // BroadcastReceiver kaydi (Android 14+ uyumlu)
        IntentFilter filter = new IntentFilter();
        filter.addAction("update.widget.playbtnstate");
        filter.addAction("update.widget.update_proBar");
        filter.addAction("update.widget.songname");
        filter.addAction("update.widget.btnfun");
        filter.addAction("update.widget.dataError");
        filter.addAction("update.widget.musicinit");
        filter.addAction("update.widget.cdinit");
        filter.addAction("update.widget.albumpic");
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(xyAutoReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            this.context.registerReceiver(xyAutoReceiver, filter);
        }
    }

    public void bindService() {
        if (xyServiceBound) return;
        try {
            Intent intent = new Intent("com.acloud.stub.service.aidl.IPlayService");
            intent.setClassName(PACKAGE_NAME, "com.acloud.stub.service.XYPlayerService");
            intent.setAction("init_widget");
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            context.bindService(intent, xyServiceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "XYPlayService baglaniyor...");
        } catch (Exception e) {
            Log.e(TAG, "XYPlayService baglanirken hata: " + e.getMessage());
        }
    }

    public void unbindService() {
        if (!xyServiceBound) return;
        try {
            context.unbindService(xyServiceConnection);
            xyPlayService = null;
            xyServiceBound = false;
            Log.d(TAG, "XYPlayService baglantisi koparildi.");
        } catch (Exception e) {
            Log.e(TAG, "XYPlayService unbind hatasi", e);
        }
    }

    private void sendServiceCommand(String action) {
        try {
            Intent intent = new Intent();
            intent.setClassName(PACKAGE_NAME, "com.acloud.stub.service.XYPlayerService");
            intent.setAction(action);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Command sent to XYPlayerService: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send command to XYPlayerService: " + action, e);
        }
    }

    private void sendBroadcastCommand(String action) {
        try {
            Intent intent = new Intent(action);
            context.sendBroadcast(intent);
            Log.d(TAG, "Broadcast sent: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send broadcast command: " + action, e);
        }
    }

    @Override
    public void play() {
        boolean done = false;
        if (xyPlayService != null && xyServiceBound) {
            try {
                xyPlayService.start();
                done = true;
            } catch (Exception e) {
                Log.e(TAG, "xyPlayService.start() hatasi", e);
            }
        }
        if (!done) {
            sendServiceCommand("xy.cdwidget.play");
        }
        sendBroadcastCommand("xy.android.playpause");
    }

    @Override
    public void pause() {
        boolean done = false;
        if (xyPlayService != null && xyServiceBound) {
            try {
                xyPlayService.pause();
                done = true;
            } catch (Exception e) {
                Log.e(TAG, "xyPlayService.pause() hatasi", e);
            }
        }
        if (!done) {
            sendServiceCommand("xy.cdwidget.pause");
        }
        sendBroadcastCommand("xy.android.playpause");
    }

    @Override
    public void next() {
        sendServiceCommand("xy.cdwidget.next");
        sendBroadcastCommand("xy.android.nextmedia");
    }

    @Override
    public void prev() {
        sendServiceCommand("xy.cdwidget.prev");
        sendBroadcastCommand("xy.android.previousmedia");
    }

    @Override
    public void seekTo(int position) {
        if (xyPlayService != null && xyServiceBound) {
            try {
                xyPlayService.seekTo(position);
            } catch (Exception e) {
                Log.e(TAG, "xyPlayService.seekTo() hatasi", e);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        return xyIsPlaying;
    }

    @Override
    public boolean isActive() {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getTitle() {
        return xyTrackTitle;
    }

    @Override
    public String getArtist() {
        return xyTrackArtist;
    }

    @Override
    public Bitmap getAlbumArt() {
        if (xyTrackAlbumArtPath != null && !xyTrackAlbumArtPath.isEmpty()) {
            try {
                return BitmapFactory.decodeFile(xyTrackAlbumArtPath);
            } catch (Exception e) {
                Log.e(TAG, "Cover art decode hatasi", e);
            }
        }
        return null;
    }

    @Override
    public int getDuration() {
        return xyDuration;
    }

    @Override
    public int getPosition() {
        return xyPosition;
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }
}
