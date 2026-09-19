package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * CarLauncher icin insets bosluklarini filtreleyen FrameLayout.
 * Android display cutout veya navigation bar tarafindan eklenen
 * asimetrik sol/sag bosluklari sifirlayarak barin tam genislikte (wide)
 * kalmasini saglar.
 */
public class CarNoInsetsFrameLayout extends FrameLayout
{
  public CarNoInsetsFrameLayout(@NonNull Context context)
  {
    super(context);
  }

  public CarNoInsetsFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
  }

  public CarNoInsetsFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr)
  {
    super(context, attrs, defStyleAttr);
  }

  public CarNoInsetsFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes)
  {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @Override
  public void setPadding(int left, int top, int right, int bottom)
  {
    // Yatayda asimetrik cutout ve navbar padding'lerini sifirla (tam genislik)
    super.setPadding(0, 0, 0, bottom);
  }
}
