package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;

import androidx.core.content.ContextCompat;
import pro.eng.yui.android.osmjppostalmap.R;

public class CooldownRefreshButton extends AppCompatImageButton {

    private Paint paint;
    private long cooldownInterval = 1;
    private long cooldownRemaining = 0;
    private boolean loading;
    private float loadingRotation;
    private int ringColor; // Red
    private final RectF arcBounds = new RectF();

    public CooldownRefreshButton(Context context) {
        super(context);
        init();
    }

    public CooldownRefreshButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CooldownRefreshButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        ringColor = ContextCompat.getColor(getContext(), R.color.brand_red);
    }

    public void setCooldown(long remaining, long interval) {
        this.cooldownRemaining = remaining;
        this.cooldownInterval = interval > 0 ? interval : 1;
        invalidate();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        if (loading) {
            loadingRotation = 0f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!loading && cooldownRemaining <= 0) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        float strokeWidth = 6f;
        float padding = strokeWidth / 2f + 2f;

        arcBounds.set(padding, padding, width - padding, height - padding);

        float sweepAngle = loading ? 280f : 360f * cooldownRemaining / cooldownInterval;
        
        paint.setColor(ringColor);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        
        // 上(270度)から時計回りに描画
        canvas.drawArc(arcBounds, 270f + loadingRotation, -sweepAngle, false, paint);
        if (loading) {
            loadingRotation = (loadingRotation + 8f) % 360f;
            postInvalidateOnAnimation();
        }
    }
}
