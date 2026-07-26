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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import pro.eng.yui.android.osmjppostalmap.core.AddressEditDialog;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.*;
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
    private Button btnSave;
    private TextView addressValue;
    private int lastCheckedShapeId = -1;
    private final ScheduleParser scheduleParser = new SimpleScheduleParser();
    private androidx.appcompat.app.AlertDialog progressDialog;
    private static final double MIN_ZOOM = 15.0;

    private static class ReticleMarker extends Marker {
        private final android.graphics.Paint paint;

        public ReticleMarker(MapView mapView) {
            super(mapView);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(androidx.core.content.ContextCompat.getColor(EditPoiActivity.this, R.color.jp_post_red)); // 赤
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
        if (authRepository.getAccessToken() == null) {
            Toast.makeText(this, "編集するにはログインが必要です", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repository = PoiRepositoryImpl.getInstance();
        ((PoiRepositoryImpl)repository).setAccessToken(authRepository.getAccessToken());

        // IntentからPOI情報を受け取る
        long id = getIntent().getLongExtra("POI_ID", 0);
        String type = getIntent().getStringExtra("POI_TYPE");
        long ver = getIntent().getLongExtra("POI_VER", 0L);
        
        // 既存の座標があればそれを使用、なければデフォルト
        double initialLat = getIntent().getDoubleExtra("POI_LAT", MainActivity.TOKYO_CENTRAL_POST_OFFICE.getLatitude());
        double initialLon = getIntent().getDoubleExtra("POI_LON", MainActivity.TOKYO_CENTRAL_POST_OFFICE.getLongitude());
        
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
        View shapeLayout = findViewById(R.id.layout_shape_edit);
        RadioGroup radioShape = findViewById(R.id.edit_radio_shape);
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
        btnSave = findViewById(R.id.btn_save);
        addressValue = findViewById(R.id.edit_address_value);
        View btnAddressEdit = findViewById(R.id.btn_address_edit);

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

        String amenityTag = targetPoi.getTag("amenity");
        pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser.Amenity amenity = 
                "post_office".equals(amenityTag) ? 
                pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser.Amenity.POST_OFFICE : 
                pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser.Amenity.POST_BOX;
        boolean isPostBox = (amenity == pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser.Amenity.POST_BOX);

        // クレンジング：不要なタグを除去
        if (isPostBox) {
            targetPoi.getTags().remove("opening_hours");
        } else {
            targetPoi.getTags().remove("collection_times");
            targetPoi.getTags().remove("ref");
        }

        title.setText(isPostBox ? "郵便ポストの編集" : "郵便局の編集");

        // 住所は addr:* の集合なので専用ダイアログで編集し、結果を targetPoi のタグへ直接書き戻す。
        // saveChanges() は targetPoi.getTags() を写して送信するため、これで保存対象に乗る
        showAddress();
        btnAddressEdit.setOnClickListener(v ->
                AddressEditDialog.show(this, JpAddress.of(targetPoi.getTags()), edited -> {
                    AddressEditDialog.applyTo(targetPoi.getTags(), edited);
                    showAddress();
                }));

        if (isPostBox) {
            shapeLayout.setVisibility(View.VISIBLE);

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

            String support = targetPoi.getTag("support");
            String pbType = targetPoi.getTag("post_box:type");
            if ("pole".equals(support) && "lamp".equals(pbType)) {
                radioShape.check(R.id.edit_shape_box);
            } else if ("ground".equals(support) && "pillar".equals(pbType)) {
                radioShape.check(R.id.edit_shape_pillar);
            } else if (support != null || pbType != null) {
                radioShape.check(R.id.edit_shape_other);
            } else {
                radioShape.clearCheck();
            }
            lastCheckedShapeId = radioShape.getCheckedRadioButtonId();

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
                editOhPhOpen.setText(editOhSaOpen.getText());
                editOhPhClose.setText(editOhSaClose.getText());
                editOhPhBreakStart.setText(editOhSaBreakStart.getText());
                editOhPhBreakEnd.setText(editOhSaBreakEnd.getText());
            });

            // 変更監視用
            class OhTextWatcher implements TextWatcher {
                private final EditText editText;
                private String originalValue = null;

                public OhTextWatcher(EditText et) {
                    this.editText = et;
                }

                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (originalValue == null) {
                        originalValue = s.toString();
                    }
                }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    boolean isModified = originalValue != null && !s.toString().equals(originalValue);
                    applyCellStyles(editText, s.toString(), isModified);
                }
            }

            EditText[] ohEditors = {
                editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd,
                editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd,
                editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd
            };
            for (EditText et : ohEditors) {
                if (et != null) {
                    et.addTextChangedListener(new OhTextWatcher(et));
                    applyCellStyles(et, et.getText().toString(), false);
                }
            }
        }

        // 地図の初期化
        map.setTileSource(new XYTileSource("OSMJP", (int) MIN_ZOOM, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(MIN_ZOOM);
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
                    if (btnSave != null) {
                        btnSave.setEnabled(false);
                    }
                    showProgress("処理を開始中…");
                    saveChanges();
                })
                .setNegativeButton("キャンセル", null)
                .show();
        });
    }

    /** 住所欄に現在の addr:* タグ由来の表示テキストを反映する。 */
    private void showAddress() {
        String text = JpPostalUtil.getAddressText(targetPoi.getTags());
        addressValue.setText(text.isEmpty() ? "データなし" : text);
    }

    private void saveChanges() {
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
        // タグの更新と位置の更新をリポジトリ経由で行う
        Map<String, String> currentTags = new HashMap<>(targetPoi.getTags());
        String amenityTag = currentTags.get("amenity");
        boolean isPostBox = "post_box".equals(amenityTag);
        
        if (isPostBox) {
            // 不要なタグを削除
            currentTags.remove("opening_hours");

            RadioGroup radioShape = findViewById(R.id.edit_radio_shape);
            int selectedShapeId = radioShape.getCheckedRadioButtonId();
            if (selectedShapeId == R.id.edit_shape_box) {
                currentTags.put("support", "pole");
                currentTags.put("post_box:type", "lamp");
            } else if (selectedShapeId == R.id.edit_shape_pillar) {
                currentTags.put("support", "ground");
                currentTags.put("post_box:type", "pillar");
            } else {
                currentTags.remove("support");
                currentTags.remove("post_box:type");
            }

            TextInputEditText refInput = findViewById(R.id.edit_ref_value);
            String newRef = refInput.getText() != null ? refInput.getText().toString().trim() : "";
            if (!newRef.isEmpty()) {
                currentTags.put("ref", newRef);
            } else {
                currentTags.remove("ref");
            }

            if (layoutFallback.getVisibility() == View.VISIBLE) {
                // パース失敗時（フォールバック表示中）は時刻タグを更新しない（位置のみ更新）
            } else {
                Map<Days, List<? extends ITagPart>> weeklyTable = new HashMap<>();
                for (int col = 0; col < 3; col++) {
                    List<CollectionTime> targetList = new ArrayList<>();
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
                        targetList.add(new CollectionTime(val));
                        lastMinutes = minutes;
                    }
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

                String collection = scheduleParser.format(weeklyTable, ScheduleParser.TimeType.COLLECTION_TIMES);
                currentTags.put("collection_times", collection);
            }
        } else {
            // 不要なタグを削除
            currentTags.remove("collection_times");
            currentTags.remove("ref");

            if (layoutFallback.getVisibility() == View.VISIBLE && findViewById(R.id.edit_tag_layout).getVisibility() != View.VISIBLE) {
                // パース失敗時（フォールバック表示中）かつ直接編集も表示されていない場合は営業時間タグを更新しない
            } else if (findViewById(R.id.edit_tag_layout).getVisibility() == View.VISIBLE) {
                // タグ直接編集が表示されている場合はその値を反映
                String manualText = tagInput.getText() != null ? tagInput.getText().toString().trim() : "";
                currentTags.put("opening_hours", manualText);
            } else {
                Map<Days, List<? extends ITagPart>> weeklyTable = new HashMap<>();
                
                // 平日
                List<ITagPart> wdTimes = new ArrayList<>();
                if (!checkOhWdOff.isChecked()) {
                    String wdOpen = editOhWdOpen.getText().toString().trim();
                    String wdClose = editOhWdClose.getText().toString().trim();
                    String wdBreakStart = editOhWdBreakStart.getText().toString().trim();
                    String wdBreakEnd = editOhWdBreakEnd.getText().toString().trim();
                    wdTimes.addAll(formatOpeningTimeRange(wdOpen, wdClose, wdBreakStart, wdBreakEnd));
                }
                for (String d : new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) {
                    weeklyTable.put(Days.valueOf(d), wdTimes);
                }
                
                // 土曜
                List<ITagPart> saTimes = new ArrayList<>();
                if (!checkOhSaOff.isChecked()) {
                    String saOpen = editOhSaOpen.getText().toString().trim();
                    String saClose = editOhSaClose.getText().toString().trim();
                    String saBreakStart = editOhSaBreakStart.getText().toString().trim();
                    String saBreakEnd = editOhSaBreakEnd.getText().toString().trim();
                    saTimes.addAll(formatOpeningTimeRange(saOpen, saClose, saBreakStart, saBreakEnd));
                }
                weeklyTable.put(Days.SATURDAY, saTimes);
                
                // 日祝
                List<ITagPart> phTimes = new ArrayList<>();
                if (!checkOhPhOff.isChecked()) {
                    String phOpen = editOhPhOpen.getText().toString().trim();
                    String phClose = editOhPhClose.getText().toString().trim();
                    String phBreakStart = editOhPhBreakStart.getText().toString().trim();
                    String phBreakEnd = editOhPhBreakEnd.getText().toString().trim();
                    phTimes.addAll(formatOpeningTimeRange(phOpen, phClose, phBreakStart, phBreakEnd));
                }
                weeklyTable.put(Days.SUNDAY, phTimes);
                weeklyTable.put(Days.PUBLIC_HOLIDAY, phTimes);

                String openingHours = scheduleParser.format(weeklyTable, ScheduleParser.TimeType.OPENING_HOURS);
                currentTags.put("opening_hours", openingHours);
            }
        }
        
        // 移動後の位置を取得
        GeoPoint pos = marker.getPosition();
        OsmPoi updatedPoi = new OsmPoi(
                targetPoi.getId(),
                pos.getLatitude(),
                pos.getLongitude(),
                targetPoi.getType(),
                currentTags,
                targetPoi.getVer()
        );

        repository.savePoi(updatedPoi, "update " + (updatedPoi.getTag("name") != null ? updatedPoi.getTag("name") : updatedPoi.getType()), new PoiRepository.PoiSaveCallback() {
            @Override
            public void onSuccess() {
                dismissProgress();
                if (btnSave != null) {
                    btnSave.setEnabled(true);
                }
                Toast.makeText(EditPoiActivity.this, "保存しました", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                dismissProgress();
                if (btnSave != null) {
                    btnSave.setEnabled(true);
                }
                Toast.makeText(EditPoiActivity.this, "保存エラー: " + message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onProgress(String message) {
                showProgress(message);
            }
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

    private void addNewRow(String initialValue1, String initialValue2, String initialValue3) {
        TableRow row = new TableRow(this);
        EditText[] rowEditors = new EditText[3];
        String[] initialValues = {initialValue1, initialValue2, initialValue3};
        for (int i = 0; i < 3; i++) {
            EditText et = new EditText(this);
            et.setHint("--:--");
            et.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            et.setGravity(Gravity.CENTER);
            et.setText(initialValues[i]);
            
            // 初期の見た目設定
            applyCellStyles(et, initialValues[i], false);

            final String finalInitialValue = initialValues[i];
            et.addTextChangedListener(new TextWatcher() {
                private final String originalValue = finalInitialValue;
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
        }
        tableCollection.addView(row);
        timeRows.add(rowEditors);
    }

    private void addNewRow() {
        addNewRow("", "", "");
    }

    private List<OpenCloseTime> formatOpeningTimeRange(String open, String close, String bStart, String bEnd) {
        List<OpenCloseTime> times = new ArrayList<>();
        if (!open.isEmpty() && !close.isEmpty()) {
            if (!bStart.isEmpty() && !bEnd.isEmpty()) {
                times.add(new OpenCloseTime(open, bStart));
                times.add(new OpenCloseTime(bEnd, close));
            } else {
                times.add(new OpenCloseTime(open, close));
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
            pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult result = scheduleParser.parse(new OpeningHours(tag), System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
            Map<Days, ? extends IDaySchedule> weeklyTable = result.getWeeklyTable();
            if (weeklyTable.isEmpty() && !tag.isEmpty()) return false;

            Days[] dayGroups = {
                Days.MONDAY,
                Days.SATURDAY,
                Days.SUNDAY
            };
            EditText[][] editors = {
                {editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd},
                {editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd},
                {editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd}
            };

            for (int i = 0; i < dayGroups.length; i++) {
                IDaySchedule daySchedule = weeklyTable.get(dayGroups[i]);
                android.widget.CheckBox checkOff = (i == 0) ? checkOhWdOff : (i == 1 ? checkOhSaOff : checkOhPhOff);
                
                if ((daySchedule == null || daySchedule.schedule().isEmpty()) && !tag.isEmpty()) {
                    checkOff.setChecked(true);
                    editors[i][0].setText("");
                    editors[i][1].setText("");
                    editors[i][2].setText("");
                    editors[i][3].setText("");
                } else {
                    checkOff.setChecked(false);
                    String open = "", close = "", bStart = "", bEnd = "";
                    if (daySchedule != null) {
                        List<? extends ITagPart> times = daySchedule.schedule();
                        if (times.size() == 1) {
                            OpenCloseTime oct = (OpenCloseTime) times.get(0);
                            open = oct.openAt.value;
                            close = oct.closeAt.value;
                        } else if (times.size() >= 2) {
                            OpenCloseTime oct1 = (OpenCloseTime) times.get(0);
                            OpenCloseTime oct2 = (OpenCloseTime) times.get(1);
                            open = oct1.openAt.value;
                            bStart = oct1.closeAt.value;
                            bEnd = oct2.openAt.value;
                            close = oct2.closeAt.value;
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
            pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult result = scheduleParser.parse(new CollectionTimes(tag), System.currentTimeMillis(), ScheduleParser.TimeType.COLLECTION_TIMES);
            Map<Days, ? extends IDaySchedule> weeklyTable = result.getWeeklyTable();
            if (weeklyTable.isEmpty() && !tag.isEmpty()) return false;

            IDaySchedule wdSched = weeklyTable.get(Days.MONDAY);
            IDaySchedule saSched = weeklyTable.get(Days.SATURDAY);
            IDaySchedule suSched = weeklyTable.get(Days.SUNDAY);
            IDaySchedule phSched = weeklyTable.get(Days.PUBLIC_HOLIDAY);

            List<String> weekday = schedToList(wdSched);
            List<String> saturday = schedToList(saSched);
            List<String> sunday = schedToList(suSched);
            List<String> holiday = schedToList(phSched);

            // 火〜金のスケジュールが月曜日と一致するか確認
            for (String day : new String[]{"TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) {
                if (!weekday.equals(schedToList(weeklyTable.get(Days.valueOf(day))))) return false;
            }

            // 日曜と祝日が同じかチェック
            boolean hasPH = tag.contains("PH");
            if (hasPH && !sunday.equals(holiday)) return false;

            if (!hasPH) {
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
                String val1 = i < weekday.size() ? weekday.get(i) : "";
                String val2 = i < saturday.size() ? saturday.get(i) : "";
                String val3 = i < holidayCol.size() ? holidayCol.get(i) : "";
                addNewRow(val1, val2, val3);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> schedToList(IDaySchedule sched) {
        List<String> list = new ArrayList<>();
        if (sched != null) {
            for (Object p : sched.schedule()) {
                list.add(p.toString());
            }
        }
        return list;
    }
}
