package pro.eng.yui.android.osmjppostalmap.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.core.AddressEditDialog;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class AddPostBoxActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private AuthRepository authRepository;
    private PoiRepository repository;
    private MainViewModel viewModel;
    private Button btnSave;
    private TextView addressValue;
    private final java.util.Map<String, String> addressTags = new java.util.HashMap<>();
    private int lastCheckedShapeId = -1;
    private androidx.appcompat.app.AlertDialog progressDialog;
    private Location lastLocation;
    private TableLayout tableCollection;
    private android.widget.CheckBox checkColWdOff, checkColSaOff, checkColPhOff;
    private final List<EditText[]> timeRows = new ArrayList<>();
    private final ScheduleParser scheduleParser = new SimpleScheduleParser();
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");

    private static final double MIN_ZOOM = 15.0;

    private class ReticleMarker extends Marker {
        private final android.graphics.Paint paint;

        public ReticleMarker(MapView mapView) {
            super(mapView);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(ContextCompat.getColor(AddPostBoxActivity.this, R.color.jp_post_red)); // 赤
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
        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        authRepository = new AuthRepository(this);
        if (authRepository.getAccessToken() == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("ログインが必要です")
                    .setMessage("地図メモを残すには、このまま続行してください。")
                    .setPositiveButton("ログイン", (dialog, which) -> {
                        Intent intent = new Intent(this, SettingsActivity.class);
                        startActivity(intent);
                        finish();
                    })
                    .setNeutralButton("このまま地図メモを残す", null)
                    .setNegativeButton("キャンセル", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        }

        repository = PoiRepositoryImpl.getInstance();
        ((PoiRepositoryImpl)repository).setAccessToken(authRepository.getAccessToken());

        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(MainViewModel.class);
        viewModel.getLocation().observe(this, location -> {
            lastLocation = location;
        });

        map = findViewById(R.id.add_map);
        map.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        RadioGroup radioShape = findViewById(R.id.radio_shape);
        for (int i = 0; i < radioShape.getChildCount(); i++) {
            View v = radioShape.getChildAt(i);
            if (v instanceof RadioButton) {
                v.setOnClickListener(view -> {
                    if (view.getId() == lastCheckedShapeId) {
                        radioShape.clearCheck();
                        lastCheckedShapeId = -1;
                    } else {
                        lastCheckedShapeId = view.getId();
                    }
                });
            }
        }
        lastCheckedShapeId = radioShape.getCheckedRadioButtonId();

        TextInputEditText inputBranch = findViewById(R.id.input_branch);
        TextInputEditText inputRef = findViewById(R.id.input_ref);
        TextInputEditText inputNote = findViewById(R.id.input_note);
        tableCollection = findViewById(R.id.table_collection);
        Button btnAddRow = findViewById(R.id.btn_add_row);
        Button btnCopyToSat = findViewById(R.id.btn_copy_to_sat);
        Button btnCopyToSun = findViewById(R.id.btn_copy_to_sun);
        checkColWdOff = findViewById(R.id.check_col_wd_off);
        checkColSaOff = findViewById(R.id.check_col_sa_off);
        checkColPhOff = findViewById(R.id.check_col_ph_off);

        android.widget.CompoundButton.OnCheckedChangeListener colOffListener = (buttonView, isChecked) -> {
            int col;
            if (buttonView == checkColWdOff) {
                col = 0;
            } else if (buttonView == checkColSaOff) {
                col = 1;
            } else {
                col = 2;
            }
            for (EditText[] row : timeRows) {
                row[col].setEnabled(!isChecked);
                row[col].setAlpha(isChecked ? 0.5f : 1.0f);
            }
        };
        checkColWdOff.setOnCheckedChangeListener(colOffListener);
        checkColSaOff.setOnCheckedChangeListener(colOffListener);
        checkColPhOff.setOnCheckedChangeListener(colOffListener);

        btnSave = findViewById(R.id.btn_add_save);
        addressValue = findViewById(R.id.add_address_value);
        View btnAddressEdit = findViewById(R.id.btn_address_edit);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

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

        showAddress();
        btnAddressEdit.setOnClickListener(v ->
                AddressEditDialog.show(this, JpAddress.of(addressTags), edited -> {
                    AddressEditDialog.applyTo(addressTags, edited);
                    showAddress();
                }));

        // 地図の初期化 (MainActivityからの遷移時はその中心座標を使用)
        map.setTileSource(new XYTileSource("OSMJP", (int) MIN_ZOOM, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(MIN_ZOOM);
        double lat = getIntent().getDoubleExtra("LATITUDE", 35.6812);
        double lon = getIntent().getDoubleExtra("LONGITUDE", 139.7671);
        double zoom = getIntent().getDoubleExtra("ZOOM_LEVEL", 18.0);
        GeoPoint startPoint = new GeoPoint(lat, lon);
        map.getController().setZoom(zoom);
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
            if (lastLocation == null || lastLocation.getAccuracy() > 50) {
                Toast.makeText(this, "現地入力が必須です", Toast.LENGTH_LONG).show();
                return;
            }

            int selectedShapeId = radioShape.getCheckedRadioButtonId();
            String shape = selectedShapeId != -1 ? ((RadioButton)findViewById(selectedShapeId)).getText().toString() : "";
            String branch = inputBranch.getText() != null ? inputBranch.getText().toString() : "";
            String ref = inputRef.getText() != null ? inputRef.getText().toString() : "";
            String note = inputNote.getText() != null ? inputNote.getText().toString() : "";
            String collection = formatCollectionTimes();

            if (collection == null) {
                // バリデーションエラーは formatCollectionTimes 内で通知済み
                return;
            }

            if (!authRepository.isLoggedIn()) {
                // 地図メモとして保存
                GeoPoint pos = marker.getPosition();
                String addr = JpPostalUtil.getAddressText(addressTags);
                
                String memo = String.format("ポストの情報（現地確認）\n収集時刻 = %s\n住所 = %s\n参照番号 = %s",
                        collection, addr, ref);
                JpPostalUtil.callOsmCreateNote(null, getString(R.string.app_name), memo, pos.getLatitude(), pos.getLongitude());
                Toast.makeText(this, "地図メモを保存しました", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            new MaterialAlertDialogBuilder(this)
                .setTitle("ポストの追加")
                .setMessage("OSMに新しいポストを追加しますか？")
                .setPositiveButton("追加", (dialog, which) -> {
                    if (btnSave != null) {
                        btnSave.setEnabled(false);
                    }
                    showProgress("処理を開始中…");
                    GeoPoint pos = marker.getPosition();
                    repository.addPostBox(pos.getLatitude(), pos.getLongitude(), shape, branch, ref, collection, note, addressTags, new PoiRepository.PoiSaveCallback() {
                        @Override
                        public void onSuccess() {
                            dismissProgress();
                            if (btnSave != null) {
                                btnSave.setEnabled(true);
                            }
                            Toast.makeText(AddPostBoxActivity.this, "追加しました", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        @Override
                        public void onError(String message) {
                            dismissProgress();
                            if (btnSave != null) {
                                btnSave.setEnabled(true);
                            }
                            Toast.makeText(AddPostBoxActivity.this, "エラー: " + message, Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onProgress(String message) {
                            showProgress(message);
                        }
                    });
                })
                .setNegativeButton("キャンセル", null)
                .show();
        });
    }

    private void showProgress(String message) {
        if (progressDialog == null) {
            View view = getLayoutInflater().inflate(R.layout.dialog_progress, null);
            TextView tvMessage = view.findViewById(R.id.progress_message);
            tvMessage.setText(message);
            progressDialog = new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        } else {
            TextView tvMessage = progressDialog.findViewById(R.id.progress_message);
            if (tvMessage != null) {
                tvMessage.setText(message);
            }
        }
    }

    private void dismissProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void showAddress() {
        String text = JpPostalUtil.getAddressText(addressTags);
        addressValue.setText(text.isEmpty() ? "データなし" : text);
    }

    private void addNewRow() {
        TableRow row = new TableRow(this);
        EditText[] rowEditors = new EditText[3];
        for (int i = 0; i < 3; i++) {
            EditText et = new EditText(this);
            et.setHint("--:--");
            et.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            et.setGravity(Gravity.CENTER);

            // 初期の見た目設定
            applyCellStyles(et, "", false);

            et.addTextChangedListener(new TextWatcher() {
                private final String originalValue = "";
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    boolean isModified = !s.toString().equals(originalValue);
                    applyCellStyles(et, s.toString(), isModified);
                }
            });

            row.addView(et);
            rowEditors[i] = et;

            // 収集なしチェックボックスの状態を反映
            android.widget.CheckBox checkColOff = (i == 0) ? checkColWdOff : (i == 1 ? checkColSaOff : checkColPhOff);
            if (checkColOff != null && checkColOff.isChecked()) {
                et.setEnabled(false);
                et.setAlpha(0.5f);
            }
        }
        tableCollection.addView(row);
        timeRows.add(rowEditors);
    }

    private void applyCellStyles(EditText et, String value, boolean isModified) {
        LayerDrawable bg = (LayerDrawable) ContextCompat.getDrawable(this, R.drawable.bg_edit_time_cell);
        if (bg == null) return;
        bg = (LayerDrawable) bg.mutate();

        GradientDrawable border = (GradientDrawable) bg.findDrawableByLayerId(R.id.cell_border);
        GradientDrawable background = (GradientDrawable) bg.findDrawableByLayerId(R.id.cell_background);

        if (value.isEmpty()) {
            // 未入力
            background.setColor(ContextCompat.getColor(this, R.color.gray_bg));
            et.setTextColor(ContextCompat.getColor(this, R.color.gray_light));
            et.setHintTextColor(ContextCompat.getColor(this, R.color.gray_light));
        } else {
            // 入力済み
            background.setColor(ContextCompat.getColor(this, R.color.white));
            et.setTextColor(ContextCompat.getColor(this, R.color.black));
        }

        if (isModified) {
            border.setColor(ContextCompat.getColor(this, R.color.blue_frame));
        } else {
            border.setColor(android.graphics.Color.TRANSPARENT);
        }

        et.setBackground(bg);
    }

    private String formatCollectionTimes() {
        java.util.Map<Days, List<? extends ITagPart>> weeklyTable = new HashMap<>();

        for (int col = 0; col < 3; col++) {
            android.widget.CheckBox checkColOff = (col == 0) ? checkColWdOff : (col == 1 ? checkColSaOff : checkColPhOff);
            List<CollectionTime> targetList = null;

            if (checkColOff.isChecked()) {
                targetList = new ArrayList<>(); // off;
            } else {
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
                    if (targetList == null) targetList = new ArrayList<>();
                    targetList.add(new CollectionTime(val));
                    lastMinutes = minutes;
                }
            }

            if (targetList != null) {
                if (col == 0) {
                    weeklyTable.put(Days.MONDAY, targetList);
                    weeklyTable.put(Days.TUESDAY, targetList);
                    weeklyTable.put(Days.WEDNESDAY, targetList);
                    weeklyTable.put(Days.THURSDAY, targetList);
                    weeklyTable.put(Days.FRIDAY, targetList);
                } else if (col == 1) {
                    weeklyTable.put(Days.SATURDAY, targetList);
                } else {
                    weeklyTable.put(Days.SUNDAY, targetList);
                    weeklyTable.put(Days.PUBLIC_HOLIDAY, targetList);
                }
            }
        }

        return scheduleParser.format(weeklyTable, ScheduleParser.TimeType.COLLECTION_TIMES);
    }

    private int parseMinutes(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.stopLocationUpdates();
    }
}
