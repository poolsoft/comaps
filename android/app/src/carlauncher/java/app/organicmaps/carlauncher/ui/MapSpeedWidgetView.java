package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.organicmaps.MwmApplication;
import app.organicmaps.carlauncher.CarLauncherSettings;
import app.organicmaps.carlauncher.telemetry.TelemetryManager;
import app.organicmaps.sdk.routing.RoutingController;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.StringUtils;
import app.organicmaps.widget.CurrentSpeedView;
import app.organicmaps.widget.SpeedLimitView;

/**
 * Harita uzeri Hiz ve Limit gostergesi kapsulu.
 * Serbest suruste ve rotada canli calisir.
 * CoMaps'in yerel CurrentSpeedView ve SpeedLimitView bilesenlerini kullanir.
 * Ayrica opsiyonel dijital saat icerir.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class MapSpeedWidgetView extends LinearLayout
{
  private static final int COLOR_CAPSULE_BG = 0xD914171E;
  private static final int COLOR_CAPSULE_STROKE = 0x33FFFFFF;

  private final SpeedLimitView speedLimitView;
  private final CurrentSpeedView currentSpeedView;
  private final TextView clockTextView;
  private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
  private final CarLauncherSettings settings;

  public MapSpeedWidgetView(@NonNull Context context)
  {
    this(context, null);
  }

  public MapSpeedWidgetView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    this.settings = new CarLauncherSettings(context);

    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER_HORIZONTAL);
    setClickable(false);
    setFocusable(false);

    int pad = dp(8);
    setPadding(pad, pad, pad, pad);

    // Kapsul arka plani
    GradientDrawable bg = new GradientDrawable();
    bg.setShape(GradientDrawable.RECTANGLE);
    bg.setCornerRadius(dp(18));
    bg.setColor(COLOR_CAPSULE_BG);
    bg.setStroke(dp(1), COLOR_CAPSULE_STROKE);
    setBackground(bg);

    // 1. Hiz Limiti (Varsa gosterilir, yoksa GONE)
    speedLimitView = new SpeedLimitView(context);
    int limitSize = dp(46);
    LayoutParams lpLimit = new LayoutParams(limitSize, limitSize);
    lpLimit.gravity = Gravity.CENTER_HORIZONTAL;
    lpLimit.bottomMargin = dp(6);
    speedLimitView.setLayoutParams(lpLimit);
    speedLimitView.setVisibility(View.GONE);
    addView(speedLimitView);

    // 2. Anlik Hiz (Dairesel CurrentSpeedView)
    currentSpeedView = new CurrentSpeedView(context, null);
    int speedSize = dp(52);
    LayoutParams lpSpeed = new LayoutParams(speedSize, speedSize);
    lpSpeed.gravity = Gravity.CENTER_HORIZONTAL;
    currentSpeedView.setLayoutParams(lpSpeed);
    addView(currentSpeedView);

    // 3. Dijital Saat (Opsiyonel)
    clockTextView = new TextView(context);
    LayoutParams lpClock = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    lpClock.gravity = Gravity.CENTER_HORIZONTAL;
    lpClock.topMargin = dp(6);
    clockTextView.setLayoutParams(lpClock);
    clockTextView.setTextColor(Color.WHITE);
    clockTextView.setTextSize(13);
    clockTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    clockTextView.setGravity(Gravity.CENTER);
    addView(clockTextView);

    update();
  }

  /**
   * Periyodik veya telemetri guncellemesinde hizi ve saati tazeler.
   */
  public void update()
  {
    // 1. Saat guncellemesi
    boolean clockEnabled = settings.isMapClockEnabled();
    if (clockEnabled)
    {
      clockTextView.setVisibility(View.VISIBLE);
      clockTextView.setText(timeFormat.format(new Date()));
    }
    else
    {
      clockTextView.setVisibility(View.GONE);
    }

    // 2. Canli Hiz Verisi
    double speedMps = -1.0;
    try
    {
      TelemetryManager tm = TelemetryManager.getInstance(getContext());
      TelemetryManager.LocationState locState = tm != null ? tm.getLocationState() : null;
      if (locState != null)
      {
        if (locState.rawLocation != null && locState.rawLocation.hasSpeed())
        {
          speedMps = locState.rawLocation.getSpeed();
        }
        else if (locState.speedKmh > 0)
        {
          speedMps = locState.speedKmh / 3.6;
        }
      }
      
      if (speedMps < 0)
      {
        Location loc = MwmApplication.from(getContext()).getLocationHelper().getSavedLocation();
        if (loc != null && loc.hasSpeed())
          speedMps = loc.getSpeed();
      }
    }
    catch (Exception ignored) {}

    currentSpeedView.setCurrentSpeed(speedMps);

    // 3. Hiz Limiti Verisi
    int speedLimit = -1;
    boolean alert = false;
    try
    {
      TelemetryManager tm = TelemetryManager.getInstance(getContext());
      TelemetryManager.NavigationState navState = tm != null ? tm.getNavigationState() : null;
      if (navState != null && navState.speedLimitMps > 0)
      {
        speedLimit = StringUtils.nativeFormatSpeed(navState.speedLimitMps);
        alert = navState.isSpeedLimitExceeded;
      }
      else
      {
        RoutingInfo routingInfo = RoutingController.get().getCachedRoutingInfo();
        if (routingInfo != null && routingInfo.speedLimitMps > 0)
        {
          speedLimit = StringUtils.nativeFormatSpeed(routingInfo.speedLimitMps);
          if (speedMps > 0)
          {
            int currentSpeedInt = StringUtils.nativeFormatSpeed(speedMps);
            alert = speedLimit < currentSpeedInt;
          }
        }
      }
    }
    catch (Exception ignored) {}

    if (speedLimit > 0)
    {
      speedLimitView.setVisibility(View.VISIBLE);
      speedLimitView.setSpeedLimit(speedLimit, alert);
    }
    else
    {
      speedLimitView.setVisibility(View.GONE);
    }
  }

  private int dp(int val)
  {
    return Math.round(val * getResources().getDisplayMetrics().density);
  }
}
