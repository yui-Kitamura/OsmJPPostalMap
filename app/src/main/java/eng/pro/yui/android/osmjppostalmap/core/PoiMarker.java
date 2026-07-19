package eng.pro.yui.android.osmjppostalmap.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import eng.pro.yui.android.osmjppostalmap.schedule.ScheduleResult;

/**
 * 郵便局とポストのカスタムマーカー
 */
public class PoiMarker extends Marker {

    private final PoiType poiType;
    private ScheduleResult schedule;
    private final Paint ringPaint;
    private final Paint bgPaint;
    private final Paint symbolPaint;

    public enum PoiType {
        POST_OFFICE, POST_BOX
    }

    public PoiMarker(MapView mapView, PoiType poiType) {
        super(mapView);
        this.poiType = poiType;
        
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(8f);
        
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFFF0000); // 日本郵便カラー (Red)
        
        symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(0xFFFFFFFF);
        symbolPaint.setTextSize(30f);
        symbolPaint.setTextAlign(Paint.Align.CENTER);
    }

    public ScheduleResult getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleResult schedule) {
        this.schedule = schedule;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        android.graphics.Point screenPos = new android.graphics.Point();
        mapView.getProjection().toPixels(getPosition(), screenPos);

        float size = 40f;
        RectF rect = new RectF(screenPos.x - size, screenPos.y - size, screenPos.x + size, screenPos.y + size);

        // 背景
        if (poiType == PoiType.POST_OFFICE) {
            canvas.drawRoundRect(rect, 10f, 10f, bgPaint);
        } else {
            canvas.drawCircle(screenPos.x, screenPos.y, size, bgPaint);
        }

        // 〒 記号
        canvas.drawText("〒", screenPos.x, screenPos.y + 10f, symbolPaint);

        // 外周リング
        if (schedule != null) {
            updateRingPaint(schedule);
            float sweepAngle = 360f;
            if (schedule.getCurrentState() == ScheduleResult.CurrentState.OPEN_SOON) {
                // TODO: 60分かけて減少するロジック
                sweepAngle = 180f; // 仮
            }
            canvas.drawArc(rect, -90f, sweepAngle, false, ringPaint);
        }
    }

    private void updateRingPaint(ScheduleResult schedule) {
        switch (schedule.getCurrentState()) {
            case OPENING:
                ringPaint.setColor(0xFF00FF00); // 緑
                break;
            case OPEN_SOON:
                ringPaint.setColor(0xFFFFA500); // 橙
                break;
            case CLOSED:
                ringPaint.setColor(0xFFFF0000); // 赤
                break;
            case TODAY_FINISHED:
                ringPaint.setColor(0xFF808080); // グレー
                break;
        }
    }
}
