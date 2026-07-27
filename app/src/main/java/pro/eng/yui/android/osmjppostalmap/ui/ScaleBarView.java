package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;

public class ScaleBarView extends View {

    private MapView mapView;
    private final Paint barPaint = new Paint();
    private final Paint textPaint = new Paint();

    private static class ScaleConfig {
        final double totalMeters;
        final int segments;
        final String middleLabel;
        final String rightLabel;

        ScaleConfig(double totalMeters, int segments, String middleLabel, String rightLabel) {
            this.totalMeters = totalMeters;
            this.segments = segments;
            this.middleLabel = middleLabel;
            this.rightLabel = rightLabel;
        }
    }

    private static final ScaleConfig[] CANDIDATES = {
            // Metric < 80m
            new ScaleConfig(10, 1, null, "10m"),
            new ScaleConfig(20, 2, "10m", "20m"),
            new ScaleConfig(50, 1, null, "50m"),
            // Walking (80m = 1min)
            new ScaleConfig(80, 1, null, "徒歩1分"),
            new ScaleConfig(160, 2, "徒歩1分", "徒歩2分"),
            new ScaleConfig(400, 1, null, "徒歩5分"),
            new ScaleConfig(800, 2, "徒歩5分", "徒歩10分"),
            new ScaleConfig(1200, 1, null, "徒歩15分"),
            // Metric > 1200m
            new ScaleConfig(2000, 2, "1km", "2km"),
            new ScaleConfig(5000, 1, null, "5km"),
            new ScaleConfig(10000, 2, "5km", "10km"),
            new ScaleConfig(20000, 2, "10km", "20km"),
            new ScaleConfig(50000, 1, null, "50km"),
            new ScaleConfig(100000, 2, "50km", "100km"),
            new ScaleConfig(200000, 2, "100km", "200km"),
            new ScaleConfig(500000, 1, null, "500km")
    };

    public ScaleBarView(Context context) {
        this(context, null);
    }

    public ScaleBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        barPaint.setColor(Color.BLACK);
        barPaint.setStrokeWidth(1 * density);
        barPaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setMapView(MapView mapView) {
        this.mapView = mapView;
        if (this.mapView != null) {
            this.mapView.addMapListener(new MapListener() {
                @Override
                public boolean onScroll(ScrollEvent event) {
                    invalidate();
                    return false;
                }

                @Override
                public boolean onZoom(ZoomEvent event) {
                    invalidate();
                    return false;
                }
            });
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mapView == null) {
            return;
        }

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        Projection projection = mapView.getProjection();
        if (projection == null) {
            return;
        }

        // Calculate distance across the view width at the center latitude
        IGeoPoint pLeft = projection.fromPixels(0, viewHeight / 2);
        IGeoPoint pRight = projection.fromPixels(viewWidth, viewHeight / 2);
        if (pLeft == null || pRight == null) return;
        
        double metersInView = new GeoPoint(pLeft.getLatitude(), pLeft.getLongitude())
                .distanceToAsDouble(new GeoPoint(pRight.getLatitude(), pRight.getLongitude()));
        
        if (metersInView <= 0) {
            return;
        }

        ScaleConfig selected = CANDIDATES[0];
        for (ScaleConfig c : CANDIDATES) {
            if (c.totalMeters <= metersInView) {
                selected = c;
            } else {
                break;
            }
        }

        float barWidthPx = (float) (viewWidth * (selected.totalMeters / metersInView));
        float density = getResources().getDisplayMetrics().density;
        
        // Bar position (relative to View bottom)
        float barY = viewHeight - 4 * density;
        float tickHeight = 6 * density;

        // Draw bar
        canvas.drawLine(0, barY, barWidthPx, barY, barPaint);
        // Left tick (0)
        canvas.drawLine(0, barY - tickHeight, 0, barY, barPaint);
        // Right tick
        canvas.drawLine(barWidthPx, barY - tickHeight, barWidthPx, barY, barPaint);
        
        // Labels Y position
        float labelY = barY - tickHeight - 2 * density;

        // Draw "0" label
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("0", 0, labelY, textPaint);

        // Middle tick and label (if 2 segments)
        if (selected.segments == 2 && selected.middleLabel != null) {
            float midX = barWidthPx / 2;
            canvas.drawLine(midX, barY - tickHeight, midX, barY, barPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(selected.middleLabel, midX, labelY, textPaint);
        }

        // Draw right label
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(selected.rightLabel, barWidthPx, labelY, textPaint);
    }
}
