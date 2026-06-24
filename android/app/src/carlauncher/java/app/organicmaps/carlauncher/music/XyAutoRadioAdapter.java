package app.organicmaps.carlauncher.music;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.util.Log;

public class XyAutoRadioAdapter implements BaseMediaAdapter {

    private static final String TAG = "XyAutoRadioAdapter";
    private static final String PACKAGE_NAME = "com.acloud.stub.extradio";
    private static final String PACKAGE_NAME_ALT = "com.acloud.stub.localradio";

    private final Context context;
    private final MusicManager manager;

    private String radioFreq = null;
    private String radioBand = null;
    private boolean radioIsActive = false;

    private final BroadcastReceiver radioReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            Log.d(TAG, "Radio broadcast received: " + action);

            if ("com.android.radio.widget.freq_volue".equals(action)) {
                String band = intent.getStringExtra("fmoram");
                int freq = intent.getIntExtra("freq", 0);

                if (band == null || band.isEmpty()) {
                    return;
                }

                radioBand = band;
                boolean isAm = "AM".equalsIgnoreCase(band) || "AM1".equalsIgnoreCase(band);

                if (isAm) {
                    radioFreq = freq + " KHz";
                } else if ("FM1".equalsIgnoreCase(band)) {
                    radioFreq = (freq / 1000) + "." + ((freq % 1000) / 100) + " MHz";
                } else {
                    radioFreq = (((float) freq) / 100.0f) + " MHz";
                }

                // If radio broadcast updates frequency, we can assume radio is active and playing!
                radioIsActive = true;
                manager.onExternalPlayerStarted(getPackageName());
                manager.notifyTrackChanged();
                manager.notifyStateChanged();
            }
        }
    };

    public XyAutoRadioAdapter(Context context, MusicManager manager) {
        this.context = context.getApplicationContext();
        this.manager = manager;

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.android.radio.widget.freq_volue");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(radioReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            this.context.registerReceiver(radioReceiver, filter);
        }
    }

    private void sendBroadcastCommand(String action) {
        try {
            Intent intent = new Intent(action);
            context.sendBroadcast(intent);
            Log.d(TAG, "Radio command sent: " + action);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send radio command: " + action, e);
        }
    }

    @Override
    public void play() {
        sendBroadcastCommand("xy.android.playpause");
    }

    @Override
    public void pause() {
        sendBroadcastCommand("xy.android.playpause");
        radioIsActive = false;
        manager.notifyStateChanged();
    }

    @Override
    public void next() {
        sendBroadcastCommand("xy.android.fm_scan_next");
        sendBroadcastCommand("xy.android.seek_next");
    }

    @Override
    public void prev() {
        sendBroadcastCommand("xy.android.fm_scan_prev");
        sendBroadcastCommand("xy.android.seek_prev");
    }

    @Override
    public void seekTo(int position) {
        // Radio doesn't support seek positions
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
            try {
                context.getPackageManager().getPackageInfo(PACKAGE_NAME_ALT, 0);
                return true;
            } catch (Exception ex) {
                return false;
            }
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
        try {
            context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0);
            return PACKAGE_NAME;
        } catch (Exception e) {
            return PACKAGE_NAME_ALT;
        }
    }
}
