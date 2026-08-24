package app.organicmaps.carlauncher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/** Draws clock glyphs individually so neighbouring characters visibly overlap. */
public class InterlockingClockView extends AppCompatTextView {

    private static final float OVERLAP_EM = 0.16f;
    private static final float COLON_GAP_EM = 0.06f;
    private static final float COLON_SCALE = 0.72f;

    public InterlockingClockView(@NonNull Context context) { super(context); }

    public InterlockingClockView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public InterlockingClockView(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        float outlineWidth = Math.max(1f, getResources().getDisplayMetrics().density * 1.15f);

        for (int i = 0; i < value.length(); i++) {
            String glyph = value.subSequence(i, i + 1).toString();
            boolean colon = value.charAt(i) == ':';
            paint.setTextSize(colon ? baseTextSize * COLON_SCALE : baseTextSize);
            float glyphBaseline = colon ? baseline - baseTextSize * 0.10f : baseline;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineWidth * 2.2f);
            paint.setColor(0x66000000);
            canvas.drawText(glyph, x, glyphBaseline, paint);
            paint.setStrokeWidth(outlineWidth);
            int outlineColor = getCurrentTextColor();
            if (colon) outlineColor = (outlineColor & 0x00FFFFFF) | 0xB0000000;
            paint.setColor(outlineColor);
            canvas.drawText(glyph, x, glyphBaseline, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((getCurrentTextColor() & 0x00FFFFFF)
                    | (colon ? 0x18000000 : 0x30000000));
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
    }

    private float spacingAfter(CharSequence value, int index, float overlap, float colonGap) {
        return value.charAt(index) == ':' || value.charAt(index + 1) == ':' ? colonGap : -overlap;
    }
}
