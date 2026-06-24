package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.util.AttributeSet;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * Custom DrawerLayout that forces MeasureSpec.EXACTLY for onMeasure.
 * This completely prevents "DrawerLayout must be measured with MeasureSpec.EXACTLY" crashes.
 */
public class ExactDrawerLayout extends DrawerLayout {

    public ExactDrawerLayout(Context context) {
        super(context);
    }

    public ExactDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExactDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // Force EXACTLY mode to prevent any crash during multi-pass measurements
        if (widthMode != MeasureSpec.EXACTLY) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
        }
        if (heightMode != MeasureSpec.EXACTLY) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
