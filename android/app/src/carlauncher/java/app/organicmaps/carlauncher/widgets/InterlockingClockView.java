package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.Xfermode;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/** Draws clock glyphs individually so neighbouring characters visibly overlap. */
public class InterlockingClockView extends AppCompatTextView {

    private static final float OVERLAP_EM = 0.16f;
    private static final float COLON_GAP_EM = 0.06f;
    private static final float COLON_SCALE = 0.72f;
    private static final Xfermode CLEAR_XFERMODE = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    public InterlockingClockView(@NonNull Context context) {
        super(context);
        applyDockClockTypeface();
    }

    public InterlockingClockView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        applyDockClockTypeface();
    }

    public InterlockingClockView(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyDockClockTypeface();
    }

    private void applyDockClockTypeface() {
        try {
            setTypeface(Typeface.createFromAsset(getContext().getAssets(), "fonts/Cross Boxed.ttf"));
        } catch (RuntimeException ignored) {
            setTypeface(Typeface.DEFAULT_BOLD);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        CharSequence value = getText();
        if (value == null || value.length() == 0) return;

        Paint paint = getPaint();
        float baseTextSize = paint.getTextSize();
        float overlap = paint.getTextSize() * OVERLAP_EM;
        float colonGap = paint.getTextSize() * COLON_GAP_EM;
        float contentWidth = 0f;
        for (int i = 0; i < value.length(); i++) {
            boolean colon = value.charAt(i) == ':';
            paint.setTextSize(colon ? baseTextSize * COLON_SCALE : baseTextSize);
            contentWidth += paint.measureText(value, i, i + 1);
            if (i < value.length() - 1) {
                contentWidth += spacingAfter(value, i, overlap, colonGap);
            }
        }
        paint.setTextSize(baseTextSize);

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float x = getPaddingLeft() + Math.max(0f,
                (getWidth() - getPaddingLeft() - getPaddingRight() - contentWidth) / 2f);
        float baseline = getPaddingTop()
                + (getHeight() - getPaddingTop() - getPaddingBottom()
                - metrics.bottom - metrics.top) / 2f;

        Paint.Style oldStyle = paint.getStyle();
        int oldColor = paint.getColor();
        float oldStrokeWidth = paint.getStrokeWidth();
        Xfermode oldXfermode = paint.getXfermode();
        float outlineWidth = Math.max(1f, getResources().getDisplayMetrics().density * 1.15f);
        int layer = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);

        for (int i = 0; i < value.length(); i++) {
            String glyph = value.subSequence(i, i + 1).toString();
            boolean colon = value.charAt(i) == ':';
            paint.setTextSize(colon ? baseTextSize * COLON_SCALE : baseTextSize);
            float glyphBaseline = colon ? baseline - baseTextSize * 0.10f : baseline;
            // Remove older glyph lines below this glyph before drawing its outline.
            // The off-screen layer keeps the cleared interior transparent.
            paint.setXfermode(CLEAR_XFERMODE);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(outlineWidth * 2.4f);
            canvas.drawText(glyph, x, glyphBaseline, paint);
            paint.setXfermode(oldXfermode);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineWidth);
            int outlineColor = getCurrentTextColor();
            if (colon) outlineColor = (outlineColor & 0x00FFFFFF) | 0xB0000000;
            paint.setColor(outlineColor);
            canvas.drawText(glyph, x, glyphBaseline, paint);
            x += paint.measureText(glyph);
            if (i < value.length() - 1) {
                x += spacingAfter(value, i, overlap, colonGap);
            }
        }

        paint.setStyle(oldStyle);
        paint.setColor(oldColor);
        paint.setStrokeWidth(oldStrokeWidth);
        paint.setTextSize(baseTextSize);
        paint.setXfermode(oldXfermode);
        canvas.restoreToCount(layer);
    }

    private float spacingAfter(CharSequence value, int index, float overlap, float colonGap) {
        return value.charAt(index) == ':' || value.charAt(index + 1) == ':' ? colonGap : -overlap;
    }
}
