package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
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

        marker = new Marker(map);
        marker.setPosition(startPoint);
        marker.setDraggable(true);
        marker.setTitle("位置をドラッグして移動");
        map.getOverlays().add(marker);

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
        // 実際には新しいOsmPoiオブジェクトを作成して渡す
        Toast.makeText(this, "保存しました（モック）", Toast.LENGTH_SHORT).show();
        finish();
    }
}
