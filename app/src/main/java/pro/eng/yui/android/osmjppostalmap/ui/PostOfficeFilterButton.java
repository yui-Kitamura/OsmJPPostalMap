package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;
import pro.eng.yui.android.osmjppostalmap.R;

public class PostOfficeFilterButton extends AppCompatImageButton {

    private Paint paint;
    private boolean isFilterActive = false;
    private int activeColor = 0xFF81C784;
    private int inactiveColor = Color.WHITE;
    private int textColor = Color.BLACK;

    public PostOfficeFilterButton(Context context) {
        super(context);
        init(context);
    }

    public PostOfficeFilterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PostOfficeFilterButton(Context context, AttributeSet attrs, int defStyleAttr) {
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
        int padding = 4;
        
        // 背景描画 (四角)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isFilterActive ? activeColor : inactiveColor);
        canvas.drawRect(padding, padding, width - padding, height - padding, paint);

        // 枠線描画
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(textColor);
        paint.setStrokeWidth(2);
        canvas.drawRect(padding, padding, width - padding, height - padding, paint);

        // 「局」ラベル描画
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
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
