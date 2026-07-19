package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
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
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class AddPostBoxActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private AuthRepository authRepository;
    private PoiRepository repository;

    private TableLayout tableCollection;
    private final List<EditText[]> timeRows = new ArrayList<>();
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");

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

        // Edge-to-Edge adjustment
        View mainLayout = findViewById(R.id.add_scroll_view);
        if (mainLayout == null) {
            mainLayout = findViewById(android.R.id.content);
        }
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        authRepository = new AuthRepository(this);
        repository = new PoiRepositoryImpl();

        map = findViewById(R.id.add_map);
        map.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        RadioGroup radioShape = findViewById(R.id.radio_shape);
        TextInputEditText inputBranch = findViewById(R.id.input_branch);
        TextInputEditText inputNote = findViewById(R.id.input_note);
        tableCollection = findViewById(R.id.table_collection);
        Button btnAddRow = findViewById(R.id.btn_add_row);
        Button btnCopyToSat = findViewById(R.id.btn_copy_to_sat);
        Button btnCopyToSun = findViewById(R.id.btn_copy_to_sun);
        Button btnSave = findViewById(R.id.btn_add_save);

        // 既定3行追加
        for (int i = 0; i < 3; i++) {
            addNewRow();
        }

        btnAddRow.setOnClickListener(v -> addNewRow());

        btnCopyToSat.setOnClickListener(v -> {
            for (EditText[] row : timeRows) {
                row[1].setText(row[0].getText());
            }
        });

        btnCopyToSun.setOnClickListener(v -> {
            for (EditText[] row : timeRows) {
                row[2].setText(row[1].getText());
            }
        });

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
        marker.setOnMarkerClickListener((m, mv) -> true); // タッチイベントを消費して地図に伝搬させない（ドラッグ無効化を補完）
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
            String note = inputNote.getText() != null ? inputNote.getText().toString() : "";
            String collection = formatCollectionTimes();

            if (collection == null) {
                // バリデーションエラーは formatCollectionTimes 内で通知済み
                return;
            }

            new MaterialAlertDialogBuilder(this)
                .setTitle("ポストの追加")
                .setMessage("OSMに新しいポストを追加しますか？")
                .setPositiveButton("追加", (dialog, which) -> {
                    GeoPoint pos = marker.getPosition();
                    repository.addPostBox(pos.getLatitude(), pos.getLongitude(), shape, branch, collection, note, new PoiRepository.PoiSaveCallback() {
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

    private void addNewRow() {
        TableRow row = new TableRow(this);
        EditText[] rowEditors = new EditText[3];
        for (int i = 0; i < 3; i++) {
            EditText et = new EditText(this);
            et.setHint("00:00");
            et.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            et.setGravity(Gravity.CENTER);
            row.addView(et);
            rowEditors[i] = et;
        }
        tableCollection.addView(row);
        timeRows.add(rowEditors);
    }

    private String formatCollectionTimes() {
        List<String> weekday = new ArrayList<>();
        List<String> saturday = new ArrayList<>();
        List<String> holiday = new ArrayList<>();

        for (int col = 0; col < 3; col++) {
            List<String> targetList = (col == 0) ? weekday : (col == 1) ? saturday : holiday;
            int lastMinutes = -1;
            for (int r = 0; r < timeRows.size(); r++) {
                String val = timeRows.get(r)[col].getText().toString().trim();
                if (val.isEmpty()) continue;

                if (!TIME_PATTERN.matcher(val).matches()) {
                    Toast.makeText(this, "無効な時刻形式です: " + val, Toast.LENGTH_SHORT).show();
                    return null;
                }

                int minutes = parseMinutes(val);
                if (minutes <= lastMinutes) {
                    Toast.makeText(this, "時刻は昇順で入力してください", Toast.LENGTH_SHORT).show();
                    return null;
                }
                targetList.add(val);
                lastMinutes = minutes;
            }
        }

        if (weekday.isEmpty() && saturday.isEmpty() && holiday.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (!weekday.isEmpty()) {
            sb.append("Mo-Fr ").append(String.join(",", weekday));
        }
        if (!saturday.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("Sa ").append(String.join(",", saturday));
        }
        if (!holiday.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("Su,PH ").append(String.join(",", holiday));
        }

        return sb.toString();
    }

    private int parseMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
