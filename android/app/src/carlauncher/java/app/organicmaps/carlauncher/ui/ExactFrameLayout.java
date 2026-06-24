package app.organicmaps.carlauncher.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.view.MotionEvent;

/**
 * DrawerLayout'un ConstraintLayout icinde AT_MOST/UNSPECIFIED ile olculup 
 * java.lang.IllegalArgumentException: DrawerLayout must be measured with MeasureSpec.EXACTLY
 * hatasi vermesini onleyen ve harita kucuk paneldeyken dokunmalari havada yakalayan ozel FrameLayout sinifi.
 */
public class ExactFrameLayout extends FrameLayout {

    private boolean interceptTouch = false;
    private Runnable onInterceptClickRunnable;

    public ExactFrameLayout(Context context) {
        super(context);
    }

    public ExactFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExactFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setInterceptTouch(boolean intercept, Runnable clickRunnable) {
        this.interceptTouch = intercept;
        this.onInterceptClickRunnable = clickRunnable;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (interceptTouch) {
            return true; // Alt harita gorunumlerinin dokunmayi tuketmesini onlemek icin havada yakala
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (interceptTouch) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (onInterceptClickRunnable != null) {
                    onInterceptClickRunnable.run();
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // Eger ConstraintLayout ara olcum adiminda EXACTLY disinda bir mod verirse,
        // olculeri EXACTLY moduna zorlayarak DrawerLayout'un cokmesini engelliyoruz.
        if (widthMode != MeasureSpec.EXACTLY) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
        }
        if (heightMode != MeasureSpec.EXACTLY) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
