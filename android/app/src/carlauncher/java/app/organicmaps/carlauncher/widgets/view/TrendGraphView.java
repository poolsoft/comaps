package app.organicmaps.carlauncher.widgets.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple Trend Graph View for Temperature
 * Draws a smooth curve connecting temperature points.
 */
public class TrendGraphView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();
    private List<Double> dataPoints = new ArrayList<>();
    private double minVal = 0, maxVal = 0;

    public TrendGraphView(Context context) {
        this(context, null);
    }

    public TrendGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint.setColor(0xFFFFC107); // Amber/Yellow
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setColor(0xFFFFC107);
        dotPaint.setStyle(Paint.Style.FILL);

        fillPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Double> data) {
        this.dataPoints = data;
        calculateMinMax();
        invalidate();
    }

    private void calculateMinMax() {
        if (dataPoints.isEmpty()) return;
        minVal = Double.MAX_VALUE;
        maxVal = -Double.MAX_VALUE;
        for (Double d : dataPoints) {
            if (d < minVal) minVal = d;
            if (d > maxVal) maxVal = d;
        }
        // Add padding
        double range = maxVal - minVal;
        if (range < 1) range = 1;
        minVal -= range * 0.2;
        maxVal += range * 0.2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.size() < 2) return;

        float width = getWidth();
        float height = getHeight();
        float padding = 40f;
        float graphWidth = width - padding * 2;
        float graphHeight = height - padding * 2;
        float stepX = graphWidth / (dataPoints.size() - 1);
        double range = maxVal - minVal;

        path.reset();
        fillPath.reset();

        float firstX = padding;
        float firstY = 0;

        // Create Gradient for fill
        fillPaint.setShader(new LinearGradient(0, 0, 0, height,
                0x44FFC107, 0x00FFC107, Shader.TileMode.CLAMP));

        for (int i = 0; i < dataPoints.size(); i++) {
            double val = dataPoints.get(i);
            float x = padding + i * stepX;
            float y = (float) (height - padding - ((val - minVal) / range) * graphHeight);

            if (i == 0) {
                path.moveTo(x, y);
                firstX = x;
                firstY = y;
            } else {
                // Cubic Bezier for smooth curve could be nice, but simple lineTo is sufficient for now
                // Or simplified Catmull-Rom spline logic?
                // Let's stick to lineTo for simplicity or quadTo for smoothing
                float prevX = padding + (i - 1) * stepX;
                float prevY = (float) (height - padding - ((dataPoints.get(i - 1) - minVal) / range) * graphHeight);
                float midX = (prevX + x) / 2;
                path.cubicTo(midX, prevY, midX, y, x, y);
            }
            
            // Draw Dots
            canvas.drawCircle(x, y, 8f, dotPaint);
        }

        // Close Fill Path
        fillPath.addPath(path);
        fillPath.lineTo(width - padding, height);
        fillPath.lineTo(padding, height);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(path, linePaint);
    }
}
