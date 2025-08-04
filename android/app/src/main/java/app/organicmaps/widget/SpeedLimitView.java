package app.organicmaps.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import app.organicmaps.R;

public class SpeedLimitView extends BaseSignView
{
  private int    mSpeedLimit = -1;
  private boolean mAlert     = false;
  private String mSpeedStr   = "-1";
  private final int unlimitedBorderColor;
  private final int unlimitedStripeColor;

  public SpeedLimitView(Context ctx, @Nullable AttributeSet attrs)
  {
    super(ctx, attrs);

    setBorderWidthRatio(0.2f);
    setBorderInsetRatio(0.05f);

    try (TypedArray a = ctx.getTheme()
            .obtainStyledAttributes(attrs, R.styleable.SpeedLimitView, 0, 0))
    {
      int bg   = a.getColor(R.styleable.SpeedLimitView_speedLimitBackgroundColor, DefaultValues.BACKGROUND_COLOR);
      int bd   = a.getColor(R.styleable.SpeedLimitView_speedLimitBorderColor,     DefaultValues.BORDER_COLOR);
      int al   = a.getColor(R.styleable.SpeedLimitView_speedLimitAlertColor,      DefaultValues.ALERT_COLOR);
      int tc   = a.getColor(R.styleable.SpeedLimitView_speedLimitTextColor,       DefaultValues.TEXT_COLOR);
      int tac  = a.getColor(R.styleable.SpeedLimitView_speedLimitTextAlertColor,  DefaultValues.TEXT_ALERT_COLOR);
      setColors(bg, bd, al, tc, tac);

      unlimitedBorderColor = a.getColor(R.styleable.SpeedLimitView_speedLimitUnlimitedBorderColor,  DefaultValues.UNLIMITED_BORDER_COLOR);
      unlimitedStripeColor = a.getColor(R.styleable.SpeedLimitView_speedLimitUnlimitedStripeColor,  DefaultValues.UNLIMITED_STRIPE_COLOR);

      if (isInEditMode())
      {
        mSpeedLimit = a.getInt(R.styleable.SpeedLimitView_speedLimitEditModeSpeedLimit, 60);
        mAlert      = a.getBoolean(R.styleable.SpeedLimitView_speedLimitEditModeAlert,   false);
        mSpeedStr   = Integer.toString(mSpeedLimit);
      }
    }
  }

  public void setSpeedLimit(int limit, boolean alert)
  {
    if (mSpeedLimit != limit)
    {
      mSpeedLimit = limit;
      mSpeedStr   = Integer.toString(limit);
      requestLayout();
    }
    mAlert = alert;
    configureTextSize();
    invalidate();
  }

  @Nullable
  @Override
  protected String getValueString()
  {
    return (mSpeedLimit > 0 ? mSpeedStr : null);
  }

  @Override
  protected boolean isAlert()
  {
    return mAlert;
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    float cx = mWidth/2f, cy = mHeight/2f;

    if (mSpeedLimit == 0)
    {
      // background
      mBackgroundPaint.setColor(mBackgroundColor);
      canvas.drawCircle(cx, cy, mRadius, mBackgroundPaint);

      // black border
      mBorderPaint.setColor(unlimitedBorderColor);
      mBorderPaint.setStrokeWidth(mBorderWidth);
      canvas.drawCircle(cx, cy, mBorderRadius, mBorderPaint);

      // draw 5 diagonal stripes
      drawUnlimitedStripes(canvas, cx, cy);
    }
    else
    {
      // delegate to BaseSignView’s onDraw
      super.onDraw(canvas);
    }
  }

  private void drawUnlimitedStripes(Canvas c, float cx, float cy)
  {
    Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    stripe.setColor(unlimitedStripeColor);
    stripe.setStrokeWidth(mBorderWidth * 0.4f);

    float r     = mRadius * 0.8f;       // shorten to 80% of full radius
    float diag  = (float)(1/Math.sqrt(2));
    float dx    = -diag, dy = +diag;
    float px    = -dy,    py = +dx;     // perpendicular
    float step  = r * 0.15f;            // spacing

    for (int i = -2; i <= 2; i++)
    {
      float ox = px * step * i;
      float oy = py * step * i;
      float sx = cx + dx * r + ox;
      float sy = cy + dy * r + oy;
      float ex = cx - dx * r + ox;
      float ey = cy - dy * r + oy;
      c.drawLine(sx, sy, ex, ey, stripe);
    }
  }


  private interface DefaultValues
  {
    int BACKGROUND_COLOR       = 0xFFFFFFFF;
    int BORDER_COLOR           = 0xFFFF0000;
    int ALERT_COLOR            = 0xFFFF0000;
    int TEXT_COLOR             = 0xFF000000;
    int TEXT_ALERT_COLOR       = 0xFFFFFFFF;
    int UNLIMITED_BORDER_COLOR = 0xFF000000;
    int UNLIMITED_STRIPE_COLOR = 0xFF000000;
  }
}
