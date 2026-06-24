package app.organicmaps.carlauncher.antenna;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class AlignmentView extends View {

    private float targetAzimuth = 0;
    private float targetPitch = 0;

    private float currentAzimuth = 0;
    private float currentPitch = 0;

    private final Paint paintCircle;
    private final Paint paintTarget;
    private final Paint paintCurrent;
    private final Paint paintText;
    private final Paint paintLeveler;

    public AlignmentView(Context context, AttributeSet attrs) {
        super(context, attrs);

        paintCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCircle.setStyle(Paint.Style.STROKE);
        paintCircle.setStrokeWidth(8f);
        paintCircle.setColor(Color.WHITE);

        paintTarget = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTarget.setStyle(Paint.Style.FILL); // STROKE/FILL
        paintTarget.setStrokeWidth(12f);
        paintTarget.setColor(Color.GREEN);
        paintTarget.setStrokeCap(Paint.Cap.ROUND);

        paintCurrent = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCurrent.setStyle(Paint.Style.STROKE);
        paintCurrent.setStrokeWidth(10f);
        paintCurrent.setColor(Color.RED);
        paintCurrent.setStrokeCap(Paint.Cap.ROUND);

        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.WHITE);
        paintText.setTextSize(60f);
        paintText.setTextAlign(Paint.Align.CENTER);

        paintLeveler = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLeveler.setColor(Color.CYAN);
        paintLeveler.setStrokeWidth(20f);
        paintLeveler.setAlpha(150);
    }

    public void setTarget(float azimuth, float pitch) {
        this.targetAzimuth = azimuth;
        this.targetPitch = pitch;
        invalidate();
    }

    public void setCurrent(float azimuth, float pitch) {
        this.currentAzimuth = azimuth;
        this.currentPitch = pitch;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int radius = Math.min(cx, cy) - 80;

        // 1. Background Circles (Cyber Deck Look)
        paintCircle.setColor(Color.parseColor("#22FFFFFF"));
        paintCircle.setStrokeWidth(2f);
        canvas.drawCircle(cx, cy, radius, paintCircle);
        canvas.drawCircle(cx, cy, radius * 0.7f, paintCircle);
        canvas.drawCircle(cx, cy, radius * 0.4f, paintCircle);

        // Crosshairs
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paintCircle);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paintCircle);

        // 2. Compass Ring (Azimuth)
        canvas.save();
        canvas.rotate(-currentAzimuth, cx, cy);

        // Compass Ticks & Degrees
        paintText.setTextSize(32f);
        paintText.setFakeBoldText(true);
        for (int i = 0; i < 360; i += 10) {
            canvas.save();
            canvas.rotate(i, cx, cy);
            if (i % 90 == 0) {
                paintCircle.setColor(i == 0 ? Color.parseColor("#FF9800") : Color.WHITE);
                paintCircle.setStrokeWidth(6f);
                canvas.drawLine(cx, cy - radius, cx, cy - radius + 30, paintCircle);
                
                String label = "";
                if (i == 0) label = "N";
                else if (i == 90) label = "E";
                else if (i == 180) label = "S";
                else if (i == 270) label = "W";
                
                paintText.setColor(i == 0 ? Color.parseColor("#FF9800") : Color.WHITE);
                canvas.drawText(label, cx, cy - radius - 20, paintText);
            } else if (i % 30 == 0) {
                paintCircle.setColor(Color.parseColor("#88FFFFFF"));
                paintCircle.setStrokeWidth(3f);
                canvas.drawLine(cx, cy - radius, cx, cy - radius + 20, paintCircle);
                // Draw numbers for 30, 60, etc.
                paintText.setTextSize(24f);
                paintText.setColor(Color.parseColor("#88FFFFFF"));
                canvas.drawText(String.valueOf(i), cx, cy - radius - 20, paintText);
            } else {
                paintCircle.setColor(Color.parseColor("#44FFFFFF"));
                paintCircle.setStrokeWidth(1f);
                canvas.drawLine(cx, cy - radius, cx, cy - radius + 10, paintCircle);
            }
            canvas.restore();
        }

        // Target Azimuth Marker (Orange Triangle)
        canvas.save();
        canvas.rotate(targetAzimuth, cx, cy);
        paintTarget.setColor(Color.parseColor("#FF9800"));
        paintTarget.setStyle(Paint.Style.FILL);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, cy - radius + 40);
        path.lineTo(cx - 15, cy - radius + 70);
        path.lineTo(cx + 15, cy - radius + 70);
        path.close();
        canvas.drawPath(path, paintTarget);
        canvas.restore();

        canvas.restore(); // Back to screen coordinates

        // 3. Current Heading Indicator (Fixed Upward Pointer)
        paintCurrent.setColor(Color.WHITE);
        paintCurrent.setStrokeWidth(6f);
        canvas.drawLine(cx, cy - radius - 40, cx, cy - radius + 10, paintCurrent);
        
        // 4. Elevation Gauge (Right side vertical ruler)
        int barX = getWidth() - 60;
        int barHeight = radius * 2;
        int barTop = cy - radius;
        int barBottom = cy + radius;

        paintLeveler.setColor(Color.parseColor("#22FFFFFF"));
        canvas.drawRect(barX - 20, barTop, barX + 20, barBottom, paintLeveler);

        // Ruler Ticks
        paintText.setTextSize(20f);
        for (int p = -90; p <= 90; p += 10) {
            float y = cy - (p * (barHeight / 180f));
            if (y >= barTop && y <= barBottom) {
                canvas.drawLine(barX - 15, y, barX, y, paintCircle);
                if (p % 30 == 0) {
                    canvas.drawText(p + "Â°", barX - 45, y + 8, paintText);
                }
            }
        }

        // Target & Current Elevation Markers
        float targetY = cy - (targetPitch * (barHeight / 180f));
        float currentY = cy - (currentPitch * (barHeight / 180f));

        paintTarget.setColor(Color.parseColor("#FF9800"));
        canvas.drawCircle(barX, targetY, 12, paintTarget);
        
        paintCurrent.setColor(Color.WHITE);
        paintCurrent.setStyle(Paint.Style.STROKE);
        paintCurrent.setStrokeWidth(4f);
        canvas.drawCircle(barX, currentY, 18, paintCurrent);

        // 5. Target Lock Feedback
        float azDiff = Math.abs(currentAzimuth - targetAzimuth);
        if (azDiff > 180) azDiff = 360 - azDiff;
        float pitchDiff = Math.abs(currentPitch - targetPitch);

        if (azDiff < 3 && pitchDiff < 3) {
            // LOCK ACHIEVED
            paintCurrent.setColor(Color.parseColor("#4CAF50"));
            paintCurrent.setStrokeWidth(10f);
            canvas.drawCircle(cx, cy, radius * 0.15f, paintCurrent);
            
            paintText.setColor(Color.parseColor("#4CAF50"));
            paintText.setTextSize(48f);
            canvas.drawText("HEDEF KÄ°LÄ°TLENDÄ°", cx, cy + radius + 40, paintText);
        } else if (azDiff < 15 && pitchDiff < 15) {
            // NEAR TARGET
            paintCurrent.setColor(Color.parseColor("#FFEB3B"));
            paintCurrent.setStrokeWidth(4f);
            canvas.drawCircle(cx, cy, radius * 0.15f, paintCurrent);
        }
    }
}
