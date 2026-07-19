package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
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
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class EditPoiActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private TextInputEditText tagInput;
    private PoiRepository repository;
    private AuthRepository authRepository;
    private OsmPoi targetPoi;

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
        setContentView(R.layout.activity_edit_poi);

        repository = new PoiRepositoryImpl();
        authRepository = new AuthRepository(this);

        // IntentからPOI情報を受け取る
        long id = getIntent().getLongExtra("POI_ID", 0);
        String type = getIntent().getStringExtra("POI_TYPE");
        double lat = getIntent().getDoubleExtra("POI_LAT", 35.6812);
        double lon = getIntent().getDoubleExtra("POI_LON", 139.7671);
        
        java.util.Map<String, String> tags = new java.util.HashMap<>();
        if (getIntent().hasExtra("TAG_AMENITY")) tags.put("amenity", getIntent().getStringExtra("TAG_AMENITY"));
        if (getIntent().hasExtra("TAG_NAME")) tags.put("name", getIntent().getStringExtra("TAG_NAME"));
        if (getIntent().hasExtra("TAG_OPENING_HOURS")) tags.put("opening_hours", getIntent().getStringExtra("TAG_OPENING_HOURS"));
        if (getIntent().hasExtra("TAG_COLLECTION_TIMES")) tags.put("collection_times", getIntent().getStringExtra("TAG_COLLECTION_TIMES"));

        targetPoi = new OsmPoi(id, lat, lon, type != null ? type : "node", tags);

        TextView title = findViewById(R.id.edit_title);
        tagInput = findViewById(R.id.edit_tag_value);
        map = findViewById(R.id.edit_map);
        Button btnSave = findViewById(R.id.btn_save);

        String amenity = targetPoi.getTag("amenity");
        boolean isPostBox = "post_box".equals(amenity);
        title.setText(isPostBox ? "郵便ポストの編集" : "郵便局の編集");
        
        String tagName = isPostBox ? "collection_times" : "opening_hours";
        tagInput.setText(targetPoi.getTag(tagName));

        // 地図の初期化
        map.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(targetPoi.getLat(), targetPoi.getLon());
        map.getController().setZoom(19.0);
        map.getController().setCenter(startPoint);

        marker = new ReticleMarker(map);
        marker.setPosition(startPoint);
        marker.setDraggable(false);
        marker.setTitle("位置を調整");
        marker.setInfoWindow(null);
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

            new MaterialAlertDialogBuilder(this)
                .setTitle("保存の確認")
                .setMessage("OSMにこの内容を保存しますか？")
                .setPositiveButton("保存", (dialog, which) -> {
                    saveChanges();
                })
                .setNegativeButton("キャンセル", null)
                .show();
        });
    }

    private void saveChanges() {
        // タグの更新と位置の更新をリポジトリ経由で行う
        String amenity = targetPoi.getTag("amenity");
        String tagName = "post_box".equals(amenity) ? "collection_times" : "opening_hours";
        String newValue = tagInput.getText() != null ? tagInput.getText().toString() : "";
        
        targetPoi.getTags().put(tagName, newValue);
        
        // 移動後の位置を取得
        GeoPoint pos = marker.getPosition();
        OsmPoi updatedPoi = new OsmPoi(
                targetPoi.getId(),
                pos.getLatitude(),
                pos.getLongitude(),
                targetPoi.getType(),
                targetPoi.getTags()
        );

        repository.savePoi(updatedPoi, "update " + (updatedPoi.getTag("name") != null ? updatedPoi.getTag("name") : updatedPoi.getType()), new PoiRepository.PoiSaveCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(EditPoiActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(EditPoiActivity.this, "保存エラー: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
