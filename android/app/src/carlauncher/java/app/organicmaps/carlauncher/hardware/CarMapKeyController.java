package app.organicmaps.carlauncher.hardware;

import android.content.Context;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import androidx.annotation.NonNull;

import app.organicmaps.MwmActivity;
import app.organicmaps.maplayer.MapButtonsController;
import app.organicmaps.sdk.Map;
import app.organicmaps.sdk.location.LocationState;

/**
 * Direksiyon kumandasi, harici klavye, gamepad veya teyp tekerinden gelen
 * donanimsal kumanda tuslarini harita kontrollerine baglayan yonetici.
 * Kod icinde Turkce karakter kullanilmamistir.
 */
public class CarMapKeyController {

    private final MwmActivity activity;
    private final Context context;
    private long lastPanTime = 0;
    private static final long PAN_THROTTLE_MS = 80L;

    public CarMapKeyController(@NonNull MwmActivity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    /**
     * Gelen KeyEvent'i analiz eder ve harita komutlarina donusturur.
     * @return Tus islendiyse true, degilse false
     */
    public boolean handleKeyEvent(@NonNull KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        int keyCode = event.getKeyCode();

        // 1. Zoom (Yakinlastirma / Uzaklastirma) Tuslari
        if (isZoomInKey(keyCode)) {
            Map.zoomIn();
            return true;
        }
        if (isZoomOutKey(keyCode)) {
            Map.zoomOut();
            return true;
        }

        // 2. Konuma Odaklanma (My Position) Tuslari
        if (isCenterKey(keyCode)) {
            LocationState.nativeSwitchToNextMode();
            return true;
        }

        // 3. D-Pad Yon Tuslari (Harita Kaydirma / Pan)
        if (isDpadKey(keyCode)) {
            long now = SystemClock.uptimeMillis();
            if (now - lastPanTime < PAN_THROTTLE_MS) {
                return true; // Asiri hizli tekrarlari yumusat
            }
            lastPanTime = now;
            return handlePan(keyCode);
        }

        // 4. Katman veya Arama Kisa Yol Tuslari
        if (keyCode == KeyEvent.KEYCODE_M) {
            activity.onMapButtonClick(MapButtonsController.MapButtons.toggleMapLayer);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            activity.onMapButtonClick(MapButtonsController.MapButtons.search);
            return true;
        }

        return false;
    }

    private boolean isZoomInKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_PLUS
                || keyCode == KeyEvent.KEYCODE_EQUALS
                || keyCode == KeyEvent.KEYCODE_ZOOM_IN
                || keyCode == KeyEvent.KEYCODE_PAGE_UP
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ADD;
    }

    private boolean isZoomOutKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MINUS
                || keyCode == KeyEvent.KEYCODE_ZOOM_OUT
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
    }

    private boolean isCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean isDpadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /**
     * D-Pad yon tusuna gore haritayi o yonde kaydirir.
     */
    private boolean handlePan(int keyCode) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float centerX = dm.widthPixels / 2f;
        float centerY = dm.heightPixels / 2f;
        float step = 160f * dm.density; // 160dp kaydirma adimi

        float toX = centerX;
        float toY = centerY;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP -> toY = centerY + step; // Harita asagi kayar, yukarisi gorunur
            case KeyEvent.KEYCODE_DPAD_DOWN -> toY = centerY - step;
            case KeyEvent.KEYCODE_DPAD_LEFT -> toX = centerX + step;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> toX = centerX - step;
            default -> {
                return false;
            }
        }

        // Sanal dokunma suruklemesi ile harita kaydirmayi uygula
        Map.nativeOnTouch(Map.NATIVE_ACTION_DOWN, 0, centerX, centerY, Map.INVALID_TOUCH_ID, 0, 0, 0);
        Map.nativeOnTouch(Map.NATIVE_ACTION_MOVE, 0, toX, toY, Map.INVALID_TOUCH_ID, 0, 0, 0);
        Map.nativeOnTouch(Map.NATIVE_ACTION_UP, 0, toX, toY, Map.INVALID_TOUCH_ID, 0, 0, 0);
        return true;
    }
}
