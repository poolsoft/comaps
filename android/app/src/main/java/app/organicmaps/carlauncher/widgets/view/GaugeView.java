package app.organicmaps.carlauncher.widgets.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class GaugeView extends View {

    private Paint arcPaint;
    private Paint needlePaint;
    private Paint textPaint;
    private RectF arcRect = new RectF();
    
    private float minValue = 0;
    private float maxValue = 200;
    private float currentValue = 0;
    private String label = "km/h";
    
    // Config
    private int arcColor = 0xFF555555;
    private int activeColor = 0xFFFF5252;
    private int needleColor = 0xFFFFFFFF;

    public GaugeView(Context context) {
        super(context);
        init();
    }

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        
        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setColor(needleColor);
        
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }
    
    public void setValue(float value) {
        this.currentValue = Math.max(minValue, Math.min(value, maxValue));
        invalidate();
    }
    
    public void setConfig(float min, float max, String label) {
        this.minValue = min;
        this.maxValue = max;
        this.label = label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int w = getWidth();
        int h = getHeight();
        int padding = 40;
        
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - padding;
        
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        
        // Draw Background Arc (135 to 405 degrees -> 270 degree sweep)
        arcPaint.setStrokeWidth(30);
        arcPaint.setColor(arcColor);
        canvas.drawArc(arcRect, 135, 270, false, arcPaint);
        
        // Draw Active Arc
        float sweep = 270f * ((currentValue - minValue) / (maxValue - minValue));
        arcPaint.setColor(activeColor);
        canvas.drawArc(arcRect, 135, sweep, false, arcPaint);
        
        // Draw Needle (Simple Line or Triangle)
        // Angle = 135 + sweep
        float angle = 135 + sweep;
        double rad = Math.toRadians(angle);
        
        float needleLen = radius - 40;
        float nx = (float) (cx + Math.cos(rad) * needleLen);
        float ny = (float) (cy + Math.sin(rad) * needleLen);
        
        needlePaint.setStrokeWidth(10);
        canvas.drawLine(cx, cy, nx, ny, needlePaint);
        
        // Draw Center Circle
        canvas.drawCircle(cx, cy, 20, needlePaint);
        
        // Draw Text
        textPaint.setTextSize(60);
        canvas.drawText(String.valueOf((int)currentValue), cx, cy + 100, textPaint);
        
        textPaint.setTextSize(30);
        textPaint.setColor(0xFFAAAAAA);
        canvas.drawText(label, cx, cy + 140, textPaint);
    }
}
