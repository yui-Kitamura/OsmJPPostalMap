package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import java.time.LocalDate;
import java.util.Calendar;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;

public class ClockFilterButton extends AppCompatImageButton {

    private Paint paint;
    private boolean isFilterActive = false;
    private int activeColor = 0xFF81C784; // Light Green 300
    private int inactiveColor = Color.WHITE;

    public ClockFilterButton(Context context) {
        super(context);
        init();
    }

    public ClockFilterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ClockFilterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // 背景はプログラムで制御するため、XMLの背景をクリアするか、自前で描画する
        // ここではbg_menu_buttonの形状を維持したいが、要件の「背景色を変える」を優先
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
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(2);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // 現在時刻の取得
        Calendar calendar = Calendar.getInstance();
        float hours = calendar.get(Calendar.HOUR);
        float minutes = calendar.get(Calendar.MINUTE);

        // 曜日 or 祝の表示
        String label;
        SimpleScheduleParser parser = new SimpleScheduleParser();
        pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult result = 
            parser.parse("24/7", System.currentTimeMillis(), pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser.Amenity.POST_BOX);

        if (result.isHoliday()) {
            label = "祝";
        } else {
            String[] weekDays = {"", "日", "月", "火", "水", "木", "金", "土"};
            label = weekDays[calendar.get(Calendar.DAY_OF_WEEK)];
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(radius * 0.5f);
        paint.setFakeBoldText(true);
        
        if (result.isHoliday() || calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            paint.setColor(Color.RED);
        } else if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
            paint.setColor(Color.BLUE);
        } else {
            paint.setColor(Color.BLACK);
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
        paint.setColor(Color.BLACK);
        canvas.drawCircle(centerX, centerY, 4, paint);
    }

    private void drawHand(Canvas canvas, int cx, int cy, float length, float angleDegrees, float strokeWidth) {
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.BLACK);
        double angleRadians = Math.toRadians(angleDegrees - 90);
        float stopX = (float) (cx + Math.cos(angleRadians) * length);
        float stopY = (float) (cy + Math.sin(angleRadians) * length);
        canvas.drawLine(cx, cy, stopX, stopY, paint);
    }
}
