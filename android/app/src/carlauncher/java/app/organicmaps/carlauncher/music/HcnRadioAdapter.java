package app.organicmaps.carlauncher.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * Hcn yerel radyo adaptor sinifi.
 * BaseMediaAdapter arayuzunu uygular.
 */
public class HcnRadioAdapter implements BaseMediaAdapter {

    private static final String TAG = "HcnRadioAdapter";
    private static final String PACKAGE_NAME = "com.hcn.autoradio";

    private final Context context;
    private final MusicManager manager;

    private String radioFreq = null;
    private String radioBand = null;
    private boolean radioIsActive = false;

    // HCN radyo frekans yayinlarini dinleyen alici
    private final BroadcastReceiver radioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            Log.d(TAG, "HCN Radio broadcast received: " + action);

            if ("com.android.radio.widget.freq_volue".equals(action)) {
                String band = intent.getStringExtra("fmoram");
                int freq = intent.getIntExtra("freq", 0);

                if (band != null && !band.isEmpty()) {
                    radioBand = band;
                    boolean isAm = "AM".equalsIgnoreCase(band) || "AM1".equalsIgnoreCase(band);
                    if (isAm) {
                        radioFreq = freq + " KHz";
                    } else {
                        radioFreq = (((float) freq) / 100.0f) + " MHz";
                    }
                    radioIsActive = true;
                    manager.onExternalPlayerStarted(PACKAGE_NAME);
                    manager.notifyTrackChanged();
                    manager.notifyStateChanged();
                }
            }
        }
    };

    public HcnRadioAdapter(Context context, MusicManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.android.radio.widget.freq_volue");
        filter.addAction("com.hcn.android.radio.freq");
        filter.addAction("com.hcn.autoradio.FREQ_CHANGED");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(radioReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            this.context.registerReceiver(radioReceiver, filter);
        }
    }

    private void sendMediaKey(int keycode) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                long eventtime = android.os.SystemClock.uptimeMillis();
                android.view.KeyEvent downEvent = new android.view.KeyEvent(eventtime, eventtime, android.view.KeyEvent.ACTION_DOWN, keycode, 0);
                android.view.KeyEvent upEvent = new android.view.KeyEvent(eventtime, eventtime, android.view.KeyEvent.ACTION_UP, keycode, 0);
                am.dispatchMediaKeyEvent(downEvent);
                am.dispatchMediaKeyEvent(upEvent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send media key: " + keycode, e);
        }
    }

    @Override
    public void play() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    @Override
    public void pause() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        radioIsActive = false;
        manager.notifyStateChanged();
    }

    @Override
    public void next() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    @Override
    public void prev() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    @Override
    public void seekTo(int position) {
    }

    @Override
    public boolean isPlaying() {
        return radioIsActive;
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
        return radioFreq != null ? radioFreq : "Yerel Radyo";
    }

    @Override
    public String getArtist() {
        return radioBand != null ? radioBand : "Radyo";
    }

    @Override
    public Bitmap getAlbumArt() {
        return null;
    }

    @Override
    public int getDuration() {
        return 0;
    }

    @Override
    public int getPosition() {
        return 0;
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }
}
