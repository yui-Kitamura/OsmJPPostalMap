package pro.eng.yui.android.osmjppostalmap.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class EditPoiActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private TextInputEditText tagInput;
    private TableLayout tableCollection;
    private TextView textFallback;
    private View layoutFallback;
    private final List<EditText[]> timeRows = new ArrayList<>();
    private EditText editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd;
    private EditText editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd;
    private EditText editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd;
    private android.widget.CheckBox checkOhWdOff, checkOhSaOff, checkOhPhOff;
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    private PoiRepository repository;
    private AuthRepository authRepository;
    private OsmPoi targetPoi;
    private final ScheduleParser scheduleParser = new SimpleScheduleParser();

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

        // Edge-to-Edge adjustment
        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, Math.max(systemBars.bottom, ime.bottom));
            return insets;
        });

        authRepository = new AuthRepository(this);
        repository = PoiRepositoryImpl.getInstance();
        ((PoiRepositoryImpl)repository).setAccessToken(authRepository.getAccessToken());

        // IntentからPOI情報を受け取る
        long id = getIntent().getLongExtra("POI_ID", 0);
        String type = getIntent().getStringExtra("POI_TYPE");
        int ver = getIntent().getIntExtra("POI_VER", 0);
        
        // 既存の座標があればそれを使用、なければデフォルト
        double initialLat = getIntent().getDoubleExtra("POI_LAT", 35.6812);
        double initialLon = getIntent().getDoubleExtra("POI_LON", 139.7671);
        
        java.util.Map<String, String> tags;
        if (getIntent().hasExtra("POI_TAGS")) {
            // POI_TAGS があればそれを使用（新しい方式）
            tags = (java.util.Map<String, String>) getIntent().getSerializableExtra("POI_TAGS");
        } else {
            // なければ個別に取得（互換性のため）
            tags = new java.util.HashMap<>();
            if (getIntent().hasExtra("TAG_AMENITY")) tags.put("amenity", getIntent().getStringExtra("TAG_AMENITY"));
            if (getIntent().hasExtra("TAG_NAME")) tags.put("name", getIntent().getStringExtra("TAG_NAME"));
            if (getIntent().hasExtra("TAG_OPENING_HOURS")) tags.put("opening_hours", getIntent().getStringExtra("TAG_OPENING_HOURS"));
            if (getIntent().hasExtra("TAG_COLLECTION_TIMES")) tags.put("collection_times", getIntent().getStringExtra("TAG_COLLECTION_TIMES"));
            if (getIntent().hasExtra("TAG_REF")) tags.put("ref", getIntent().getStringExtra("TAG_REF"));
        }

        targetPoi = new OsmPoi(id, initialLat, initialLon, type != null ? type : "node", tags, ver);

        TextView title = findViewById(R.id.edit_title);
        tagInput = findViewById(R.id.edit_tag_value);
        View tagLayout = findViewById(R.id.edit_tag_layout);
        View collectionLayout = findViewById(R.id.layout_collection_edit);
        View refLayout = findViewById(R.id.edit_ref_layout);
        TextInputEditText refInput = findViewById(R.id.edit_ref_value);
        tableCollection = findViewById(R.id.table_collection);
        layoutFallback = findViewById(R.id.layout_fallback);
        textFallback = findViewById(R.id.text_fallback_value);
        View btnForceEdit = findViewById(R.id.btn_force_edit);
        Button btnAddRow = findViewById(R.id.btn_add_row);
        Button btnCopyToSat = findViewById(R.id.btn_copy_to_sat);
        Button btnCopyToSun = findViewById(R.id.btn_copy_to_sun);
        map = findViewById(R.id.edit_map);
        map.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        Button btnSave = findViewById(R.id.btn_save);

        // Opening Hours UI
        View ohLayout = findViewById(R.id.layout_opening_hours_edit);
        editOhWdOpen = findViewById(R.id.edit_oh_wd_open);
        editOhWdClose = findViewById(R.id.edit_oh_wd_close);
        editOhWdBreakStart = findViewById(R.id.edit_oh_wd_break_start);
        editOhWdBreakEnd = findViewById(R.id.edit_oh_wd_break_end);
        editOhSaOpen = findViewById(R.id.edit_oh_sa_open);
        editOhSaClose = findViewById(R.id.edit_oh_sa_close);
        editOhSaBreakStart = findViewById(R.id.edit_oh_sa_break_start);
        editOhSaBreakEnd = findViewById(R.id.edit_oh_sa_break_end);
        editOhPhOpen = findViewById(R.id.edit_oh_ph_open);
        editOhPhClose = findViewById(R.id.edit_oh_ph_close);
        editOhPhBreakStart = findViewById(R.id.edit_oh_ph_break_start);
        editOhPhBreakEnd = findViewById(R.id.edit_oh_ph_break_end);
        checkOhWdOff = findViewById(R.id.check_oh_wd_off);
        checkOhSaOff = findViewById(R.id.check_oh_sa_off);
        checkOhPhOff = findViewById(R.id.check_oh_ph_off);
        
        android.widget.CompoundButton.OnCheckedChangeListener ohOffListener = (buttonView, isChecked) -> {
            EditText[] rowEditors;
            if (buttonView == checkOhWdOff) {
                rowEditors = new EditText[]{editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd};
            } else if (buttonView == checkOhSaOff) {
                rowEditors = new EditText[]{editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd};
            } else {
                rowEditors = new EditText[]{editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd};
            }
            for (EditText et : rowEditors) {
                et.setEnabled(!isChecked);
                et.setAlpha(isChecked ? 0.5f : 1.0f);
            }
        };
        checkOhWdOff.setOnCheckedChangeListener(ohOffListener);
        checkOhSaOff.setOnCheckedChangeListener(ohOffListener);
        checkOhPhOff.setOnCheckedChangeListener(ohOffListener);
        
        Button btnOhCopyToSa = findViewById(R.id.btn_oh_copy_to_sa);
        Button btnOhCopyToPh = findViewById(R.id.btn_oh_copy_to_ph);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        String amenity = targetPoi.getTag("amenity");
        boolean isPostBox = "post_box".equals(amenity);

        // クレンジング：不要なタグを除去
        if (isPostBox) {
            targetPoi.getTags().remove("opening_hours");
        } else {
            targetPoi.getTags().remove("collection_times");
            targetPoi.getTags().remove("ref");
        }

        title.setText(isPostBox ? "郵便ポストの編集" : "郵便局の編集");

        if (isPostBox) {
            refLayout.setVisibility(View.VISIBLE);
            String currentRef = targetPoi.getTag("ref");
            if (currentRef != null) {
                refInput.setText(currentRef);
            }

            String currentTimes = targetPoi.getTag("collection_times");
            if (currentTimes != null && !currentTimes.isEmpty()) {
                boolean parsed = parseAndFillCollectionTimes(currentTimes);
                if (!parsed) {
                    collectionLayout.setVisibility(View.GONE);
                    layoutFallback.setVisibility(View.VISIBLE);
                    textFallback.setText("解析できない形式のため直接編集できません:\n" + currentTimes);
                } else {
                    collectionLayout.setVisibility(View.VISIBLE);
                    layoutFallback.setVisibility(View.GONE);
                }
            } else {
                collectionLayout.setVisibility(View.VISIBLE);
                layoutFallback.setVisibility(View.GONE);
            }
            tagLayout.setVisibility(View.GONE);
            
            btnForceEdit.setOnClickListener(v -> {
                layoutFallback.setVisibility(View.GONE);
                collectionLayout.setVisibility(View.VISIBLE);
                // 必要最低限の行を確保
                if (timeRows.isEmpty()) {
                    for (int i = 0; i < 3; i++) addNewRow();
                }
            });
            
            if (layoutFallback.getVisibility() != View.VISIBLE) {
                // 既存データが少ない、または無い場合のために最低3行は確保
                while (timeRows.size() < 3) {
                    addNewRow();
                }
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
        } else {
            String hours = targetPoi.getTag("opening_hours");
            if (hours != null && !hours.isEmpty()) {
                boolean parsed = parseAndFillOpeningHours(hours);
                if (!parsed) {
                    ohLayout.setVisibility(View.GONE);
                    tagLayout.setVisibility(View.VISIBLE);
                    layoutFallback.setVisibility(View.VISIBLE);
                    textFallback.setText("解析できない形式のため直接編集できません:\n" + hours);
                } else {
                    ohLayout.setVisibility(View.VISIBLE);
                    tagLayout.setVisibility(View.GONE);
                    layoutFallback.setVisibility(View.GONE);
                }
            } else {
                ohLayout.setVisibility(View.VISIBLE);
                tagLayout.setVisibility(View.GONE);
                layoutFallback.setVisibility(View.GONE);
            }

            btnForceEdit.setOnClickListener(v -> {
                layoutFallback.setVisibility(View.GONE);
                ohLayout.setVisibility(View.VISIBLE);
                tagLayout.setVisibility(View.VISIBLE);
            });

            btnOhCopyToSa.setOnClickListener(v -> {
                editOhSaOpen.setText(editOhWdOpen.getText());
                editOhSaClose.setText(editOhWdClose.getText());
                editOhSaBreakStart.setText(editOhWdBreakStart.getText());
                editOhSaBreakEnd.setText(editOhWdBreakEnd.getText());
            });
            btnOhCopyToPh.setOnClickListener(v -> {
                editOhPhOpen.setText(editOhWdOpen.getText());
                editOhPhClose.setText(editOhWdClose.getText());
                editOhPhBreakStart.setText(editOhWdBreakStart.getText());
                editOhPhBreakEnd.setText(editOhWdBreakEnd.getText());
            });

            // 変更監視用
            TextWatcher ohWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    // 何か入力されたらスタイルを適用
                    View focused = getCurrentFocus();
                    if (focused instanceof EditText) {
                        applyCellStyles((EditText) focused, s.toString(), true);
                    }
                }
            };
            EditText[] ohEditors = {
                editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd,
                editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd,
                editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd
            };
            for (EditText et : ohEditors) {
                if (et != null) {
                    et.addTextChangedListener(ohWatcher);
                    applyCellStyles(et, et.getText().toString(), false);
                }
            }
        }

        // 地図の初期化
        map.setTileSource(new XYTileSource("OSMJP", 0, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(targetPoi.getLat(), targetPoi.getLon());
        double zoom = getIntent().getDoubleExtra("ZOOM_LEVEL", 19.0);
        map.getController().setZoom(zoom);
        map.getController().setCenter(startPoint);

        marker = new ReticleMarker(map);
        marker.setPosition(startPoint);
        marker.setDraggable(false);
        marker.setTitle("位置を調整");
        marker.setInfoWindow(null);
        marker.setOnMarkerClickListener((m, mv) -> true);
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
        boolean isPostBox = "post_box".equals(amenity);
        
        if (isPostBox) {
            // 不要なタグを削除
            targetPoi.getTags().remove("opening_hours");

            TextInputEditText refInput = findViewById(R.id.edit_ref_value);
            String newRef = refInput.getText() != null ? refInput.getText().toString().trim() : "";
            if (!newRef.isEmpty()) {
                targetPoi.getTags().put("ref", newRef);
            } else {
                targetPoi.getTags().remove("ref");
            }

            if (layoutFallback.getVisibility() == View.VISIBLE) {
                // パース失敗時（フォールバック表示中）は時刻タグを更新しない（位置のみ更新）
                // 既に targetPoi.getTags() には元の値が入っている
            } else {
                Map<String, List<String>> weeklyTable = new HashMap<>();
                String[] dayKeys = {"Mo", "Sa", "Su", "PH"};
                for (int col = 0; col < 3; col++) {
                    List<String> targetList = new ArrayList<>();
                    int lastMinutes = -1;
                    for (int r = 0; r < timeRows.size(); r++) {
                        String val = timeRows.get(r)[col].getText().toString().trim();
                        if (val.isEmpty()) continue;

                        if (!TIME_PATTERN.matcher(val).matches()) {
                            Toast.makeText(this, "無効な時刻形式です: " + val, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int minutes = SimpleScheduleParser.parseMinutes(val);
                        if (minutes <= lastMinutes) {
                            Toast.makeText(this, "時刻は昇順で入力してください", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        targetList.add(val);
                        lastMinutes = minutes;
                    }
                    if (col == 0) {
                        weeklyTable.put("Mo", targetList);
                        weeklyTable.put("Tu", targetList);
                        weeklyTable.put("We", targetList);
                        weeklyTable.put("Th", targetList);
                        weeklyTable.put("Fr", targetList);
                    } else if (col == 1) {
                        weeklyTable.put("Sa", targetList);
                    } else {
                        weeklyTable.put("Su", targetList);
                        weeklyTable.put("PH", targetList);
                    }
                }

                String collection = scheduleParser.format(weeklyTable, ScheduleParser.Amenity.POST_BOX);
                targetPoi.getTags().put("collection_times", collection);
            }
        } else {
            // 不要なタグを削除
            targetPoi.getTags().remove("collection_times");
            targetPoi.getTags().remove("ref");

            if (layoutFallback.getVisibility() == View.VISIBLE && findViewById(R.id.edit_tag_layout).getVisibility() != View.VISIBLE) {
                // パース失敗時（フォールバック表示中）かつ直接編集も表示されていない場合は営業時間タグを更新しない
            } else if (findViewById(R.id.edit_tag_layout).getVisibility() == View.VISIBLE) {
                // タグ直接編集が表示されている場合はその値を反映
                String manualText = tagInput.getText() != null ? tagInput.getText().toString().trim() : "";
                targetPoi.getTags().put("opening_hours", manualText);
            } else {
                Map<String, List<String>> weeklyTable = new HashMap<>();
                
                // 平日
                List<String> wdTimes;
                if (checkOhWdOff.isChecked()) {
                    wdTimes = new ArrayList<>();
                } else {
                    String wdOpen = editOhWdOpen.getText().toString().trim();
                    String wdClose = editOhWdClose.getText().toString().trim();
                    String wdBreakStart = editOhWdBreakStart.getText().toString().trim();
                    String wdBreakEnd = editOhWdBreakEnd.getText().toString().trim();
                    wdTimes = formatOpeningTimeRange(wdOpen, wdClose, wdBreakStart, wdBreakEnd);
                }
                for (String d : new String[]{"Mo", "Tu", "We", "Th", "Fr"}) weeklyTable.put(d, wdTimes);
                
                // 土曜
                List<String> saTimes;
                if (checkOhSaOff.isChecked()) {
                    saTimes = new ArrayList<>();
                } else {
                    String saOpen = editOhSaOpen.getText().toString().trim();
                    String saClose = editOhSaClose.getText().toString().trim();
                    String saBreakStart = editOhSaBreakStart.getText().toString().trim();
                    String saBreakEnd = editOhSaBreakEnd.getText().toString().trim();
                    saTimes = formatOpeningTimeRange(saOpen, saClose, saBreakStart, saBreakEnd);
                }
                weeklyTable.put("Sa", saTimes);
                
                // 日祝
                List<String> phTimes;
                if (checkOhPhOff.isChecked()) {
                    phTimes = new ArrayList<>();
                } else {
                    String phOpen = editOhPhOpen.getText().toString().trim();
                    String phClose = editOhPhClose.getText().toString().trim();
                    String phBreakStart = editOhPhBreakStart.getText().toString().trim();
                    String phBreakEnd = editOhPhBreakEnd.getText().toString().trim();
                    phTimes = formatOpeningTimeRange(phOpen, phClose, phBreakStart, phBreakEnd);
                }
                weeklyTable.put("Su", phTimes);
                weeklyTable.put("PH", phTimes);

                String openingHours = scheduleParser.format(weeklyTable, ScheduleParser.Amenity.POST_OFFICE);
                targetPoi.getTags().put("opening_hours", openingHours);
            }
        }
        
        // 移動後の位置を取得
        GeoPoint pos = marker.getPosition();
        OsmPoi updatedPoi = new OsmPoi(
                targetPoi.getId(),
                pos.getLatitude(),
                pos.getLongitude(),
                targetPoi.getType(),
                targetPoi.getTags(),
                targetPoi.getVer()
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
                private String originalValue = null;
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (originalValue == null) {
                        originalValue = s.toString();
                    }
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    boolean isModified = originalValue != null && !s.toString().equals(originalValue);
                    applyCellStyles(et, s.toString(), isModified);
                }
            });

            row.addView(et);
            rowEditors[i] = et;
        }
        tableCollection.addView(row);
        timeRows.add(rowEditors);
    }

    private List<String> formatOpeningTimeRange(String open, String close, String bStart, String bEnd) {
        List<String> times = new ArrayList<>();
        if (!open.isEmpty() && !close.isEmpty()) {
            if (!bStart.isEmpty() && !bEnd.isEmpty()) {
                times.add(open + "-" + bStart);
                times.add(bEnd + "-" + close);
            } else {
                times.add(open + "-" + close);
            }
        }
        return times;
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

    private boolean parseAndFillOpeningHours(String tag) {
        try {
            pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult result = scheduleParser.parse(tag, System.currentTimeMillis(), ScheduleParser.Amenity.POST_OFFICE);
            Map<String, List<String>> weeklyTable = result.getWeeklyTable();
            if (weeklyTable.isEmpty() && !tag.isEmpty()) return false;

            String[] dayGroups = {"Mo", "Sa", "Su"};
            EditText[][] editors = {
                {editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd},
                {editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd},
                {editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd}
            };

            for (int i = 0; i < dayGroups.length; i++) {
                List<String> times = weeklyTable.getOrDefault(dayGroups[i], new ArrayList<>());
                android.widget.CheckBox checkOff = (i == 0) ? checkOhWdOff : (i == 1 ? checkOhSaOff : checkOhPhOff);
                
                if (times.isEmpty() && !tag.isEmpty()) {
                    checkOff.setChecked(true);
                    editors[i][0].setText("");
                    editors[i][1].setText("");
                    editors[i][2].setText("");
                    editors[i][3].setText("");
                } else {
                    checkOff.setChecked(false);
                    String open = "", close = "", bStart = "", bEnd = "";
                    if (times.size() == 1) {
                        String[] range = times.get(0).split("-");
                        if (range.length == 2) {
                            open = range[0].trim();
                            close = range[1].trim();
                        }
                    } else if (times.size() >= 2) {
                        String[] range1 = times.get(0).split("-");
                        String[] range2 = times.get(1).split("-");
                        if (range1.length == 2 && range2.length == 2) {
                            open = range1[0].trim();
                            bStart = range1[1].trim();
                            bEnd = range2[0].trim();
                            close = range2[1].trim();
                        }
                    }
                    editors[i][0].setText(open);
                    editors[i][1].setText(close);
                    editors[i][2].setText(bStart);
                    editors[i][3].setText(bEnd);
                }
                for (EditText et : editors[i]) applyCellStyles(et, et.getText().toString(), false);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean parseAndFillCollectionTimes(String tag) {
        try {
            pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult result = scheduleParser.parse(tag, System.currentTimeMillis(), ScheduleParser.Amenity.POST_BOX);
            Map<String, List<String>> weeklyTable = result.getWeeklyTable();
            if (weeklyTable.isEmpty() && !tag.isEmpty()) return false;

            List<String> weekday = weeklyTable.getOrDefault("Mo", new ArrayList<>());
            List<String> saturday = weeklyTable.getOrDefault("Sa", new ArrayList<>());
            List<String> sunday = weeklyTable.getOrDefault("Su", new ArrayList<>());
            List<String> holiday = weeklyTable.getOrDefault("PH", new ArrayList<>());

            // 火〜金のスケジュールが月曜日と一致するか確認
            for (String day : new String[]{"Tu", "We", "Th", "Fr"}) {
                if (!weekday.equals(weeklyTable.getOrDefault(day, new ArrayList<>()))) return false;
            }

            // 日曜と祝日が同じかチェック（UI上は「日祝」列にまとめているため）
            boolean hasPH = tag.contains("PH");
            if (hasPH && !sunday.equals(holiday)) return false;

            if (!hasPH) {
                // PHがない場合、警告を表示する準備をする
                findViewById(R.id.layout_holiday_warning).setVisibility(View.VISIBLE);
                TextView header = findViewById(R.id.header_sun_ph);
                if (header != null) header.setTextColor(android.graphics.Color.RED);
                
                findViewById(R.id.btn_apply_sun_to_ph).setOnClickListener(v -> {
                    findViewById(R.id.layout_holiday_warning).setVisibility(View.GONE);
                    if (header != null) header.setTextColor(android.graphics.Color.BLACK);
                });
            }

            List<String> holidayCol = hasPH ? holiday : sunday;
            int maxRows = Math.max(weekday.size(), Math.max(saturday.size(), holidayCol.size()));
            for (int i = 0; i < maxRows; i++) {
                addNewRow();
                if (i < weekday.size()) {
                    timeRows.get(i)[0].setText(weekday.get(i));
                    applyCellStyles(timeRows.get(i)[0], weekday.get(i), false);
                }
                if (i < saturday.size()) {
                    timeRows.get(i)[1].setText(saturday.get(i));
                    applyCellStyles(timeRows.get(i)[1], saturday.get(i), false);
                }
                if (i < holidayCol.size()) {
                    timeRows.get(i)[2].setText(holidayCol.get(i));
                    applyCellStyles(timeRows.get(i)[2], holidayCol.get(i), false);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
