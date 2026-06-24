package app.organicmaps.carlauncher.widgets.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Modern Analog Clock View for L-Size Widget.
 * Minimalist design with smooth second hand.
 */
public class ModernAnalogClockView extends View {

    private Paint paint;
    private float centerX, centerY, radius;
    private final Calendar calendar;

    // Colors
    private int colorBackground = Color.TRANSPARENT;
    private int colorFace = Color.parseColor("#EEEEEEC0"); // Hafif seffaf beyaz
    private int colorHands = Color.WHITE;
    private int colorSecondHand = Color.parseColor("#FF4081"); // Accent Pink/Red
    private int colorTicks = Color.LTGRAY;

    public ModernAnalogClockView(Context context) {
        super(context);
        calendar = Calendar.getInstance();
        init();
    }

    public ModernAnalogClockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        calendar = Calendar.getInstance();
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(centerX, centerY) * 0.9f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (radius <= 0) return;

        calendar.setTimeInMillis(System.currentTimeMillis());

        float hour = calendar.get(Calendar.HOUR);
        float minute = calendar.get(Calendar.MINUTE);
        float second = calendar.get(Calendar.SECOND);
        float millis = calendar.get(Calendar.MILLISECOND);

        // Smooth movement
        float smoothSecond = second + (millis / 1000f);
        float smoothMinute = minute + (smoothSecond / 60f);
        float smoothHour = hour + (smoothMinute / 60f);

        drawFace(canvas);
        drawTicks(canvas);
        drawHands(canvas, smoothHour, smoothMinute, smoothSecond);
    }

    private void drawFace(Canvas canvas) {
        paint.reset();
        paint.setAntiAlias(true);
        
        // Outer Rim removed for minimal look, just background circle if needed
        // canvas.drawCircle(centerX, centerY, radius, paint);
    }

    private void drawTicks(Canvas canvas) {
        paint.setColor(colorTicks);
        paint.setStrokeWidth(radius * 0.02f);
        paint.setStrokeCap(Paint.Cap.BUTT);

        for (int i = 0; i < 60; i++) {
            float angle = (float) (Math.PI / 30 * i);
            float startRadius = (i % 5 == 0) ? radius * 0.85f : radius * 0.92f;
            
            // Only draw major ticks or few minor ones for minimal look
            if (i % 5 != 0) continue; // Only Hour Ticks for Modern Clean Look

            float stopRadius = radius * 0.95f;

            float x1 = (float) (centerX + Math.cos(angle) * startRadius);
            float y1 = (float) (centerY + Math.sin(angle) * startRadius);
            float x2 = (float) (centerX + Math.cos(angle) * stopRadius);
            float y2 = (float) (centerY + Math.sin(angle) * stopRadius);

            paint.setStrokeWidth((i % 5 == 0) ? radius * 0.04f : radius * 0.02f);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    private void drawHands(Canvas canvas, float hour, float minute, float second) {
        // Hour Hand
        paint.setColor(colorHands);
        paint.setStrokeWidth(radius * 0.06f);
        double hourAngle = (hour / 6.0 * Math.PI) - (Math.PI / 2); // -90 deg rotate
        canvas.drawLine(centerX, centerY,
                (float) (centerX + Math.cos(hourAngle) * radius * 0.5f),
                (float) (centerY + Math.sin(hourAngle) * radius * 0.5f), paint);

        // Minute Hand
        paint.setStrokeWidth(radius * 0.04f);
        double minAngle = (minute / 30.0 * Math.PI) - (Math.PI / 2);
        canvas.drawLine(centerX, centerY,
                (float) (centerX + Math.cos(minAngle) * radius * 0.75f),
                (float) (centerY + Math.sin(minAngle) * radius * 0.75f), paint);

        // Second Hand (Accent)
        paint.setColor(colorSecondHand);
        paint.setStrokeWidth(radius * 0.02f);
        double secAngle = (second / 30.0 * Math.PI) - (Math.PI / 2);
        
        // Draw tail
        canvas.drawLine(
                (float) (centerX - Math.cos(secAngle) * radius * 0.15f),
                (float) (centerY - Math.sin(secAngle) * radius * 0.15f),
                (float) (centerX + Math.cos(secAngle) * radius * 0.85f),
                (float) (centerY + Math.sin(secAngle) * radius * 0.85f), paint);

        // Center Dot
        paint.setColor(colorHands);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius * 0.05f, paint);
        
        paint.setColor(colorSecondHand);
        canvas.drawCircle(centerX, centerY, radius * 0.02f, paint);
    }
    
    // Auto-animate
    private final Runnable animator = new Runnable() {
        @Override
        public void run() {
            invalidate();
            postDelayed(this, 16); // ~60fps
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(animator);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(animator);
    }
}
