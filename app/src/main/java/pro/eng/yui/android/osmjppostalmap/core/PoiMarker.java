package pro.eng.yui.android.osmjppostalmap.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.osmdroid.util.GeoPoint;
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
    private static final float SIZE = 30f;

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
        bgPaint.setColor(0xCCFF0000); // 日本郵便カラー (Red) with alpha
        
        symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(0xFFFFFFFF);
        symbolPaint.setTextSize(22f);
        symbolPaint.setTextAlign(Paint.Align.CENTER);

        // ヒットテスト用の範囲を設定
        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
    }

    public ScheduleResult getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleResult schedule) {
        this.schedule = schedule;
    }

    @Override
    public boolean onSingleTapConfirmed(android.view.MotionEvent event, MapView mapView) {
        // デフォルトのヒットテストがアイコン画像の有無に依存するため、自前で判定
        android.graphics.Point screenPos = mapView.getProjection().toPixels(getPosition(), null);
        
        float dx = event.getX() - screenPos.x;
        float dy = event.getY() - screenPos.y;
        
        // タップ判定を厳密にするため、サイズに合わせて調整
        if (dx*dx + dy*dy <= SIZE * SIZE * 1.5f) { 
            if (mOnMarkerClickListener != null) {
                return mOnMarkerClickListener.onMarkerClick(this, mapView);
            }
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        android.graphics.Point screenPos = new android.graphics.Point();
        mapView.getProjection().toPixels(getPosition(), screenPos);

        float size = SIZE;
        RectF rect = new RectF(screenPos.x - size, screenPos.y - size, screenPos.x + size, screenPos.y + size);

        // 背景
        if (poiType == PoiType.POST_OFFICE) {
            canvas.drawRoundRect(rect, 10f, 10f, bgPaint);
        } else {
            canvas.drawCircle(screenPos.x, screenPos.y, size, bgPaint);
        }

        // 外周リング
        if (schedule != null && schedule.getCurrentState() != ScheduleResult.CurrentState.UNKNOWN) {
            updateRingPaint(schedule);
            float sweepAngle = 360f;
            long now = System.currentTimeMillis();
            
            // リングを少し外側に描画
            float ringSize = size + (ringPaint.getStrokeWidth() / 2) - 1f;
            RectF ringRect = new RectF(screenPos.x - ringSize, screenPos.y - ringSize, screenPos.x + ringSize, screenPos.y + ringSize);

            if (schedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON && schedule.getNextEvent() != null) {
                long remainingMillis = schedule.getNextEvent().getTimestamp() - now;
                float remainingMinutes = remainingMillis / 60000f;
                if (remainingMinutes < 0) remainingMinutes = 0;
                if (remainingMinutes > 60) remainingMinutes = 60;
                sweepAngle = (remainingMinutes / 60f) * 360f;
            }
            
            boolean showDot = false;
            if (schedule.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON && schedule.getNextEvent() != null) {
                showDot = true;
            }

            if (showDot) {
                // 営業開始前または収集前：緑ドットを短針の位置に配置
                if (poiType == PoiType.POST_OFFICE) {
                    canvas.drawRoundRect(ringRect, 10f, 10f, ringPaint);
                } else {
                    canvas.drawArc(ringRect, -90f, 360f, false, ringPaint);
                }
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(schedule.getNextEvent().getTimestamp());
                int hour = cal.get(java.util.Calendar.HOUR);
                int minute = cal.get(java.util.Calendar.MINUTE);
                float angle = (hour + minute / 60f) * 30f - 90f;
                
                Paint dotPaint = new Paint(ringPaint);
                dotPaint.setColor(0xFF00FF00); // 明るい緑
                dotPaint.setStyle(Paint.Style.FILL);
                
                float dotX = (float) (screenPos.x + ringSize * Math.cos(Math.toRadians(angle)));
                float dotY = (float) (screenPos.y + ringSize * Math.sin(Math.toRadians(angle)));
                canvas.drawCircle(dotX, dotY, 6f, dotPaint);
            } else {
                if (schedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                    // 背景としてグレーのリングを描画
                    Paint bgRingPaint = new Paint(ringPaint);
                    bgRingPaint.setColor(0xFF808080);
                    if (poiType == PoiType.POST_OFFICE) {
                        canvas.drawRoundRect(ringRect, 10f, 10f, bgRingPaint);
                    } else {
                        canvas.drawArc(ringRect, -90f, 360f, false, bgRingPaint);
                    }
                }

                if (poiType == PoiType.POST_OFFICE && sweepAngle == 360f) {
                    canvas.drawRoundRect(ringRect, 10f, 10f, ringPaint);
                } else {
                    canvas.drawArc(ringRect, -90f, sweepAngle, false, ringPaint);
                }
            }
        }

        // 〒 記号
        String symbol = "〒";
        if (schedule != null && schedule.getCurrentState() == ScheduleResult.CurrentState.UNKNOWN) {
            symbol = "?";
        }
        canvas.drawText(symbol, screenPos.x, screenPos.y + (symbolPaint.getTextSize() / 3), symbolPaint);
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
                ringPaint.setColor(0xFF808080); // グレー (赤から変更)
                break;
            case TODAY_FINISHED:
                ringPaint.setColor(0xFF808080); // グレー
                break;
            case CLOSING_BUT_OPEN_SOON:
                if (poiType == PoiType.POST_BOX) {
                    ringPaint.setColor(0xFF808080); // ポストの収集待ちはグレー
                } else {
                    ringPaint.setColor(0xFF556B2F); // 郵便局の営業開始前は灰がかった緑
                }
                break;
            case UNKNOWN:
                ringPaint.setColor(0x00000000); // 透明
                break;
        }
    }
}
