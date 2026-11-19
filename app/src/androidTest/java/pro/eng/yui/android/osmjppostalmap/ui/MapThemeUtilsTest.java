package pro.eng.yui.android.osmjppostalmap.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.CustomZoomButtonsDisplay;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.concurrent.atomic.AtomicInteger;

import pro.eng.yui.android.osmjppostalmap.R;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class MapThemeUtilsTest {

    @Test
    public void zoomControlsUseThemeColorsAndRemainInteractive() {
        assertZoomControlsForNightMode(Configuration.UI_MODE_NIGHT_NO);
        assertZoomControlsForNightMode(Configuration.UI_MODE_NIGHT_YES);
    }

    @Test
    public void modifiedTimeCellColorsRemainReadableInBothThemes() {
        assertCellContrast(Configuration.UI_MODE_NIGHT_NO);
        assertCellContrast(Configuration.UI_MODE_NIGHT_YES);
    }

    @Test
    public void tileColorsAreInvertedOnlyInDarkTheme() {
        assertTileColorFilter(Configuration.UI_MODE_NIGHT_NO, null);
        assertTileColorFilter(Configuration.UI_MODE_NIGHT_YES, TilesOverlay.INVERT_COLORS);
    }

    private void assertZoomControlsForNightMode(int nightMode) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = themedContext(nightMode);
            MapView mapView = new MapView(context);
            int buttonSize = MapThemeUtils.getZoomButtonSizePx(context);
            mapView.layout(0, 0, buttonSize * 6, buttonSize * 10);
            MapThemeUtils.applyZoomButtonTheme(mapView);

            CustomZoomButtonsDisplay display = mapView.getZoomController().getDisplay();
            display.setPositions(true, CustomZoomButtonsDisplay.HorizontalPosition.LEFT,
                    CustomZoomButtonsDisplay.VerticalPosition.TOP);
            display.setMarginPadding(0f, 0f);

            Bitmap rendered = Bitmap.createBitmap(mapView.getWidth(), mapView.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(rendered);
            display.draw(canvas, 1f, true, true);
            assertEquals(ContextCompat.getColor(context, R.color.map_zoom_button_bg), rendered.getPixel(1, 1));
            assertEquals(ContextCompat.getColor(context, R.color.map_zoom_button_icon),
                    rendered.getPixel(buttonSize / 2, buttonSize / 2));

            rendered.eraseColor(Color.TRANSPARENT);
            display.draw(canvas, 1f, false, false);
            assertEquals(ContextCompat.getColor(context, R.color.map_zoom_button_bg_disabled), rendered.getPixel(1, 1));
            assertEquals(ContextCompat.getColor(context, R.color.map_zoom_button_icon_disabled),
                    rendered.getPixel(buttonSize / 2, buttonSize / 2));

            CustomZoomButtonsController controller = mapView.getZoomController();
            AtomicInteger zoomDirection = new AtomicInteger();
            controller.setOnZoomListener(new CustomZoomButtonsController.OnZoomListener() {
                @Override
                public void onVisibilityChanged(boolean visible) {
                }

                @Override
                public void onZoom(boolean zoomIn) {
                    zoomDirection.set(zoomIn ? 1 : -1);
                }
            });
            controller.setZoomInEnabled(true);
            controller.setZoomOutEnabled(true);
            controller.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS);
            MotionEvent tapZoomIn = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP,
                    buttonSize * 1.5f, buttonSize / 2f, 0);
            try {
                assertTrue(controller.isTouched(tapZoomIn));
            } finally {
                tapZoomIn.recycle();
            }
            assertEquals(1, zoomDirection.get());

            controller.setZoomInEnabled(false);
            zoomDirection.set(0);
            MotionEvent tapDisabledZoomIn = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP,
                    buttonSize * 1.5f, buttonSize / 2f, 0);
            try {
                assertTrue(controller.isTouched(tapDisabledZoomIn));
            } finally {
                tapDisabledZoomIn.recycle();
            }
            assertEquals(0, zoomDirection.get());
            mapView.onDetach();
        });
    }

    private void assertCellContrast(int nightMode) {
        Context context = themedContext(nightMode);
        int modifiedBackground = ContextCompat.getColor(context, R.color.modified_cell_bg);
        int regularBackground = ContextCompat.getColor(context, R.color.cell_bg);
        int emptyBackground = ContextCompat.getColor(context, R.color.gray_bg);
        TypedValue onSurfaceValue = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface,
                onSurfaceValue, true));
        int foreground = onSurfaceValue.data;
        int secondaryForeground = ContextCompat.getColor(context, R.color.text_secondary);
        assertTrue(contrastRatio(foreground, modifiedBackground) >= 4.5);
        assertTrue(contrastRatio(foreground, regularBackground) >= 4.5);
        assertTrue(contrastRatio(secondaryForeground, emptyBackground) >= 3.0);
        assertTrue(modifiedBackground != regularBackground);
        assertTrue(modifiedBackground != emptyBackground);
        assertTrue(regularBackground != emptyBackground);
    }

    private void assertTileColorFilter(int nightMode, ColorFilter expectedFilter) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = themedContext(nightMode);
            MapView mapView = new MapView(context);
            ColorFilter[] appliedFilter = new ColorFilter[1];
            TilesOverlay overlay = new TilesOverlay(mapView.getTileProvider(), context) {
                @Override
                public void setColorFilter(ColorFilter filter) {
                    super.setColorFilter(filter);
                    appliedFilter[0] = filter;
                }
            };
            mapView.getOverlayManager().setTilesOverlay(overlay);
            overlay.setColorFilter(expectedFilter == null ? TilesOverlay.INVERT_COLORS : null);

            MapThemeUtils.applyTileColorFilter(mapView);
            assertSame(expectedFilter, appliedFilter[0]);
            mapView.onDetach();
        });
    }

    private Context themedContext(int nightMode) {
        Context base = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return new ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_OsmJPPostalMap);
    }

    private double contrastRatio(int foreground, int background) {
        double foregroundLuminance = luminance(foreground);
        double backgroundLuminance = luminance(background);
        return (Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
                / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05);
    }

    private double luminance(int color) {
        return 0.2126 * linearize(Color.red(color) / 255.0)
                + 0.7152 * linearize(Color.green(color) / 255.0)
                + 0.0722 * linearize(Color.blue(color) / 255.0);
    }

    private double linearize(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
