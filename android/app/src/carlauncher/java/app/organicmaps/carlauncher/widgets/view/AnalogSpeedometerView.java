package app.organicmaps.carlauncher.widgets.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Modern Analog Speedometer for L-Size Widget.
 * Shows speed with needle and speed limit marker.
 */
public class AnalogSpeedometerView extends View {

    private Paint paint;
    private Paint textPaint;
    private float centerX, centerY, radius;
    
    // Config
    private float currentSpeed = 0f;
    private float speedLimit = 0f;
    private float maxScaleSpeed = 240f;
    
    // Geometry
    private final float startAngle = 135f;
    private final float sweepAngle = 270f;
    private RectF arcRect = new RectF();

    // Colors
    private int colorArc = Color.parseColor("#44FFFFFF");
    private int colorTicks = Color.WHITE;
    private int colorNeedle = Color.CYAN; // OsmAnd Blue/Cyan
    private int colorLimit = Color.RED;
    private int colorText = Color.WHITE;

    public AnalogSpeedometerView(Context context) {
        super(context);
        init();
    }

    public AnalogSpeedometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
        
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(colorText);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }
    
    public void setSpeed(float speed) {
        this.currentSpeed = Math.min(speed, maxScaleSpeed * 1.1f);
        invalidate();
    }
    
    public void setSpeedLimit(float limit) {
        this.speedLimit = limit;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(centerX, centerY) * 0.9f;
        
        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (radius <= 0) return;

        drawScale(canvas);
        drawLimit(canvas);
        drawNeedle(canvas);
        drawDigitalSpeed(canvas);
    }
    
    private void drawScale(Canvas canvas) {
        // Background Arc
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * 0.05f);
        paint.setColor(colorArc);
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, paint);
        
        // Ticks
        paint.setColor(colorTicks);
        paint.setStrokeWidth(radius * 0.02f);
        
        int step = 20; // 20kmh steps
        for (int s = 0; s <= maxScaleSpeed; s += step) {
            float progress = s / maxScaleSpeed;
            float angle = startAngle + (progress * sweepAngle);
            float rad = (float) Math.toRadians(angle);
            
            float r1 = radius * 0.85f;
            float r2 = radius * 0.95f;
            
            // Major tick
             paint.setStrokeWidth(radius * 0.03f);
            
            float x1 = (float) (centerX + Math.cos(rad) * r1);
            float y1 = (float) (centerY + Math.sin(rad) * r1);
            float x2 = (float) (centerX + Math.cos(rad) * r2);
            float y2 = (float) (centerY + Math.sin(rad) * r2);
            
            canvas.drawLine(x1, y1, x2, y2, paint);
            
            // Text ? Maybe too crowded for small widget, but L is big.
            // Let's draw numbers for 0, 60, 120, 180, 240
            if (s % 60 == 0) {
                textPaint.setTextSize(radius * 0.15f);
                float tx = (float) (centerX + Math.cos(rad) * (radius * 0.70f));
                float ty = (float) (centerY + Math.sin(rad) * (radius * 0.70f)) + (radius * 0.05f);
                canvas.drawText(String.valueOf(s), tx, ty, textPaint);
            }
        }
    }
    
    private void drawLimit(Canvas canvas) {
        if (speedLimit <= 0) return;
        
        float progress = Math.min(speedLimit, maxScaleSpeed) / maxScaleSpeed;
        float angle = startAngle + (progress * sweepAngle);
        float rad = (float) Math.toRadians(angle);
        
        // Red Mark Line
        paint.setColor(colorLimit);
        paint.setStrokeWidth(radius * 0.06f);
        
        float r1 = radius * 0.80f;
        float r2 = radius * 1.0f;
        
        float x1 = (float) (centerX + Math.cos(rad) * r1);
        float y1 = (float) (centerY + Math.sin(rad) * r1);
        float x2 = (float) (centerX + Math.cos(rad) * r2);
        float y2 = (float) (centerY + Math.sin(rad) * r2);
        
        canvas.drawLine(x1, y1, x2, y2, paint);
    }
    
    private void drawNeedle(Canvas canvas) {
        float progress = Math.min(currentSpeed, maxScaleSpeed) / maxScaleSpeed;
        float angle = startAngle + (progress * sweepAngle);
        float rad = (float) Math.toRadians(angle);
        
        paint.setColor(colorNeedle);
        paint.setStyle(Paint.Style.FILL);
        
        // Draw simple needle line
        // Or triangle? Simple line with circle
        paint.setStrokeWidth(radius * 0.04f);
        
        float xEnd = (float) (centerX + Math.cos(rad) * (radius * 0.85f));
        float yEnd = (float) (centerY + Math.sin(rad) * (radius * 0.85f));
        
        canvas.drawLine(centerX, centerY, xEnd, yEnd, paint);
        
        // Center cap
        paint.setColor(colorTicks);
        canvas.drawCircle(centerX, centerY, radius * 0.1f, paint);
    }
    
    private void drawDigitalSpeed(Canvas canvas) {
        // Draw Digital Speed number at bottom center
        textPaint.setTextSize(radius * 0.35f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        int val = Math.round(currentSpeed);
        canvas.drawText(String.valueOf(val), centerX, centerY + (radius * 0.6f), textPaint);
        
        textPaint.setTextSize(radius * 0.12f);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("km/h", centerX, centerY + (radius * 0.75f), textPaint);
    }
}
