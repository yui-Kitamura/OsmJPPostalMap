package pro.eng.yui.android.osmjppostalmap.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import androidx.core.content.ContextCompat;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

import java.util.Calendar;

/**
 * 郵便局とポストのカスタムマーカー
 */
public class PoiMarker extends Marker {

    private final PoiType poiType;
    private ScheduleResult schedule;
    private ScheduleResult limitedServiceSchedule;
    private final Paint ringPaint;
    private final Paint innerRingPaint;
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
        ringPaint.setStrokeWidth(6f);

        innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerRingPaint.setStyle(Paint.Style.STROKE);
        innerRingPaint.setStrokeWidth(8f);
        innerRingPaint.setColor(0xFFFF8888); // 淡い赤
        
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int logoRed = ContextCompat.getColor(mapView.getContext(), R.color.jp_post_red);
        bgPaint.setColor((logoRed & 0x00FFFFFF) | 0xCC000000); // 日本郵便カラー (Red) with alpha
        
        symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(0xFFFFFFFF);
        symbolPaint.setTextSize(30f);
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

    public ScheduleResult getLimitedServiceSchedule() {
        return limitedServiceSchedule;
    }

    public void setLimitedServiceSchedule(ScheduleResult limitedServiceSchedule) {
        this.limitedServiceSchedule = limitedServiceSchedule;
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
        PoiStatusDrawingUtil.drawStatusRing(canvas, screenPos.x, screenPos.y, size,
                poiType == PoiType.POST_OFFICE, schedule, limitedServiceSchedule,
                ringPaint, innerRingPaint, 10f);

        // 〒 記号
        ScheduleResult effectiveSchedule = schedule;
        if (limitedServiceSchedule != null && 
            (limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING || 
             limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON)) {
            effectiveSchedule = limitedServiceSchedule;
        }
        
        String symbol = PoiStatusDrawingUtil.getStatusSymbol(effectiveSchedule);
        canvas.drawText(symbol, screenPos.x, screenPos.y + (symbolPaint.getTextSize() / 3), symbolPaint);
    }

    private void updateRingPaint(ScheduleResult schedule) {
        ringPaint.setColor(PoiStatusDrawingUtil.getRingColor(schedule, poiType == PoiType.POST_BOX));
    }

    private void drawSquareGauge(Canvas canvas, RectF rect, float progress, Paint paint) {
        PoiStatusDrawingUtil.drawSquareGauge(canvas, rect, progress, 10f, 10f, paint);
    }
}
