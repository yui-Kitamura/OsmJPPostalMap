package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import java.time.LocalDateTime;

import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.Days;

public class ClockFilterButton extends AppCompatImageButton {

    private Paint paint;
    private boolean isFilterActive = false;
    private int activeColor = 0xFF81C784;
    private int inactiveColor = Color.WHITE;
    private int textColor = Color.BLACK;

    public ClockFilterButton(Context context) {
        super(context);
        init(context);
    }

    public ClockFilterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ClockFilterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(R.attr.colorEditable, typedValue, true);
        activeColor = typedValue.data;
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, typedValue, true);
        inactiveColor = typedValue.data;
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        textColor = typedValue.data;
    }

    public void setFilterActive(boolean active) {
        this.isFilterActive = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 2 - 4; // 少し余裕を持たせる

        // 背景描画
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isFilterActive ? activeColor : inactiveColor);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 枠線描画
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(textColor);
        paint.setStrokeWidth(2);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 現在時刻の取得
        LocalDateTime now = LocalDateTime.now(JpPostalUtil.JST);
        int hours = now.getHour();
        int minutes = now.getMinute();

        // 曜日 or 祝の表示
        Days today = Days.values()[now.getDayOfWeek().getValue() -1];
        String label = today.jaLabel;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(radius * 0.6f);
        paint.setFakeBoldText(true);
        
        if (today == Days.SUNDAY || today == Days.PUBLIC_HOLIDAY) {
            paint.setColor(Color.RED);
        } else if (today == Days.SATURDAY) {
            paint.setColor(Color.BLUE);
        } else {
            paint.setColor(textColor);
        }

        paint.setTextAlign(Paint.Align.CENTER);

        Rect textBounds = new Rect();
        paint.getTextBounds(label, 0, label.length(), textBounds);
        // 盤面の上部中央付近に描画
        canvas.drawText(label, centerX, centerY - radius * 0.4f, paint);

        // 各描画後にPaintの状態をリセット
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);

        // 短針の描画
        float hourAngle = (hours + minutes / 60f) * 30f; // 360 / 12 = 30
        drawHand(canvas, centerX, centerY, radius * 0.5f, hourAngle, 4);

        // 長針の描画
        float minuteAngle = minutes * 6f; // 360 / 60 = 6
        drawHand(canvas, centerX, centerY, radius * 0.8f, minuteAngle, 2);

        // 中心点
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
        canvas.drawCircle(centerX, centerY, 4, paint);
    }

    private void drawHand(Canvas canvas, int cx, int cy, float length, float angleDegrees, float strokeWidth) {
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(textColor);
        double angleRadians = Math.toRadians(angleDegrees - 90);
        float stopX = (float) (cx + Math.cos(angleRadians) * length);
        float stopY = (float) (cy + Math.sin(angleRadians) * length);
        canvas.drawLine(cx, cy, stopX, stopY, paint);
    }
}
