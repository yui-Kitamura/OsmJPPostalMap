package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;

public class PostOfficeFilterButton extends AppCompatImageButton {

    private Paint paint;
    private boolean isFilterActive = false;
    private int activeColor = 0xFF81C784; // Light Green 300
    private int inactiveColor = Color.WHITE;

    public PostOfficeFilterButton(Context context) {
        super(context);
        init();
    }

    public PostOfficeFilterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PostOfficeFilterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setFilterActive(boolean active) {
        this.isFilterActive = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int padding = 4;
        
        // 背景描画 (四角)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isFilterActive ? activeColor : inactiveColor);
        canvas.drawRect(padding, padding, width - padding, height - padding, paint);

        // 枠線描画
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(2);
        canvas.drawRect(padding, padding, width - padding, height - padding, paint);

        // 「局」ラベル描画
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextSize(Math.min(width, height) * 0.5f);
        paint.setFakeBoldText(true);
        paint.setTextAlign(Paint.Align.CENTER);

        Rect textBounds = new Rect();
        String label = "局";
        paint.getTextBounds(label, 0, label.length(), textBounds);
        
        // 中央に描画
        float x = width / 2f;
        float y = height / 2f - textBounds.centerY();
        canvas.drawText(label, x, y, paint);
    }
}
