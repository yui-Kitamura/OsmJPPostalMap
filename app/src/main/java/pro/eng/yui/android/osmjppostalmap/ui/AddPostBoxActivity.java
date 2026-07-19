package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class AddPostBoxActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private AuthRepository authRepository;
    private PoiRepository repository;

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

        // 地図の初期化 (現在地付近を想定)
        map.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(35.6812, 139.7671);
        map.getController().setZoom(18.0);
        map.getController().setCenter(startPoint);

        marker = new Marker(map);
        marker.setPosition(startPoint);
        marker.setDraggable(true);
        marker.setTitle("設置位置");
        map.getOverlays().add(marker);

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
