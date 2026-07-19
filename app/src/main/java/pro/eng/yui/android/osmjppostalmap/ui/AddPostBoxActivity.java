package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class AddPostBoxActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private AuthRepository authRepository;
    private PoiRepository repository;

    private static class ReticleMarker extends Marker {
        private final android.graphics.Paint paint;

        public ReticleMarker(MapView mapView) {
            super(mapView);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFFFF0000); // 赤
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
        }

        @Override
        public void draw(android.graphics.Canvas canvas, MapView mapView, boolean shadow) {
            if (shadow) return;
            android.graphics.Point screenPos = new android.graphics.Point();
            mapView.getProjection().toPixels(getPosition(), screenPos);

            float radius = 40f;
            float centerGap = 10f;

            // 円を描画
            canvas.drawCircle(screenPos.x, screenPos.y, radius, paint);

            // 十字を描画 (中心は空白)
            // 上
            canvas.drawLine(screenPos.x, screenPos.y - radius, screenPos.x, screenPos.y - centerGap, paint);
            // 下
            canvas.drawLine(screenPos.x, screenPos.y + centerGap, screenPos.x, screenPos.y + radius, paint);
            // 左
            canvas.drawLine(screenPos.x - radius, screenPos.y, screenPos.x - centerGap, screenPos.y, paint);
            // 右
            canvas.drawLine(screenPos.x + centerGap, screenPos.y, screenPos.x + radius, screenPos.y, paint);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_postbox);

        authRepository = new AuthRepository(this);
        repository = new PoiRepositoryImpl();

        map = findViewById(R.id.add_map);
        RadioGroup radioShape = findViewById(R.id.radio_shape);
        TextInputEditText inputBranch = findViewById(R.id.input_branch);
        TextInputEditText inputCollection = findViewById(R.id.input_collection);
        Button btnSave = findViewById(R.id.btn_add_save);

        // 地図の初期化 (MainActivityからの遷移時はその中心座標を使用)
        map.setMultiTouchControls(true);
        double lat = getIntent().getDoubleExtra("LATITUDE", 35.6812);
        double lon = getIntent().getDoubleExtra("LONGITUDE", 139.7671);
        GeoPoint startPoint = new GeoPoint(lat, lon);
        map.getController().setZoom(18.0);
        map.getController().setCenter(startPoint);

        marker = new ReticleMarker(map);
        marker.setPosition(startPoint);
        marker.setDraggable(false); // 中心固定にするためドラッグ不可にする
        marker.setTitle("設置位置");
        marker.setInfoWindow(null); // 中心固定なのでInfoWindowは不要または自動表示されないようにする
        map.getOverlays().add(marker);

        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                marker.setPosition((GeoPoint) map.getMapCenter());
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                marker.setPosition((GeoPoint) map.getMapCenter());
                return true;
            }
        });

        btnSave.setOnClickListener(v -> {
            if (!authRepository.isLoggedIn()) {
                Toast.makeText(this, "ログインが必要です", Toast.LENGTH_SHORT).show();
                return;
            }

            String shape = ((RadioButton)findViewById(radioShape.getCheckedRadioButtonId())).getText().toString();
            String branch = inputBranch.getText() != null ? inputBranch.getText().toString() : "";
            String collection = inputCollection.getText() != null ? inputCollection.getText().toString() : "";

            new MaterialAlertDialogBuilder(this)
                .setTitle("ポストの追加")
                .setMessage("OSMに新しいポストを追加しますか？")
                .setPositiveButton("追加", (dialog, which) -> {
                    GeoPoint pos = marker.getPosition();
                    repository.addPostBox(pos.getLatitude(), pos.getLongitude(), shape, branch, collection, new PoiRepository.PoiSaveCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(AddPostBoxActivity.this, "追加しました", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        @Override
                        public void onError(String message) {
                            Toast.makeText(AddPostBoxActivity.this, "エラー: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("キャンセル", null)
                .show();
        });
    }
}
