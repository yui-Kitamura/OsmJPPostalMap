package pro.eng.yui.android.osmjppostalmap.core;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

/**
 * POIの営業状態表示に関する共通描画ユーティリティ
 */
public class PoiStatusDrawingUtil {

    /**
     * スケジュール状態に基づいたリングの色を取得する
     */
    public static int getRingColor(ScheduleResult schedule, boolean isPostBox) {
        switch (schedule.getCurrentState()) {
            case OPENING:
                return 0xFF00FF00; // 緑
            case OPENING_BUT_EVENT_SOON:
                return 0xFFFFA500; // 橙
            case CLOSED:
            case TODAY_FINISHED:
                return 0xFF808080; // グレー
            case CLOSING_BUT_OPEN_SOON:
                return isPostBox ? 0xFF808080 : 0xFF556B2F; // ポストはグレー、局は灰緑
            case UNKNOWN:
            default:
                return 0x00000000; // 透明
        }
    }

    /**
     * スケジュール状態に基づいた記号を取得する
     */
    public static String getStatusSymbol(ScheduleResult effectiveSchedule) {
        if (effectiveSchedule == null) return "〒";
        switch (effectiveSchedule.getCurrentState()) {
            case UNKNOWN:
                return "？";
            case PARSE_ERROR:
                return "△";
            default:
                return "〒";
        }
    }

    /**
     * ステータスリングを描画する
     * 
     * @param canvas 描画先キャンバス
     * @param centerX 中心X座標
     * @param centerY 中心Y座標
     * @param size POIアイコンの基本サイズ（半径相当）
     * @param isPostOffice 郵便局の場合はtrue、ポストの場合はfalse
     * @param schedule 通常スケジュール
     * @param limitedServiceSchedule ゆうゆう窓口などの特別スケジュール
     * @param ringPaint リング用ペイント
     * @param innerRingPaint 特別スケジュール用インナーリングペイント
     * @param cornerRadius 郵便局の場合の角丸半径
     */
    public static void drawStatusRing(Canvas canvas, float centerX, float centerY, float size, 
                                     boolean isPostOffice, ScheduleResult schedule, 
                                     ScheduleResult limitedServiceSchedule, 
                                     Paint ringPaint, Paint innerRingPaint, 
                                     float cornerRadius) {
        
        ScheduleResult effectiveSchedule = schedule;
        if (limitedServiceSchedule != null && 
            (limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING || 
             limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON)) {
            effectiveSchedule = limitedServiceSchedule;
        }

        if (effectiveSchedule == null || effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.UNKNOWN) {
            return;
        }

        ringPaint.setColor(getRingColor(effectiveSchedule, !isPostOffice));
        float sweepAngle = 360f;
        long now = System.currentTimeMillis();
        
        // リングを少し外側に描画
        float ringStrokeWidth = ringPaint.getStrokeWidth();
        float ringSize = size + ringStrokeWidth + (isPostOffice ? 1f : 0.5f);
        RectF ringRect = new RectF(centerX - ringSize, centerY - ringSize, centerX + ringSize, centerY + ringSize);

        if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON && effectiveSchedule.getNextEvent() != null) {
            long remainingMillis = effectiveSchedule.getNextEvent().getTimestamp().toInstant().toEpochMilli() - now;
            float remainingMinutes = remainingMillis / 60000f;
            if (remainingMinutes < 0) remainingMinutes = 0;
            if (remainingMinutes > 60) remainingMinutes = 60;
            sweepAngle = (remainingMinutes / 60f) * 360f;
        }
        
        boolean showDot = (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON && effectiveSchedule.getNextEvent() != null);

        if (showDot) {
            // 営業開始前または収集前：緑ドットを短針の位置に配置
            if (isPostOffice) {
                canvas.drawRoundRect(ringRect, cornerRadius, cornerRadius, ringPaint);
            } else {
                canvas.drawArc(ringRect, -90f, 360f, false, ringPaint);
            }
            
            int hour = effectiveSchedule.getNextEvent().getTimestamp().getHour();
            int minute = effectiveSchedule.getNextEvent().getTimestamp().getMinute();
            float angle = (hour + minute / 60f) * 30f - 90f;
            
            Paint dotPaint = new Paint(ringPaint);
            dotPaint.setColor(0xFF00FF00); // 明るい緑
            dotPaint.setStyle(Paint.Style.FILL);
            
            float dotX = (float) (centerX + ringSize * Math.cos(Math.toRadians(angle)));
            float dotY = (float) (centerY + ringSize * Math.sin(Math.toRadians(angle)));
            // ドットのサイズは郵便局の方が少し大きくする
            float dotRadius = isPostOffice ? ringStrokeWidth : ringStrokeWidth / 2f;
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint);
        } else {
            if (effectiveSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                // 背景としてグレーのリングを描画
                Paint bgRingPaint = new Paint(ringPaint);
                bgRingPaint.setColor(0xFF808080);
                if (isPostOffice) {
                    canvas.drawRoundRect(ringRect, cornerRadius, cornerRadius, bgRingPaint);
                } else {
                    canvas.drawArc(ringRect, -90f, 360f, false, bgRingPaint);
                }
            }

            if (isPostOffice) {
                if (sweepAngle == 360f) {
                    canvas.drawRoundRect(ringRect, cornerRadius, cornerRadius, ringPaint);
                } else {
                    drawSquareGauge(canvas, ringRect, sweepAngle / 360f, cornerRadius, cornerRadius, ringPaint);
                }
            } else {
                canvas.drawArc(ringRect, -90f, sweepAngle, false, ringPaint);
            }
        }

        // ゆうゆう窓口が営業中で通常窓口が閉まっている場合、メインリングの上に淡い赤のリングを重ねる
        if (limitedServiceSchedule == effectiveSchedule && schedule != null &&
                schedule.getCurrentState() != ScheduleResult.CurrentState.OPENING &&
                schedule.getCurrentState() != ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
            if (isPostOffice) {
                canvas.drawRoundRect(ringRect, cornerRadius, cornerRadius, innerRingPaint);
            } else {
                canvas.drawArc(ringRect, -90f, 360f, false, innerRingPaint);
            }
        }
    }

    /**
     * 角丸長方形のゲージ（プログレスバー）を描画する
     */
    public static void drawSquareGauge(Canvas canvas, RectF rect, float progress, float rx, float ry, Paint paint) {
        Path path = new Path();

        // 上部中央から開始して時計回りに描画
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
}
