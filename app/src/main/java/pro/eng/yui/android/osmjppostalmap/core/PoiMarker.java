package pro.eng.yui.android.osmjppostalmap.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

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
        String symbol = "〒";
        if (schedule != null && schedule.getCurrentState() == ScheduleResult.CurrentState.UNKNOWN) {
            symbol = "?";
        }
        canvas.drawText(symbol, screenPos.x, screenPos.y + 10f, symbolPaint);

        // 外周リング
        if (schedule != null && schedule.getCurrentState() != ScheduleResult.CurrentState.UNKNOWN) {
            updateRingPaint(schedule);
            float sweepAngle = 360f;
            long now = System.currentTimeMillis();
            
            if (schedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON && schedule.getNextEvent() != null) {
                long remainingMillis = schedule.getNextEvent().getTimestamp() - now;
                float remainingMinutes = remainingMillis / 60000f;
                if (remainingMinutes < 0) remainingMinutes = 0;
                if (remainingMinutes > 60) remainingMinutes = 60;
                sweepAngle = (remainingMinutes / 60f) * 360f;
            }
            
            if (schedule.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON && schedule.getNextEvent() != null) {
                // 営業開始前：緑ドットを短針の位置に配置
                canvas.drawArc(rect, -90f, 360f, false, ringPaint); // 灰がかった緑のリング（updateRingPaintで設定）
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(schedule.getNextEvent().getTimestamp());
                int hour = cal.get(java.util.Calendar.HOUR);
                int minute = cal.get(java.util.Calendar.MINUTE);
                float angle = (hour + minute / 60f) * 30f - 90f;
                
                Paint dotPaint = new Paint(ringPaint);
                dotPaint.setColor(0xFF00FF00); // 明るい緑
                dotPaint.setStyle(Paint.Style.FILL);
                
                float dotX = (float) (screenPos.x + size * Math.cos(Math.toRadians(angle)));
                float dotY = (float) (screenPos.y + size * Math.sin(Math.toRadians(angle)));
                canvas.drawCircle(dotX, dotY, 6f, dotPaint);
            } else {
                canvas.drawArc(rect, -90f, sweepAngle, false, ringPaint);
            }
        }
    }

    private void updateRingPaint(ScheduleResult schedule) {
        switch (schedule.getCurrentState()) {
            case OPENING:
                ringPaint.setColor(0xFF00FF00); // 緑
                break;
            case OPENING_BUT_EVENT_SOON:
                ringPaint.setColor(0xFFFFA500); // 橙
                break;
            case CLOSED:
                ringPaint.setColor(0xFFFF0000); // 赤
                break;
            case TODAY_FINISHED:
                ringPaint.setColor(0xFF808080); // グレー
                break;
            case CLOSING_BUT_OPEN_SOON:
                ringPaint.setColor(0xFF556B2F); // 灰がかった緑 (DarkOliveGreen)
                break;
            case UNKNOWN:
                ringPaint.setColor(0x00000000); // 透明
                break;
        }
    }
}
