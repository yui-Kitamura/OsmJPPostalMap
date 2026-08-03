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
import pro.eng.yui.android.osmjppostalmap.core.PoiStatusDrawingUtil;
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
        int logoRed = ContextCompat.getColor(context, R.color.brand_red);
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
        PoiStatusDrawingUtil.drawStatusRing(canvas, centerX, centerY, size,
                type == SearchResult.Type.POST_OFFICE, schedule, limitedServiceSchedule,
                ringPaint, innerRingPaint, 4f);

        // 〒 記号
        symbolPaint.setTextSize(size * 1.2f);
        ScheduleResult effectiveSchedule = schedule;
        if (limitedServiceSchedule != null && 
            (limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING || 
             limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON)) {
            effectiveSchedule = limitedServiceSchedule;
        }
        
        String symbol = PoiStatusDrawingUtil.getStatusSymbol(effectiveSchedule);
        canvas.drawText(symbol, centerX, centerY + (symbolPaint.getTextSize() / 3), symbolPaint);
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
