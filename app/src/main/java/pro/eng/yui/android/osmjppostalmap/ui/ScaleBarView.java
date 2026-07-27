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

        // Scale candidates (meters)
        // Walking: 1min=80m, 5min=400m, 15min=1200m
        // Others: km units or smaller meters
        double[] candidates = {
            10, 20, 50, 80, 100, 200, 400, 800, 1000, 1200, 2000, 5000, 10000, 20000, 50000, 100000, 200000, 500000
        };

        double selectedMeters = candidates[0];
        for (double c : candidates) {
            if (c <= metersInView) {
                selectedMeters = c;
            } else {
                break;
            }
        }

        String text;
        if (selectedMeters == 80) {
            text = "徒歩1分";
        } else if (selectedMeters == 400) {
            text = "徒歩5分";
        } else if (selectedMeters == 1200) {
            text = "徒歩15分";
        } else if (selectedMeters >= 1000) {
            text = ((int) (selectedMeters / 1000)) + "km";
        } else {
            text = ((int) selectedMeters) + "m";
        }

        float barWidthPx = (float) (viewWidth * (selectedMeters / metersInView));
        float density = getResources().getDisplayMetrics().density;
        
        // Bar position (relative to View bottom)
        float barY = viewHeight - 4 * density;
        float tickHeight = 6 * density;
        float tickHeightSub = 3 * density;

        // Draw bar
        canvas.drawLine(0, barY, barWidthPx, barY, barPaint);
        // Left tick
        canvas.drawLine(0, barY - tickHeight, 0, barY, barPaint);
        // Right tick
        canvas.drawLine(barWidthPx, barY - tickHeight, barWidthPx, barY, barPaint);
        // Middle tick (2 segments)
        canvas.drawLine(barWidthPx / 2, barY - tickHeightSub, barWidthPx / 2, barY, barPaint);

        // Draw text
        canvas.drawText(text, 2 * density, barY - tickHeight - 2 * density, textPaint);
    }
}
