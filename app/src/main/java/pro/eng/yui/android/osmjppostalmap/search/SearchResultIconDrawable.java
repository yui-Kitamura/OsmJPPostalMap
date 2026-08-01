package pro.eng.yui.android.osmjppostalmap.search;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

public class SearchResultIconDrawable extends Drawable {

    private final SearchResult.Type type;
    private final ScheduleResult schedule;
    private final ScheduleResult limitedServiceSchedule;
    
    private final Paint ringPaint;
    private final Paint innerRingPaint;
    private final Paint bgPaint;
    private final Paint symbolPaint;
    
    public SearchResultIconDrawable(Context context, SearchResult.Type type, ScheduleResult schedule, ScheduleResult limitedServiceSchedule) {
        this.type = type;
        this.schedule = schedule;
        this.limitedServiceSchedule = limitedServiceSchedule;
        
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerRingPaint.setStyle(Paint.Style.STROKE);
        innerRingPaint.setStrokeWidth(4f);
        innerRingPaint.setColor(0xFFFF8888); // 淡い赤
        
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int logoRed = ContextCompat.getColor(context, R.color.jp_post_red);
        bgPaint.setColor((logoRed & 0x00FFFFFF) | 0xCC000000);
        
        symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        symbolPaint.setColor(0xFFFFFFFF);
        symbolPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float centerX = bounds.centerX();
        float centerY = bounds.centerY();
        float size = Math.min(bounds.width(), bounds.height()) / 2.5f;
        
        RectF rect = new RectF(centerX - size, centerY - size, centerX + size, centerY + size);

        // 背景
        if (type == SearchResult.Type.POST_OFFICE) {
            canvas.drawRoundRect(rect, 4f, 4f, bgPaint);
        } else {
            canvas.drawCircle(centerX, centerY, size, bgPaint);
        }

        // 外周リング
        ScheduleResult effectiveSchedule = schedule;
        if (limitedServiceSchedule != null && 
            (limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING || 
             limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON)) {
            effectiveSchedule = limitedServiceSchedule;
        }

        if (effectiveSchedule != null && effectiveSchedule.getCurrentState() != ScheduleResult.CurrentState.UNKNOWN) {
            updateRingPaint(effectiveSchedule);
            float sweepAngle = 360f;
            long now = System.currentTimeMillis();
            
            float ringSize = size + ringPaint.getStrokeWidth() + 0.5f;
            RectF ringRect = new RectF(centerX - ringSize, centerY - ringSize, centerX + ringSize, centerY + ringSize);

            if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON && effectiveSchedule.getNextEvent() != null) {
                long remainingMillis = effectiveSchedule.getNextEvent().getTimestamp().toInstant().toEpochMilli() - now;
                float remainingMinutes = remainingMillis / 60000f;
                if (remainingMinutes < 0) remainingMinutes = 0;
                if (remainingMinutes > 60) remainingMinutes = 60;
                sweepAngle = (remainingMinutes / 60f) * 360f;
            }
            
            boolean showDot = false;
            if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON && effectiveSchedule.getNextEvent() != null) {
                showDot = true;
            }

            if (showDot) {
                if (type == SearchResult.Type.POST_OFFICE) {
                    canvas.drawRoundRect(ringRect, 4f, 4f, ringPaint);
                } else {
                    canvas.drawArc(ringRect, -90f, 360f, false, ringPaint);
                }
                
                int hour = effectiveSchedule.getNextEvent().getTimestamp().getHour();
                int minute = effectiveSchedule.getNextEvent().getTimestamp().getMinute();
                float angle = (hour + minute / 60f) * 30f - 90f;
                
                Paint dotPaint = new Paint(ringPaint);
                dotPaint.setColor(0xFF00FF00);
                dotPaint.setStyle(Paint.Style.FILL);
                
                float dotX = (float) (centerX + ringSize * Math.cos(Math.toRadians(angle)));
                float dotY = (float) (centerY + ringSize * Math.sin(Math.toRadians(angle)));
                canvas.drawCircle(dotX, dotY, 3f, dotPaint);
            } else {
                if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                    Paint bgRingPaint = new Paint(ringPaint);
                    bgRingPaint.setColor(0xFF808080);
                    if (type == SearchResult.Type.POST_OFFICE) {
                        canvas.drawRoundRect(ringRect, 4f, 4f, bgRingPaint);
                    } else {
                        canvas.drawArc(ringRect, -90f, 360f, false, bgRingPaint);
                    }
                }

                if (type == SearchResult.Type.POST_OFFICE) {
                    if (sweepAngle == 360f) {
                        canvas.drawRoundRect(ringRect, 4f, 4f, ringPaint);
                    } else {
                        drawSquareGauge(canvas, ringRect, sweepAngle / 360f, ringPaint);
                    }
                } else {
                    canvas.drawArc(ringRect, -90f, sweepAngle, false, ringPaint);
                }
            }

            if (limitedServiceSchedule == effectiveSchedule && schedule != null &&
                    schedule.getCurrentState() != ScheduleResult.CurrentState.OPENING &&
                    schedule.getCurrentState() != ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                if (type == SearchResult.Type.POST_OFFICE) {
                    canvas.drawRoundRect(ringRect, 4f, 4f, innerRingPaint);
                } else {
                    canvas.drawArc(ringRect, -90f, 360f, false, innerRingPaint);
                }
            }
        }

        // 〒 記号
        symbolPaint.setTextSize(size * 1.2f);
        String symbol = "〒";
        if (effectiveSchedule != null) {
            if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.UNKNOWN) {
                symbol = "？";
            } else if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.PARSE_ERROR) {
                symbol = "△";
            }
        }
        canvas.drawText(symbol, centerX, centerY + (symbolPaint.getTextSize() / 3), symbolPaint);
    }

    private void updateRingPaint(ScheduleResult schedule) {
        switch (schedule.getCurrentState()) {
            case OPENING:
                ringPaint.setColor(0xFF00FF00);
                break;
            case OPENING_BUT_EVENT_SOON:
                ringPaint.setColor(0xFFFFA500);
                break;
            case CLOSED:
            case TODAY_FINISHED:
                ringPaint.setColor(0xFF808080);
                break;
            case CLOSING_BUT_OPEN_SOON:
                if (type == SearchResult.Type.POST_BOX) {
                    ringPaint.setColor(0xFF808080);
                } else {
                    ringPaint.setColor(0xFF556B2F);
                }
                break;
            case UNKNOWN:
                ringPaint.setColor(0x00000000);
                break;
        }
    }

    private void drawSquareGauge(Canvas canvas, RectF rect, float progress, Paint paint) {
        Path path = new Path();
        float rx = 4f;
        float ry = 4f;

        path.moveTo(rect.centerX(), rect.top);
        path.lineTo(rect.right - rx, rect.top);
        path.arcTo(new RectF(rect.right - 2 * rx, rect.top, rect.right, rect.top + 2 * ry), -90, 90);
        path.lineTo(rect.right, rect.bottom - ry);
        path.arcTo(new RectF(rect.right - 2 * rx, rect.bottom - 2 * ry, rect.right, rect.bottom), 0, 90);
        path.lineTo(rect.left + rx, rect.bottom);
        path.arcTo(new RectF(rect.left, rect.bottom - 2 * ry, rect.left + 2 * rx, rect.bottom), 90, 90);
        path.lineTo(rect.left, rect.top + ry);
        path.arcTo(new RectF(rect.left, rect.top, rect.left + 2 * rx, rect.top + 2 * ry), 180, 90);
        path.lineTo(rect.centerX(), rect.top);

        PathMeasure pm = new PathMeasure(path, false);
        float length = pm.getLength();
        Path dst = new Path();
        pm.getSegment(0, length * progress, dst, true);
        canvas.drawPath(dst, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        ringPaint.setAlpha(alpha);
        innerRingPaint.setAlpha(alpha);
        symbolPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        bgPaint.setColorFilter(colorFilter);
        ringPaint.setColorFilter(colorFilter);
        innerRingPaint.setColorFilter(colorFilter);
        symbolPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
