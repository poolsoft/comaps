package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.R;
import app.organicmaps.sdk.Router;
import app.organicmaps.sdk.routing.RoutingController;

/**
 * iOS Modes UI esintili harita ustu ulasim modu secim kapsulu.
 * Haritanin ust-orta kisminda yatay hap (pill) seklinde konumlanir.
 * 4 mod barindirir: Arac, Yaya, Bisiklet, Toplu Tasima.
 *
 * Kod icerisinde kesinlikle Turkce karakter kullanilmamistir.
 */
public class ModesUiView extends LinearLayout
{
  private static final int COLOR_CAPSULE_BG = 0xD914171E;
  private static final int COLOR_CAPSULE_STROKE = 0x33FFFFFF;
  private static final int COLOR_SELECTED_BG = 0xFF2563EB;
  private static final int COLOR_ACTIVE_ICON = 0xFFFFFFFF;
  private static final int COLOR_INACTIVE_ICON = 0x88FFFFFF;

  private final ImageView btnVehicle;
  private final ImageView btnPedestrian;
  private final ImageView btnBicycle;
  private final ImageView btnTransit;

  public ModesUiView(@NonNull Context context)
  {
    this(context, null);
  }

  public ModesUiView(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setClickable(false);
    setFocusable(false);

    int padH = dp(6);
    int padV = dp(4);
    setPadding(padH, padV, padH, padV);

    // Kapsul arka plani (Oval/Hap seklinde, yuvarlak koseli)
    GradientDrawable capsuleBg = new GradientDrawable();
    capsuleBg.setShape(GradientDrawable.RECTANGLE);
    capsuleBg.setCornerRadius(dp(22));
    capsuleBg.setColor(COLOR_CAPSULE_BG);
    capsuleBg.setStroke(dp(1), COLOR_CAPSULE_STROKE);
    setBackground(capsuleBg);

    // 4 Ulasim Modu Butonunun Olusturulmasi
    btnVehicle = createModeButton(R.drawable.ic_car, "Arac", v -> selectRouter(Router.Vehicle));
    btnPedestrian = createModeButton(R.drawable.ic_pedestrian, "Yaya", v -> selectRouter(Router.Pedestrian));
    btnBicycle = createModeButton(R.drawable.ic_bike, "Bisiklet", v -> selectRouter(Router.Bicycle));
    btnTransit = createModeButton(app.organicmaps.sdk.R.drawable.ic_route_planning_metro_40px, "Toplu Tasima", v -> selectRouter(Router.Transit));

    addView(btnVehicle);
    addView(createSpacer());
    addView(btnPedestrian);
    addView(createSpacer());
    addView(btnBicycle);
    addView(createSpacer());
    addView(btnTransit);

    updateSelectionFromController();
  }

  @NonNull
  private ImageView createModeButton(int iconResId, String contentDescription, OnClickListener listener)
  {
    ImageView iv = new ImageView(getContext());
    int size = dp(34);
    LayoutParams lp = new LayoutParams(size, size);
    iv.setLayoutParams(lp);
    iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    int iconPad = dp(7);
    iv.setPadding(iconPad, iconPad, iconPad, iconPad);
    iv.setImageResource(iconResId);
    iv.setContentDescription(contentDescription);
    iv.setClickable(true);
    iv.setFocusable(true);
    iv.setOnClickListener(listener);
    return iv;
  }

  @NonNull
  private View createSpacer()
  {
    View spacer = new View(getContext());
    LayoutParams lp = new LayoutParams(dp(4), dp(1));
    spacer.setLayoutParams(lp);
    return spacer;
  }

  private void selectRouter(Router router)
  {
    try
    {
      RoutingController.get().setRouterType(router);
    }
    catch (Exception ignored) {}
    updateSelection(router);
  }

  public void updateSelectionFromController()
  {
    try
    {
      Router current = RoutingController.get().getLastRouterType();
      updateSelection(current != null ? current : Router.Vehicle);
    }
    catch (Exception e)
    {
      updateSelection(Router.Vehicle);
    }
  }

  public void updateSelection(Router router)
  {
    applyButtonState(btnVehicle, router == Router.Vehicle);
    applyButtonState(btnPedestrian, router == Router.Pedestrian);
    applyButtonState(btnBicycle, router == Router.Bicycle);
    applyButtonState(btnTransit, router == Router.Transit);
  }

  private void applyButtonState(@NonNull ImageView button, boolean isSelected)
  {
    if (isSelected)
    {
      GradientDrawable selectedBg = new GradientDrawable();
      selectedBg.setShape(GradientDrawable.OVAL);
      selectedBg.setColor(COLOR_SELECTED_BG);
      button.setBackground(selectedBg);
      button.setColorFilter(COLOR_ACTIVE_ICON);
      button.setAlpha(1.0f);
    }
    else
    {
      button.setBackground(null);
      button.setColorFilter(COLOR_INACTIVE_ICON);
      button.setAlpha(0.75f);
    }
  }

  private int dp(int val)
  {
    return Math.round(val * getResources().getDisplayMetrics().density);
  }
}
