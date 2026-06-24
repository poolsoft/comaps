package app.organicmaps.carlauncher.radio;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * XYAuto Yerel Radyo Kontrol Yoneticisi.
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class RadioManager {

    private static final String TAG = "RadioManager";
    private static RadioManager instance;
    private final Context context;

    private RadioManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized RadioManager getInstance(Context context) {
        if (instance == null) {
            instance = new RadioManager(context);
        }
        return instance;
    }

    /**
     * Radyo frekansini ayarlar.
     * @param freqKHz kHz cinsinden frekans degeri. Ornek: 98.8 MHz icin 98800 gonderilmelidir.
     */
    public void setFrequency(int freqKHz) {
        try {
            Intent intent = new Intent("xy.setfm.freq");
            intent.putExtra("freq", freqKHz);
            context.sendBroadcast(intent);
            Log.d(TAG, "Sent radio frequency broadcast: " + freqKHz + " KHz");
        } catch (Exception e) {
            Log.e(TAG, "Failed to set frequency: " + e.getMessage());
        }
    }

    /**
     * Radyoda bir sonraki kanali arar.
     */
    public void seekNext() {
        try {
            Intent intent = new Intent("xy.android.seek_next");
            context.sendBroadcast(intent);
            Log.d(TAG, "Sent radio seek next broadcast.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek next: " + e.getMessage());
        }
    }

    /**
     * Radyoda bir onceki kanali arar.
     */
    public void seekPrev() {
        try {
            Intent intent = new Intent("xy.android.seek_prev");
            context.sendBroadcast(intent);
            Log.d(TAG, "Sent radio seek prev broadcast.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek prev: " + e.getMessage());
        }
    }

    /**
     * Yerel radyo uygulamasini acar.
     */
    public void openRadioApp() {
        try {
            Intent intent = new Intent("xy.android.radio");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "Launched local radio application.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open radio app: " + e.getMessage());
        }
    }
}
