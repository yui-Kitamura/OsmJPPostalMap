package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;

import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.MapView;

import pro.eng.yui.android.osmjppostalmap.R;

final class MapThemeUtils {

    private static final int ZOOM_BUTTON_SIZE_DP = 36;

    private MapThemeUtils() {
    }

    static void applyZoomButtonTheme(MapView mapView) {
        Context context = mapView.getContext();
        int background = ContextCompat.getColor(context, R.color.map_zoom_button_bg);
        int disabledBackground = ContextCompat.getColor(context, R.color.map_zoom_button_bg_disabled);
        int icon = ContextCompat.getColor(context, R.color.map_zoom_button_icon);
        int disabledIcon = ContextCompat.getColor(context, R.color.map_zoom_button_icon_disabled);

        CustomZoomButtonsDisplay display = mapView.getZoomController().getDisplay();
        display.setBitmaps(
                createZoomButton(context, true, background, icon),
                createZoomButton(context, true, disabledBackground, disabledIcon),
                createZoomButton(context, false, background, icon),
                createZoomButton(context, false, disabledBackground, disabledIcon));
    }

    static int getZoomButtonSizePx(Context context) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                ZOOM_BUTTON_SIZE_DP,
                context.getResources().getDisplayMetrics());
    }

    private static Bitmap createZoomButton(Context context, boolean zoomIn, int backgroundColor, int iconColor) {
        int size = getZoomButtonSizePx(context);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(backgroundColor);
        canvas.drawRect(0, 0, size, size, paint);

        paint.setColor(iconColor);
        paint.setStrokeWidth(Math.max(1f, context.getResources().getDisplayMetrics().density * 2f));
        paint.setStrokeCap(Paint.Cap.SQUARE);
        float center = size / 2f;
        float iconInset = size * 0.3f;
        canvas.drawLine(iconInset, center, size - iconInset, center, paint);
        if (zoomIn) {
            canvas.drawLine(center, iconInset, center, size - iconInset, paint);
        }
        return bitmap;
    }
}
