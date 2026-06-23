package app.organicmaps.carlauncher.antenna;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.osmand.data.LatLon;
import net.osmand.data.RotatedTileBox;
import app.organicmaps.views.OsmandMapTileView;
import app.organicmaps.views.layers.base.OsmandMapLayer;
import app.organicmaps.views.layers.base.OsmandMapLayer.DrawSettings;

/**
 * Layer to visualize Antenna Alignment (Line between A and B).
 */
public class AntennaMapLayer extends OsmandMapLayer implements AntennaManager.AntennaListener {

    private final AntennaManager manager;
    private final Paint linePaint;
    private final Paint textPaint;
    private final Path path = new Path();

    private Bitmap iconA;
    private Bitmap iconB;

    public AntennaMapLayer(OsmandMapTileView view) {
        // initLayer is deprecated/removed in some versions? No, OsmandMapLayer
        // constructor usually takes no args or Context.
        // Checking MeasurementToolLayer: super(ctx).
        // Let's defer "view" usage to initLayer or use context from view.
        super(view.getContext());
        this.manager = AntennaManager.getInstance(view.getContext());
        this.manager.setListener(this);

        linePaint = new Paint();
        linePaint.setColor(Color.MAGENTA); // Visibility
        linePaint.setStrokeWidth(5f * view.getDensity());
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);
        linePaint.setShadowLayer(3, 0, 0, Color.BLACK);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(14f * view.getDensity());
        textPaint.setAntiAlias(true);
        textPaint.setShadowLayer(3, 0, 0, Color.BLACK);

        // Load icons? Or simple circles for now.
        // iconA = BitmapFactory.decodeResource(view.getResources(),
        // R.drawable.ic_antenna_a);
    }

    @Override
    public void onDraw(Canvas canvas, RotatedTileBox tileBox,
            DrawSettings settings) {
        if (!manager.isLayerVisible()) {
            return;
        }

        AntennaManager.AntennaPoint pA = manager.getSource();
        AntennaManager.AntennaPoint pB = manager.getTarget();

        if (pA != null) {
            drawPoint(canvas, tileBox, pA, "KAYNAK");
        }
        if (pB != null) {
            drawPoint(canvas, tileBox, pB, "HEDEF");
        }

        if (pA != null && pB != null) {
            drawLine(canvas, tileBox, pA, pB);
        }
    }

    private void drawPoint(Canvas canvas, RotatedTileBox tileBox, AntennaManager.AntennaPoint point, String label) {
        if (tileBox.containsLatLon(point.lat, point.lon)) {
            float x = tileBox.getPixXFromLatLon(point.lat, point.lon);
            float y = tileBox.getPixYFromLatLon(point.lat, point.lon);

            canvas.drawCircle(x, y, 15, linePaint);
            canvas.drawText(label, x + 20, y, textPaint);
        }
    }

    private void drawLine(Canvas canvas, RotatedTileBox tileBox, AntennaManager.AntennaPoint pA,
            AntennaManager.AntennaPoint pB) {
        // Simple straight line on screen (valid for visible distances)
        // For accurate geodesic lines on global scale, we need more points.
        // Assuming WiFi distance (<50km), screen line is OK.

        float xA = tileBox.getPixXFromLatLon(pA.lat, pA.lon);
        float yA = tileBox.getPixYFromLatLon(pA.lat, pA.lon);
        float xB = tileBox.getPixXFromLatLon(pB.lat, pB.lon);
        float yB = tileBox.getPixYFromLatLon(pB.lat, pB.lon);

        canvas.drawLine(xA, yA, xB, yB, linePaint);

        // Midpoint Text (Distance)
        float midX = (xA + xB) / 2;
        float midY = (yA + yB) / 2;
        // String distText = String.format("%.1f km", manager.getDistanceMeters() /
        // 1000);
        // canvas.drawText(distText, midX, midY - 10, textPaint);
    }

    @Override
    public void destroyLayer() {
        // manager.setListener(null); // Could be dangerous if this layer is destroyed
        // but manager lives?
        // Manager is Singleton, Layer is tied to View.
        // Ideally we remove listener.
    }

    @Override
    public boolean onSingleTap(PointF point, RotatedTileBox tileBox) {
        String pickingMode = manager.getPickingMode();
        if (pickingMode != null) {
            LatLon latLon = tileBox.getLatLonFromPixel(point.x, point.y);
            double alt = 0;

            if (AntennaManager.PICK_SOURCE.equals(pickingMode)) {
                manager.setSource(latLon, "Harita Noktasi", alt);
                android.widget.Toast.makeText(view.getContext(), "Kaynak nokta ayarlandi.",
                        android.widget.Toast.LENGTH_SHORT).show();
            } else if (AntennaManager.PICK_TARGET.equals(pickingMode)) {
                manager.setTarget(latLon, "Harita Noktasi", alt);
                android.widget.Toast.makeText(view.getContext(), "Hedef nokta ayarlandi.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }

            manager.setPickingMode(null);
            view.refreshMap();
            return true;
        }
        return false;
    }

    @Override
    public boolean onLongPressEvent(PointF point, RotatedTileBox tileBox) {
        return false;
    }

    @Override
    public void onAntennaPointsChanged() {
        // Request redraw
        if (view != null) {
            view.refreshMap();
        }
    }

    @Override
    public boolean drawInScreenPixels() {
        return true;
    }
}
