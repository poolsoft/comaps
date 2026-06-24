package app.organicmaps.carlauncher.widgets;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import app.organicmaps.carlauncher.widgets.view.WorkspaceCellLayout;

/**
 * Android Sistem Widget'larini Car Launcher widget panelinde gostermek icin sarmalayici sinif.
 *
 * Widget provider'larin cogu, RemoteViews olusturmadan once
 * kendilerine ne kadar alan ayrildigini bilmek ister.
 * Bu bilgi AppWidgetOptions (MIN_WIDTH, MIN_HEIGHT, MAX_WIDTH, MAX_HEIGHT)
 * Bundle'i ile DP cinsinden iletilir.
 *
 * Eger bu bilgi verilmezse, provider RemoteViews gondermez ve
 * getErrorView() tetiklenir. Bu sinif, gercek hucre boyutlarini
 * DP'ye cevirip provider'a ileterek bu sorunu cozer.
 *
 * hostView bir kez olusturulur ve cache'lenir.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class SystemAppWidget extends BaseWidget {

    private final int appWidgetId;
    private AppWidgetHostView hostView;

    public SystemAppWidget(@NonNull Context context, int appWidgetId) {
        super(context, "appwidget_" + appWidgetId, "Sistem Widget");
        this.appWidgetId = appWidgetId;
        this.size = WidgetSize.MEDIUM;
    }

    public int getAppWidgetId() {
        return appWidgetId;
    }

    @NonNull
    @Override
    public View createView() {
        Context currentContext = getContext();
        if (currentContext == null) {
            currentContext = context;
        }

        // 3. parti widget'larin (BatteryGuru, Wellbeing vb.) Material 3 / Dinamik renk
        // ozniteliklerini (attribute) bulabilmesi ve APPLY_FAILURE hatasi vermemesi icin
        // baglami (context) sarmaliyoruz. Burada LayoutInflater'in AppCompatViewInflater
        // kullanip ImageView'u AppCompatImageView'a donusturmesini ve RemoteViews'u
        // cokturmesini (ActionException) engellemek icin Application Context kullaniyoruz.
        Context themedContext = currentContext;
        try {
            Context appCtx = currentContext.getApplicationContext() != null ? currentContext.getApplicationContext() : currentContext;
            themedContext = new android.view.ContextThemeWrapper(appCtx, android.R.style.Theme_DeviceDefault_DayNight);
        } catch (Exception e) {
            android.util.Log.e("SystemAppWidget", "Tema sarmalama hatasi: " + e.getMessage());
        }

        // Cache: Daha once olusturulmus hostView'i tekrar kullan
        if (hostView != null) {
            rootView = hostView;
            return hostView;
        }

        try {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(themedContext);
            AppWidgetProviderInfo appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);

            if (appWidgetInfo == null) {
                return createErrorView(themedContext, "Widget bulunamadi (ID: " + appWidgetId + ")");
            }

            AppWidgetHost host = WidgetManager.getInstance(themedContext).getAppWidgetHost();
            if (host == null) {
                return createErrorView(themedContext, "Widget Host hazir degil");
            }

            // HostView olustur
            hostView = host.createView(themedContext, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);

            // Layout parametrelerini ayarla
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            hostView.setLayoutParams(params);
            rootView = hostView;

            // ---- KRITIK: Provider'a gercek boyut bilgisini DP cinsinden gonder ----
            // Cogu widget provider (BatteryGuru, Weather, Google Keep vb.)
            // onAppWidgetOptionsChanged() callback'ini kullanarak boyuta gore
            // RemoteViews olusturur. Bu bilgi olmadan provider hicbir sey gondermez.
            sendWidgetSizeOptions(themedContext, appWidgetManager, appWidgetInfo);

            // Ek olarak provider'a update broadcast'i gonder
            try {
                Intent updateIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
                updateIntent.setComponent(appWidgetInfo.provider);
                updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
                themedContext.sendBroadcast(updateIntent);
            } catch (Exception broadcastErr) {
                android.util.Log.w("SystemAppWidget", "Widget update broadcast gonderilemedi: " + broadcastErr.getMessage());
            }

            return hostView;

        } catch (Exception e) {
            android.util.Log.e("SystemAppWidget", "Widget olusturulurken hata: " + e.getMessage());
            return createErrorView(themedContext, "Yukleme hatasi: " + e.getLocalizedMessage());
        }
    }

    /**
     * Widget provider'a gercek boyut bilgisini DP cinsinden gonderir.
     * Bu metot, ekranin piksel boyutlarindan grid hucre boyutlarini hesaplar,
     * widget'in spanX ve spanY degerlerine gore toplam alani DP'ye cevirir
     * ve AppWidgetOptions Bundle icinde provider'a iletir.
     *
     * Provider bu bilgiyle onAppWidgetOptionsChanged() callback'ini alir
     * ve dogru boyutta RemoteViews olusturup gonderir.
     */
    private void sendWidgetSizeOptions(Context ctx, AppWidgetManager awm, AppWidgetProviderInfo info) {
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            float density = dm.density;

            // Ekranin kullanilabilir alanini piksel cinsinden al
            int screenWidthPx = dm.widthPixels;
            int screenHeightPx = dm.heightPixels;

            // WorkspaceCellLayout padding'lerini cikar (12dp sol/sag, 8dp ust/alt)
            int paddingSidePx = Math.round(12 * density);
            int paddingTopBottomPx = Math.round(8 * density);
            int usableWidthPx = screenWidthPx - (2 * paddingSidePx);
            int usableHeightPx = screenHeightPx - (2 * paddingTopBottomPx);

            // Taskbar yuksekligini cikar (yakl. 56dp)
            int taskbarPx = Math.round(56 * density);
            usableHeightPx -= taskbarPx;

            // Tek hucre boyutunu piksel cinsinden hesapla
            int cellSize = WorkspaceCellLayout.getCellSize(ctx, usableWidthPx, usableHeightPx);
            int cellWidthPx = cellSize;
            int cellHeightPx = cellSize;

            // Widget'in kapladigi toplam alani piksel cinsinden hesapla
            int spanX = getSpanX();
            int spanY = getSpanY();
            int widgetWidthPx = cellWidthPx * spanX;
            int widgetHeightPx = cellHeightPx * spanY;

            // Pikselleri DP'ye cevir (provider DP bekler)
            int widthDp = Math.round(widgetWidthPx / density);
            int heightDp = Math.round(widgetHeightPx / density);

            // Minimum 40dp olsun (cok kucuk degerlerden kacinmak icin)
            widthDp = Math.max(40, widthDp);
            heightDp = Math.max(40, heightDp);

            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);

            awm.updateAppWidgetOptions(appWidgetId, options);

            android.util.Log.d("SystemAppWidget", "Widget " + appWidgetId +
                    " boyut bilgisi gonderildi: " + widthDp + "x" + heightDp + "dp" +
                    " (span: " + spanX + "x" + spanY + ")");

        } catch (Exception e) {
            android.util.Log.w("SystemAppWidget", "Widget boyut bilgisi gonderilemedi: " + e.getMessage());
        }
    }

    /**
     * Widget boyutu degistiginde (resize sonrasi) provider'a
     * yeni boyut bilgisini gonderir.
     */
    public void notifySizeChanged() {
        Context ctx = getContext();
        if (ctx == null) ctx = context;
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(appWidgetId);
            if (info != null) {
                sendWidgetSizeOptions(ctx, awm, info);
            }
        } catch (Exception e) {
            android.util.Log.w("SystemAppWidget", "notifySizeChanged hatasi: " + e.getMessage());
        }
    }

    private View createErrorView(Context ctx, String errorMessage) {
        TextView errView = new TextView(ctx);
        errView.setText(errorMessage);
        errView.setTextColor(0xFFFF3333);
        errView.setPadding(16, 16, 16, 16);
        errView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        FrameLayout container = new FrameLayout(ctx);
        container.setBackgroundColor(0x33FF0000);
        container.addView(errView);

        rootView = container;
        return container;
    }

    @Override
    public void update() {
        // Sistem widget'lari kendi kendilerini gunceller.
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hostView != null) {
            hostView = null;
        }
    }
}
