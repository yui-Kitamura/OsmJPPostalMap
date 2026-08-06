package pro.eng.yui.android.osmjppostalmap.ui;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.GradientDrawable;
import pro.eng.yui.android.osmjppostalmap.domain.Util;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
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
    private EditText editLsWdOpen, editLsWdClose, editLsWdBreakStart, editLsWdBreakEnd;
    private EditText editLsSaOpen, editLsSaClose, editLsSaBreakStart, editLsSaBreakEnd;
    private EditText editLsPhOpen, editLsPhClose, editLsPhBreakStart, editLsPhBreakEnd;
    private android.widget.CheckBox checkOhWdOff, checkOhSaOff, checkOhPhOff;
    private android.widget.CheckBox checkLsWdOff, checkLsSaOff, checkLsPhOff;
    private android.widget.CheckBox checkColWdOff, checkColSaOff, checkColPhOff;
    private RadioGroup radioLimitedService;
    private View layoutLimitedServiceEditRoot;
    private TableLayout tableLimitedService;
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    private PoiRepository repository;
    private MainViewModel viewModel;
    private AuthRepository authRepository;
    private OsmPoi targetPoi;
    private Button btnSave;
    private TextView addressValue;
    private TextView kanaValue;
    private View layoutKanaEdit;
    private TextView textLocationStatus;
    private TextView topBanner;
    private TextInputEditText editSpecialNote;
    private MyLocationNewOverlay myLocationOverlay;
    private boolean isNew;
    private int lastCheckedShapeId = -1;
    private final ScheduleParser scheduleParser = new SimpleScheduleParser();
    private androidx.appcompat.app.AlertDialog progressDialog;
    private Location lastLocation;
    private double originalLat;
    private double originalLon;
    private String poiType;
    private boolean isMapUnlocked = false;
    private boolean isResettingCenter = false;
    private static final double MIN_ZOOM = 15.0;

    private int getThemeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    private int getMaxDistance() {
        if (targetPoi != null) {
            String amenity = targetPoi.getTag("amenity");
            if ("post_office".equals(amenity)) {
                if ("way".equals(poiType)) {
                    return 100;
                } else {
                    return 75;
                }
            }
        }
        return 50;
    }

    private class ReticleMarker extends Marker {
        private final android.graphics.Paint paint;

        public ReticleMarker(MapView mapView) {
            super(mapView);
            paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            paint.setColor(androidx.core.content.ContextCompat.getColor(EditPoiActivity.this, R.color.brand_red)); // 赤
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
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_poi);

        // Edge-to-Edge adjustment
        View rootLayout = findViewById(R.id.edit_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            // ルートには左右のみ
            v.setPadding(systemBars.left, 0, systemBars.right, 0);

            // ヘッダーにトップインセットを反映
            View header = findViewById(R.id.layout_header);
            header.setPadding(header.getPaddingLeft(), systemBars.top, header.getPaddingRight(), header.getPaddingBottom());

            // 保存ボタンのコンテナにボトムインセット（IME含む）
            View saveContainer = findViewById(R.id.layout_save_container);
            saveContainer.setPadding(saveContainer.getPaddingLeft(), saveContainer.getPaddingTop(),
                    saveContainer.getPaddingRight(), Math.max(systemBars.bottom, ime.bottom));

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
        textLocationStatus = findViewById(R.id.text_location_status);
        topBanner = findViewById(R.id.top_banner);
        
        // IntentからPOI情報を受け取る
        long id = getIntent().getLongExtra("POI_ID", 0);
        isNew = id <= 0;
        poiType = getIntent().getStringExtra("POI_TYPE");
        if (poiType == null) {
            poiType = "node";
        }
        if (isNew && !"way".equals(poiType)) {
            isMapUnlocked = true;
        }
        long ver = getIntent().getLongExtra("POI_VER", 0L);
        
        // 既存の座標があればそれを使用、なければデフォルト
        double initialLat = getIntent().getDoubleExtra("POI_LAT", MainActivity.TOKYO_CENTRAL_POST_OFFICE.getLatitude());
        double initialLon = getIntent().getDoubleExtra("POI_LON", MainActivity.TOKYO_CENTRAL_POST_OFFICE.getLongitude());
        originalLat = initialLat;
        originalLon = initialLon;

        map = findViewById(R.id.edit_map);
        // 地図の初期化
        map.setTileSource(new XYTileSource("OSMJP", (int) MIN_ZOOM, 18, 256, ".png", 
                new String[] { "https://tile.openstreetmap.jp/" }));
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(MIN_ZOOM);
        GeoPoint startPoint = new GeoPoint(initialLat, initialLon);
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

        viewModel.getLocation().observe(this, location -> {
            lastLocation = location;
            updateLocationStatus(location);
        });

        // Intentタグのパースなど続く
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

        targetPoi = new OsmPoi(id, initialLat, initialLon, poiType, tags, ver);

        TextView title = findViewById(R.id.edit_title);
        // タイトル設定は amenity タグなどが判明してから再度行うため、ここでは初期設定のみ
        title.setText(isNew ? R.string.title_add_postbox : R.string.title_edit_postbox);

        tagInput = findViewById(R.id.edit_tag_value);
        Util.addNumberFilter(tagInput);
        View tagLayout = findViewById(R.id.edit_tag_layout);
        View collectionLayout = findViewById(R.id.layout_collection_edit);
        View branchLayout = findViewById(R.id.edit_branch_layout);
        TextInputEditText branchInput = findViewById(R.id.edit_branch_value);
        Util.addNumberFilter(branchInput);
        View refLayout = findViewById(R.id.edit_ref_layout);
        TextInputEditText refInput = findViewById(R.id.edit_ref_value);
        Util.addNumberFilter(refInput);
        View shapeLayout = findViewById(R.id.layout_shape_edit);
        RadioGroup radioShape = findViewById(R.id.edit_radio_shape);
        tableCollection = findViewById(R.id.table_collection);
        layoutFallback = findViewById(R.id.layout_fallback);
        textFallback = findViewById(R.id.text_fallback_value);
        View btnForceEdit = findViewById(R.id.btn_force_edit);
        Button btnAddRow = findViewById(R.id.btn_add_row);
        Button btnCopyToSat = findViewById(R.id.btn_copy_to_sat);
        Button btnCopyToSun = findViewById(R.id.btn_copy_to_sun);
        ImageButton btnLockMap = findViewById(R.id.btn_lock_map);
        updateLockButtonStyle(btnLockMap);
        btnLockMap.setOnClickListener(v -> {
            if ("way".equals(poiType)) {
                Toast.makeText(this, "移動できません", Toast.LENGTH_SHORT).show();
                return;
            }
            isMapUnlocked = !isMapUnlocked;
            updateLockButtonStyle(btnLockMap);
            if (isMapUnlocked) {
                Toast.makeText(this, "地図の移動を許可しました", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "地図の移動をロックしました", Toast.LENGTH_SHORT).show();
            }
        });
        map = findViewById(R.id.edit_map);
        map.setOnTouchListener((v, event) -> {
            if (!isMapUnlocked) {
                return true;
            }
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });
        btnSave = findViewById(R.id.btn_save);
        if (isNew) {
            btnSave.setText(R.string.add);
        } else {
            btnSave.setText(R.string.save);
        }
        addressValue = findViewById(R.id.edit_address_value);
        View btnAddressEdit = findViewById(R.id.btn_address_edit);

        kanaValue = findViewById(R.id.edit_kana_value);
        layoutKanaEdit = findViewById(R.id.layout_kana_edit);
        View btnKanaEdit = findViewById(R.id.btn_kana_edit);

        if ("post_office".equals(targetPoi.getTag("amenity"))) {
            layoutKanaEdit.setVisibility(View.VISIBLE);
            updateKanaDisplay();
            btnKanaEdit.setOnClickListener(v -> showKanaEditDialog());
        } else {
            layoutKanaEdit.setVisibility(View.GONE);
        }

        editSpecialNote = findViewById(R.id.edit_special_note_value);
        Util.addNumberFilter(editSpecialNote);
        TextInputLayout specialNoteLayout = findViewById(R.id.edit_special_note_layout);
        if ("post_office".equals(targetPoi.getTag("amenity"))) {
            specialNoteLayout.setHint(getString(R.string.label_special_note_postoffice));
        } else {
            specialNoteLayout.setHint(getString(R.string.label_special_note_postbox));
        }
        String currentNote = targetPoi.getTag("note");
        if (currentNote != null) {
            editSpecialNote.setText(Util.normalizeNumber(currentNote));
        }

        findViewById(R.id.btn_my_location).setOnClickListener(v -> {
            if ("way".equals(poiType)) {
                Toast.makeText(this, "移動できません", Toast.LENGTH_SHORT).show();
                return;
            }
            if (lastLocation != null) {
                float[] results = new float[1];
                Location.distanceBetween(originalLat, originalLon, lastLocation.getLatitude(), lastLocation.getLongitude(), results);
                int maxDist = getMaxDistance();
                if (!isNew && results[0] > maxDist) {
                    Toast.makeText(this, "現在地が初期位置から" + maxDist + "m以上離れているため移動できません", Toast.LENGTH_SHORT).show();
                    return;
                }
                GeoPoint gp = new GeoPoint(lastLocation);
                if (isNew) {
                    originalLat = gp.getLatitude();
                    originalLon = gp.getLongitude();
                }
                map.getController().animateTo(gp);
                marker.setPosition(gp);
                updateLocationStatus(lastLocation);
            } else {
                Toast.makeText(this, R.string.error_location_not_found, Toast.LENGTH_SHORT).show();
            }
        });

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
        checkColWdOff = findViewById(R.id.check_col_wd_off);
        checkColSaOff = findViewById(R.id.check_col_sa_off);
        checkColPhOff = findViewById(R.id.check_col_ph_off);

        layoutLimitedServiceEditRoot = findViewById(R.id.layout_limited_service_edit_root);
        radioLimitedService = findViewById(R.id.radio_limited_service);
        tableLimitedService = findViewById(R.id.table_limited_service);
        editLsWdOpen = findViewById(R.id.edit_ls_wd_open);
        editLsWdClose = findViewById(R.id.edit_ls_wd_close);
        editLsWdBreakStart = findViewById(R.id.edit_ls_wd_break_start);
        editLsWdBreakEnd = findViewById(R.id.edit_ls_wd_break_end);
        editLsSaOpen = findViewById(R.id.edit_ls_sa_open);
        editLsSaClose = findViewById(R.id.edit_ls_sa_close);
        editLsSaBreakStart = findViewById(R.id.edit_ls_sa_break_start);
        editLsSaBreakEnd = findViewById(R.id.edit_ls_sa_break_end);
        editLsPhOpen = findViewById(R.id.edit_ls_ph_open);
        editLsPhClose = findViewById(R.id.edit_ls_ph_close);
        editLsPhBreakStart = findViewById(R.id.edit_ls_ph_break_start);
        editLsPhBreakEnd = findViewById(R.id.edit_ls_ph_break_end);
        checkLsWdOff = findViewById(R.id.check_ls_wd_off);
        checkLsSaOff = findViewById(R.id.check_ls_sa_off);
        checkLsPhOff = findViewById(R.id.check_ls_ph_off);

        radioLimitedService.setOnCheckedChangeListener((group, checkedId) -> {
            tableLimitedService.setVisibility(checkedId == R.id.radio_ls_yes ? View.VISIBLE : View.GONE);
        });

        android.widget.CompoundButton.OnCheckedChangeListener ohOffListener = (buttonView, isChecked) -> {
            EditText[] rowEditors;
            if (buttonView == checkOhWdOff) {
                rowEditors = new EditText[]{editOhWdOpen, editOhWdClose, editOhWdBreakStart, editOhWdBreakEnd};
            } else if (buttonView == checkOhSaOff) {
                rowEditors = new EditText[]{editOhSaOpen, editOhSaClose, editOhSaBreakStart, editOhSaBreakEnd};
            } else if (buttonView == checkOhPhOff) {
                rowEditors = new EditText[]{editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd};
            } else if (buttonView == checkLsWdOff) {
                rowEditors = new EditText[]{editLsWdOpen, editLsWdClose, editLsWdBreakStart, editLsWdBreakEnd};
            } else if (buttonView == checkLsSaOff) {
                rowEditors = new EditText[]{editLsSaOpen, editLsSaClose, editLsSaBreakStart, editLsSaBreakEnd};
            } else {
                rowEditors = new EditText[]{editLsPhOpen, editLsPhClose, editLsPhBreakStart, editLsPhBreakEnd};
            }
            for (EditText et : rowEditors) {
                et.setEnabled(!isChecked);
                et.setAlpha(isChecked ? 0.5f : 1.0f);
            }
        };
        checkOhWdOff.setOnCheckedChangeListener(ohOffListener);
        checkOhSaOff.setOnCheckedChangeListener(ohOffListener);
        checkOhPhOff.setOnCheckedChangeListener(ohOffListener);
        checkLsWdOff.setOnCheckedChangeListener(ohOffListener);
        checkLsSaOff.setOnCheckedChangeListener(ohOffListener);
        checkLsPhOff.setOnCheckedChangeListener(ohOffListener);

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

        String name = targetPoi.getTag("name");
        if (name != null && !name.isEmpty()) {
            title.setText(name + "の編集");
        } else {
            title.setText(isPostBox ? (isNew ? getString(R.string.title_add_postbox) : getString(R.string.title_edit_postbox)) : getString(R.string.title_edit_postoffice));
        }

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

            branchLayout.setVisibility(View.VISIBLE);
            String currentBranch = targetPoi.getTag("operator:branch");
            if (currentBranch != null) {
                branchInput.setText(Util.normalizeNumber(currentBranch));
            }

            refLayout.setVisibility(View.VISIBLE);
            String currentRef = targetPoi.getTag("ref");
            if (currentRef != null) {
                refInput.setText(Util.normalizeNumber(currentRef));
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
                    Util.applyTimeFormat(row[0]);
                    row[1].setText(row[0].getText());
                }
            });
            btnCopyToSun.setOnClickListener(v -> {
                for (EditText[] row : timeRows) {
                    Util.applyTimeFormat(row[1]);
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

            layoutLimitedServiceEditRoot.setVisibility(View.VISIBLE);
            String lsMail = targetPoi.getTag("limited_service:mail");
            String lsHours = targetPoi.getTag("opening_hours:limited_service");
            
            if ("yes".equals(lsMail)) {
                radioLimitedService.check(R.id.radio_ls_yes);
                tableLimitedService.setVisibility(View.VISIBLE);
            } else if ("no".equals(lsMail)) {
                radioLimitedService.check(R.id.radio_ls_no);
                tableLimitedService.setVisibility(View.GONE);
            } else {
                // 不明
                if (lsHours != null && !lsHours.isEmpty()) {
                    radioLimitedService.check(R.id.radio_ls_yes);
                    tableLimitedService.setVisibility(View.VISIBLE);
                } else {
                    radioLimitedService.check(R.id.radio_ls_unknown);
                    tableLimitedService.setVisibility(View.GONE);
                }
            }

            if (lsHours != null && !lsHours.isEmpty()) {
                parseAndFillLimitedService(lsHours);
            }

            btnForceEdit.setOnClickListener(v -> {
                layoutFallback.setVisibility(View.GONE);
                ohLayout.setVisibility(View.VISIBLE);
                tagLayout.setVisibility(View.VISIBLE);
            });

            btnOhCopyToSa.setOnClickListener(v -> {
                Util.applyTimeFormat(editOhWdOpen);
                Util.applyTimeFormat(editOhWdClose);
                Util.applyTimeFormat(editOhWdBreakStart);
                Util.applyTimeFormat(editOhWdBreakEnd);
                editOhSaOpen.setText(editOhWdOpen.getText());
                editOhSaClose.setText(editOhWdClose.getText());
                editOhSaBreakStart.setText(editOhWdBreakStart.getText());
                editOhSaBreakEnd.setText(editOhWdBreakEnd.getText());
                checkOhSaOff.setChecked(checkOhWdOff.isChecked());
            });
            btnOhCopyToPh.setOnClickListener(v -> {
                Util.applyTimeFormat(editOhSaOpen);
                Util.applyTimeFormat(editOhSaClose);
                Util.applyTimeFormat(editOhSaBreakStart);
                Util.applyTimeFormat(editOhSaBreakEnd);
                editOhPhOpen.setText(editOhSaOpen.getText());
                editOhPhClose.setText(editOhSaClose.getText());
                editOhPhBreakStart.setText(editOhSaBreakStart.getText());
                editOhPhBreakEnd.setText(editOhSaBreakEnd.getText());
                checkOhPhOff.setChecked(checkOhSaOff.isChecked());
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
                editOhPhOpen, editOhPhClose, editOhPhBreakStart, editOhPhBreakEnd,
                editLsWdOpen, editLsWdClose, editLsWdBreakStart, editLsWdBreakEnd,
                editLsSaOpen, editLsSaClose, editLsSaBreakStart, editLsSaBreakEnd,
                editLsPhOpen, editLsPhClose, editLsPhBreakStart, editLsPhBreakEnd
            };
            for (EditText et : ohEditors) {
                if (et != null) {
                    Util.addNumberFilter(et);
                    Util.addTimeParseHandler(et);
                    Util.addClearRestoreHandler(et);
                    et.addTextChangedListener(new OhTextWatcher(et));
                    applyCellStyles(et, et.getText().toString(), false);
                }
            }
        }


        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        
        // Use a simple blue circle for current location instead of the default person icon
        android.graphics.Bitmap personBitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(personBitmap);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x330000FF); // Semi-transparent blue for the outer ring
        canvas.drawCircle(24, 24, 24, paint);
        paint.setColor(0xFFFFFFFF); // White border
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(24, 24, 10, paint);
        paint.setColor(0xFF4285F4); // Google Maps blue
        paint.setStyle(android.graphics.Paint.Style.FILL);
        canvas.drawCircle(24, 24, 10, paint);
        
        myLocationOverlay.setPersonIcon(personBitmap);
        myLocationOverlay.setDirectionIcon(personBitmap);
        myLocationOverlay.setPersonHotspot(24, 24);

        myLocationOverlay.enableMyLocation();
        map.getOverlays().add(myLocationOverlay);

        if (isNew && lastLocation != null) {
            GeoPoint currentPoint = new GeoPoint(lastLocation);
            originalLat = currentPoint.getLatitude();
            originalLon = currentPoint.getLongitude();
            marker.setPosition(currentPoint);
            map.getController().setCenter(currentPoint);
        }

        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                return checkMapDistanceAndRestrict();
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                return checkMapDistanceAndRestrict();
            }
        });

            btnSave.setOnClickListener(v -> {
                // 時刻入力を確定
                if (layoutFallback.getVisibility() != View.VISIBLE) {
                    for (EditText[] row : timeRows) {
                        for (EditText et : row) Util.applyTimeFormat(et);
                    }
                    Util.applyTimeFormat(editOhWdOpen);
                    Util.applyTimeFormat(editOhWdClose);
                    Util.applyTimeFormat(editOhWdBreakStart);
                    Util.applyTimeFormat(editOhWdBreakEnd);
                    Util.applyTimeFormat(editOhSaOpen);
                    Util.applyTimeFormat(editOhSaClose);
                    Util.applyTimeFormat(editOhSaBreakStart);
                    Util.applyTimeFormat(editOhSaBreakEnd);
                    Util.applyTimeFormat(editOhPhOpen);
                    Util.applyTimeFormat(editOhPhClose);
                    Util.applyTimeFormat(editOhPhBreakStart);
                    Util.applyTimeFormat(editOhPhBreakEnd);
                    Util.applyTimeFormat(editLsWdOpen);
                    Util.applyTimeFormat(editLsWdClose);
                    Util.applyTimeFormat(editLsWdBreakStart);
                    Util.applyTimeFormat(editLsWdBreakEnd);
                    Util.applyTimeFormat(editLsSaOpen);
                    Util.applyTimeFormat(editLsSaClose);
                    Util.applyTimeFormat(editLsSaBreakStart);
                    Util.applyTimeFormat(editLsSaBreakEnd);
                    Util.applyTimeFormat(editLsPhOpen);
                    Util.applyTimeFormat(editLsPhClose);
                    Util.applyTimeFormat(editLsPhBreakStart);
                    Util.applyTimeFormat(editLsPhBreakEnd);
                }

                float[] results = new float[1];
            GeoPoint markerPos = marker.getPosition();
            if (lastLocation != null) {
                Location.distanceBetween(markerPos.getLatitude(), markerPos.getLongitude(), lastLocation.getLatitude(), lastLocation.getLongitude(), results);
            }
            float distance = lastLocation != null ? results[0] : Float.MAX_VALUE;

            int maxDist = getMaxDistance();
            if (lastLocation == null || lastLocation.getAccuracy() > maxDist || distance > maxDist) {
                String tooFarMsg = getString(R.string.error_location_required);
                if (lastLocation != null) {
                    tooFarMsg += String.format("\n(現在の精度: %.1fm, 距離: %.1fm)", lastLocation.getAccuracy(), distance);
                }
                showErrorBanner(tooFarMsg);
                return;
            }

            if (!authRepository.isLoggedIn()) {
                // 地図メモとして保存
                String collection;
                String ref;
                String aTag = targetPoi.getTag("amenity");
                boolean isPostBoxLocal = isNew || "post_box".equals(aTag);

                if (isPostBoxLocal) {
                    if (layoutFallback.getVisibility() == View.VISIBLE) {
                        collection = targetPoi.getTag("collection_times");
                    } else {
                        Map<Days, List<? extends ITagPart>> weeklyTable = new HashMap<>();
                        for (int col = 0; col < 3; col++) {
                            android.widget.CheckBox checkColOff = (col == 0) ? checkColWdOff : (col == 1 ? checkColSaOff : checkColPhOff);
                            List<CollectionTime> targetList = null;

                            if (checkColOff.isChecked()) {
                                targetList = new ArrayList<>(); // off;
                            } else {
                                int lastMinutes = -1;
                                for (int r = 0; r < timeRows.size(); r++) {
                                    EditText et = timeRows.get(r)[col];
                                    Util.applyTimeFormat(et);
                                    String val = Util.normalizeNumber(et.getText().toString().trim());
                                    if (val.isEmpty()) continue;
                                    if (!TIME_PATTERN.matcher(val).matches()) {
                                        showErrorBanner(String.format(getString(R.string.error_time_format), val));
                                        return;
                                    }
                                    int minutes = SimpleScheduleParser.parseMinutes(val);
                                    if (minutes <= lastMinutes) {
                                        showErrorBanner(getString(R.string.error_time_order));
                                        return;
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
                        collection = scheduleParser.format(weeklyTable, ScheduleParser.TimeType.COLLECTION_TIMES);
                    }
                    TextInputEditText branchInputLocal = findViewById(R.id.edit_branch_value);
                    String branch = branchInputLocal.getText() != null ? branchInputLocal.getText().toString().trim() : "";
                    TextInputEditText refInputLocal = findViewById(R.id.edit_ref_value);
                    ref = refInputLocal.getText() != null ? refInputLocal.getText().toString().trim() : "";
                    
                    GeoPoint pos = marker.getPosition();
                    String addr = JpPostalUtil.getAddressText(targetPoi.getTags());
                    
                    String memo = getString(R.string.memo_postbox, collection, addr, branch, ref);
                    String specialNote = editSpecialNote.getText() != null ? editSpecialNote.getText().toString().trim() : "";
                    if (!specialNote.isEmpty()) {
                        memo += "\n特殊収集時刻パターン: " + specialNote;
                    }
                    JpPostalUtil.callOsmCreateNote(null, getString(R.string.app_name), memo, pos.getLatitude(), pos.getLongitude());
                } else {
                    // 郵便局の場合は営業時間
                    collection = targetPoi.getTag("opening_hours");
                    ref = "";
                    
                    GeoPoint pos = marker.getPosition();
                    String addr = JpPostalUtil.getAddressText(targetPoi.getTags());
                    
                    String memo = getString(R.string.memo_postoffice, collection, addr);
                    String specialNote = editSpecialNote.getText() != null ? editSpecialNote.getText().toString().trim() : "";
                    if (!specialNote.isEmpty()) {
                        memo += "\n特殊営業時間パターン: " + specialNote;
                    }
                    JpPostalUtil.callOsmCreateNote(null, getString(R.string.app_name), memo, pos.getLatitude(), pos.getLongitude());
                }

                Intent resultIntent = new Intent();
                resultIntent.putExtra("is_note", true);
                setResult(RESULT_OK, resultIntent);
                finish();
                return;
            }

            int titleRes = isNew ? R.string.confirm_add : R.string.confirm_save;
            int msgRes = isNew ? R.string.confirm_add_message : R.string.confirm_save_message;
            int btnRes = isNew ? R.string.add : R.string.save;

            new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(msgRes)
                .setPositiveButton(btnRes, (d, which) -> {
                    if (btnSave != null) {
                        btnSave.setEnabled(false);
                    }
                    showProgress(getString(R.string.progress_starting));
                    saveChanges();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });
    }

    /** 住所欄に現在の addr:* タグ由来の表示テキストを反映する。 */
    private void showAddress() {
        String text = JpPostalUtil.getAddressText(targetPoi.getTags());
        addressValue.setText(text.isEmpty() ? getString(R.string.data_none) : text);
    }

    private void updateKanaDisplay() {
        String reading = Util.getKana(targetPoi);
        if (reading != null && !reading.isEmpty()) {
            kanaValue.setText(reading);
        } else {
            kanaValue.setText("読み仮名なし");
        }
    }

    private void showKanaEditDialog() {
        String currentReading = Util.getKana(targetPoi);
        if (currentReading == null) currentReading = "";

        int margin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        
        FrameLayout container = new FrameLayout(this);
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint("例: とうきょうちゅうおうゆうびんきょく");
        layout.setErrorEnabled(true);

        TextInputEditText input = new TextInputEditText(this);
        input.setText(currentReading);
        input.setSingleLine();
        layout.addView(input);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(margin, margin / 2, margin, 0);
        layout.setLayoutParams(lp);
        container.addView(layout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("読み仮名の編集")
                .setView(container)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();

        Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

        Runnable validate = () -> {
            String text = input.getText().toString().trim();
            boolean isValid = Util.isValidReading(text);
            if (isValid) {
                layout.setError(null);
                saveButton.setEnabled(true);
            } else {
                layout.setError(getString(R.string.error_kana_format));
                saveButton.setEnabled(false);
            }
        };

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                validate.run();
            }
        });

        // 初期検証
        validate.run();

        saveButton.setOnClickListener(v -> {
            String newReading = input.getText().toString().trim();
            if (newReading.isEmpty()) {
                targetPoi.getTags().remove(Util.TAG_NAME_KANA);
            } else {
                targetPoi.getTags().put(Util.TAG_NAME_KANA, newReading);
            }
            targetPoi.getTags().remove("kana");
            updateKanaDisplay();
            dialog.dismiss();
        });
    }

    private void saveChanges() {
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
        // タグの更新と位置の更新をリポジトリ経由で行う
        Map<String, String> currentTags = new HashMap<>(targetPoi.getTags());
        String amenityTag = isNew ? "post_box" : currentTags.get("amenity");
        boolean isPostBox = "post_box".equals(amenityTag);
        
        String shape = "";
        String collection = "";
        String branch = "";
        String ref = "";

        if (isPostBox) {
            // 不要なタグを削除
            currentTags.remove("opening_hours");

            RadioGroup radioShape = findViewById(R.id.edit_radio_shape);
            int selectedShapeId = radioShape.getCheckedRadioButtonId();
            if (selectedShapeId == R.id.edit_shape_box) {
                shape = "柱上箱型";
                currentTags.put("support", "pole");
                currentTags.put("post_box:type", "lamp");
            } else if (selectedShapeId == R.id.edit_shape_pillar) {
                shape = "円柱";
                currentTags.put("support", "ground");
                currentTags.put("post_box:type", "pillar");
            } else {
                shape = "その他";
                currentTags.remove("support");
                currentTags.remove("post_box:type");
            }

            TextInputEditText branchInput = findViewById(R.id.edit_branch_value);
            branch = branchInput.getText() != null ? Util.normalizeNumber(branchInput.getText().toString().trim()) : "";
            if (!branch.isEmpty()) {
                currentTags.put("operator:branch", branch);
            } else {
                currentTags.remove("operator:branch");
            }

            TextInputEditText refInput = findViewById(R.id.edit_ref_value);
            ref = refInput.getText() != null ? Util.normalizeNumber(refInput.getText().toString().trim()) : "";
            if (!ref.isEmpty()) {
                currentTags.put("ref", ref);
            } else {
                currentTags.remove("ref");
            }

            if (layoutFallback.getVisibility() == View.VISIBLE) {
                collection = currentTags.get("collection_times");
            } else {
                Map<Days, List<? extends ITagPart>> weeklyTable = new HashMap<>();
                for (int col = 0; col < 3; col++) {
                    android.widget.CheckBox checkColOff = (col == 0) ? checkColWdOff : (col == 1 ? checkColSaOff : checkColPhOff);
                    List<CollectionTime> targetList = null;

                    if (checkColOff.isChecked()) {
                        targetList = new ArrayList<>(); // off;
                    } else {
                        int lastMinutes = -1;
                        for (int r = 0; r < timeRows.size(); r++) {
                            EditText et = timeRows.get(r)[col];
                            Util.applyTimeFormat(et);
                            String val = Util.normalizeNumber(et.getText().toString().trim());
                            if (val.isEmpty()) continue;

                            if (!TIME_PATTERN.matcher(val).matches()) {
                                showErrorBanner(String.format(getString(R.string.error_time_format), val));
                                if (btnSave != null) btnSave.setEnabled(true);
                                dismissProgress();
                                return;
                            }

                            int minutes = SimpleScheduleParser.parseMinutes(val);
                            if (minutes <= lastMinutes) {
                                showErrorBanner(getString(R.string.error_time_order));
                                if (btnSave != null) btnSave.setEnabled(true);
                                dismissProgress();
                                return;
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

                collection = scheduleParser.format(weeklyTable, ScheduleParser.TimeType.COLLECTION_TIMES);
                if (collection != null && !collection.isEmpty()) {
                    currentTags.put("collection_times", collection);
                } else {
                    currentTags.remove("collection_times");
                }
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
                    Util.applyTimeFormat(editOhWdOpen);
                    Util.applyTimeFormat(editOhWdClose);
                    Util.applyTimeFormat(editOhWdBreakStart);
                    Util.applyTimeFormat(editOhWdBreakEnd);
                    String wdOpen = Util.normalizeNumber(editOhWdOpen.getText().toString().trim());
                    String wdClose = Util.normalizeNumber(editOhWdClose.getText().toString().trim());
                    String wdBreakStart = Util.normalizeNumber(editOhWdBreakStart.getText().toString().trim());
                    String wdBreakEnd = Util.normalizeNumber(editOhWdBreakEnd.getText().toString().trim());
                    wdTimes.addAll(formatOpeningTimeRange(wdOpen, wdClose, wdBreakStart, wdBreakEnd));
                }
                for (String d : new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) {
                    weeklyTable.put(Days.valueOf(d), wdTimes);
                }
                
                // 土曜
                List<ITagPart> saTimes = new ArrayList<>();
                if (!checkOhSaOff.isChecked()) {
                    Util.applyTimeFormat(editOhSaOpen);
                    Util.applyTimeFormat(editOhSaClose);
                    Util.applyTimeFormat(editOhSaBreakStart);
                    Util.applyTimeFormat(editOhSaBreakEnd);
                    String saOpen = Util.normalizeNumber(editOhSaOpen.getText().toString().trim());
                    String saClose = Util.normalizeNumber(editOhSaClose.getText().toString().trim());
                    String saBreakStart = Util.normalizeNumber(editOhSaBreakStart.getText().toString().trim());
                    String saBreakEnd = Util.normalizeNumber(editOhSaBreakEnd.getText().toString().trim());
                    saTimes.addAll(formatOpeningTimeRange(saOpen, saClose, saBreakStart, saBreakEnd));
                }
                weeklyTable.put(Days.SATURDAY, saTimes);
                
                // 日祝
                List<ITagPart> phTimes = new ArrayList<>();
                if (!checkOhPhOff.isChecked()) {
                    Util.applyTimeFormat(editOhPhOpen);
                    Util.applyTimeFormat(editOhPhClose);
                    Util.applyTimeFormat(editOhPhBreakStart);
                    Util.applyTimeFormat(editOhPhBreakEnd);
                    String phOpen = Util.normalizeNumber(editOhPhOpen.getText().toString().trim());
                    String phClose = Util.normalizeNumber(editOhPhClose.getText().toString().trim());
                    String phBreakStart = Util.normalizeNumber(editOhPhBreakStart.getText().toString().trim());
                    String phBreakEnd = Util.normalizeNumber(editOhPhBreakEnd.getText().toString().trim());
                    phTimes.addAll(formatOpeningTimeRange(phOpen, phClose, phBreakStart, phBreakEnd));
                }
                weeklyTable.put(Days.SUNDAY, phTimes);
                weeklyTable.put(Days.PUBLIC_HOLIDAY, phTimes);

                String openingHours = scheduleParser.format(weeklyTable, ScheduleParser.TimeType.OPENING_HOURS);
                currentTags.put("opening_hours", openingHours);
            }

            // Limited Service (Yu-Yu Window)
            int selectedLsId = radioLimitedService.getCheckedRadioButtonId();
            if (selectedLsId == R.id.radio_ls_yes) {
                currentTags.put("limited_service:mail", "yes");
                Map<Days, List<? extends ITagPart>> lsWeeklyTable = new HashMap<>();
                
                // 平日
                List<ITagPart> wdTimes = new ArrayList<>();
                if (!checkLsWdOff.isChecked()) {
                    Util.applyTimeFormat(editLsWdOpen);
                    Util.applyTimeFormat(editLsWdClose);
                    Util.applyTimeFormat(editLsWdBreakStart);
                    Util.applyTimeFormat(editLsWdBreakEnd);
                    wdTimes.addAll(formatOpeningTimeRange(
                        Util.normalizeNumber(editLsWdOpen.getText().toString().trim()),
                        Util.normalizeNumber(editLsWdClose.getText().toString().trim()),
                        Util.normalizeNumber(editLsWdBreakStart.getText().toString().trim()),
                        Util.normalizeNumber(editLsWdBreakEnd.getText().toString().trim())
                    ));
                }
                for (String d : new String[]{"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}) {
                    lsWeeklyTable.put(Days.valueOf(d), wdTimes);
                }
                
                // 土曜
                List<ITagPart> saTimes = new ArrayList<>();
                if (!checkLsSaOff.isChecked()) {
                    Util.applyTimeFormat(editLsSaOpen);
                    Util.applyTimeFormat(editLsSaClose);
                    Util.applyTimeFormat(editLsSaBreakStart);
                    Util.applyTimeFormat(editLsSaBreakEnd);
                    saTimes.addAll(formatOpeningTimeRange(
                        Util.normalizeNumber(editLsSaOpen.getText().toString().trim()),
                        Util.normalizeNumber(editLsSaClose.getText().toString().trim()),
                        Util.normalizeNumber(editLsSaBreakStart.getText().toString().trim()),
                        Util.normalizeNumber(editLsSaBreakEnd.getText().toString().trim())
                    ));
                }
                lsWeeklyTable.put(Days.SATURDAY, saTimes);
                
                // 日祝
                List<ITagPart> phTimes = new ArrayList<>();
                if (!checkLsPhOff.isChecked()) {
                    Util.applyTimeFormat(editLsPhOpen);
                    Util.applyTimeFormat(editLsPhClose);
                    Util.applyTimeFormat(editLsPhBreakStart);
                    Util.applyTimeFormat(editLsPhBreakEnd);
                    phTimes.addAll(formatOpeningTimeRange(
                        Util.normalizeNumber(editLsPhOpen.getText().toString().trim()),
                        Util.normalizeNumber(editLsPhClose.getText().toString().trim()),
                        Util.normalizeNumber(editLsPhBreakStart.getText().toString().trim()),
                        Util.normalizeNumber(editLsPhBreakEnd.getText().toString().trim())
                    ));
                }
                lsWeeklyTable.put(Days.SUNDAY, phTimes);
                lsWeeklyTable.put(Days.PUBLIC_HOLIDAY, phTimes);

                String lsHours = scheduleParser.format(lsWeeklyTable, ScheduleParser.TimeType.OPENING_HOURS);
                if (lsHours != null && !lsHours.isEmpty()) {
                    currentTags.put("opening_hours:limited_service", lsHours);
                } else {
                    currentTags.remove("opening_hours:limited_service");
                }
            } else if (selectedLsId == R.id.radio_ls_no) {
                currentTags.put("limited_service:mail", "no");
                currentTags.remove("opening_hours:limited_service");
            } else {
                // 不明
                currentTags.remove("limited_service:mail");
                currentTags.remove("opening_hours:limited_service");
            }
        }
        
        // 移動後の位置を取得
        GeoPoint pos = marker.getPosition();
        String note = editSpecialNote.getText() != null ? Util.normalizeNumber(editSpecialNote.getText().toString().trim()) : "";
        if (!note.isEmpty()) {
            currentTags.put("note", note);
        } else {
            currentTags.remove("note");
        }

        PoiRepository.PoiSaveCallback callback = new PoiRepository.PoiSaveCallback() {
            @Override
            public void onSuccess() {
                dismissProgress();
                if (btnSave != null) btnSave.setEnabled(true);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("is_new", isNew);
                setResult(RESULT_OK, resultIntent);
                finish();
            }

            @Override
            public void onError(String message) {
                dismissProgress();
                if (btnSave != null) btnSave.setEnabled(true);
                showErrorBanner("エラー: " + message);
            }

            @Override
            public void onProgress(String message) {
                showProgress(message);
            }
        };

        if (isNew) {
            Map<String, String> addressTags = new HashMap<>();
            for (Map.Entry<String, String> entry : currentTags.entrySet()) {
                if (entry.getKey().startsWith("addr:")) {
                    addressTags.put(entry.getKey(), entry.getValue());
                }
            }
            repository.addPostBox(pos.getLatitude(), pos.getLongitude(), shape, branch, ref, collection, note, addressTags, callback);
        } else {
            OsmPoi updatedPoi = new OsmPoi(
                    targetPoi.getId(),
                    pos.getLatitude(),
                    pos.getLongitude(),
                    targetPoi.getType(),
                    currentTags,
                    targetPoi.getVer()
            );
            String poiName = updatedPoi.getTag("name");
            if (poiName == null) {
                poiName = "post_box".equals(updatedPoi.getTag("amenity")) ? "郵便ポスト" : updatedPoi.getType();
            }
            String comment = getString(R.string.changeset_comment_update, poiName);
            repository.savePoi(updatedPoi, comment, callback);
        }
    }

    private void updateLockButtonStyle(ImageButton btn) {
        if (btn == null) return;
        android.graphics.drawable.Drawable bg = btn.getBackground();
        if (bg != null) {
            bg = bg.mutate();
            if (isMapUnlocked) {
                androidx.core.graphics.drawable.DrawableCompat.setTint(bg, ContextCompat.getColor(this, R.color.white));
            } else {
                androidx.core.graphics.drawable.DrawableCompat.setTint(bg, ContextCompat.getColor(this, R.color.brand_red));
            }
            btn.setBackground(bg);
        }

        if (isMapUnlocked) {
            btn.setImageResource(R.drawable.ic_lock_unlocked);
            btn.setColorFilter(ContextCompat.getColor(this, R.color.brand_red));
        } else {
            btn.setImageResource(R.drawable.ic_lock_locked);
            btn.setColorFilter(ContextCompat.getColor(this, R.color.white));
        }
    }

    private boolean checkMapDistanceAndRestrict() {
        if (isResettingCenter) return true;
        GeoPoint center = (GeoPoint) map.getMapCenter();
        float[] results = new float[1];
        Location.distanceBetween(originalLat, originalLon, center.getLatitude(), center.getLongitude(), results);
        int maxDist = getMaxDistance();
        if (results[0] > maxDist) {
            isResettingCenter = true;
            map.getController().setCenter(new GeoPoint(originalLat, originalLon));
            isResettingCenter = false;
            Toast.makeText(EditPoiActivity.this, maxDist + "m以上の移動は認められません", Toast.LENGTH_SHORT).show();
            return true;
        }
        marker.setPosition(center);
        updateLocationStatus(lastLocation);
        return true;
    }

    private void updateLocationStatus(Location location) {
        if (location != null) {
            float[] results = new float[1];
            GeoPoint markerPos = marker.getPosition();
            Location.distanceBetween(markerPos.getLatitude(), markerPos.getLongitude(), location.getLatitude(), location.getLongitude(), results);
            float distance = results[0];

            int maxDist = getMaxDistance();
            String status = getString(R.string.location_status_tracking, distance);
            boolean isAccuracyNg = location.getAccuracy() > maxDist;
            boolean isDistanceNg = distance > maxDist;

            if (isAccuracyNg || isDistanceNg) {
                if (isAccuracyNg) {
                    status += getString(R.string.location_status_low_accuracy, maxDist);
                }
                if (isDistanceNg) {
                    status += getString(R.string.location_status_too_far, maxDist);
                }
                textLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_red));
            } else {
                textLocationStatus.setTextColor(getThemeColor(R.attr.colorEditable));
            }
            textLocationStatus.setText(status);
        } else {
            textLocationStatus.setText(R.string.location_status_fetching);
            textLocationStatus.setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary));
        }
    }

    private void showProgress(String message) {
        if (isFinishing() || isDestroyed()) return;
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
        if (isFinishing() || isDestroyed()) {
            progressDialog = null;
            return;
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    private void showErrorBanner(String message) {
        if (topBanner == null) return;
        topBanner.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_red));
        topBanner.setText(message);
        topBanner.setVisibility(View.VISIBLE);
        findViewById(R.id.edit_scroll_view).scrollTo(0, 0);

        topBanner.postDelayed(() -> {
            topBanner.setVisibility(View.GONE);
        }, 5000);
    }

    private void addNewRow(String initialValue1, String initialValue2, String initialValue3) {
        TableRow row = new TableRow(this);
        EditText[] rowEditors = new EditText[3];
        String[] initialValues = {initialValue1, initialValue2, initialValue3};
        for (int i = 0; i < 3; i++) {
            EditText et = new EditText(this);
            et.setHint("--:--");
            et.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            Util.addNumberFilter(et);
            Util.addTimeParseHandler(et);
            Util.addClearRestoreHandler(et);
            et.setGravity(Gravity.CENTER);
            et.setText(Util.normalizeNumber(initialValues[i]));
            et.setPadding((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                         (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                         (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()),
                         (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, getResources().getDisplayMetrics()));
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            
            TableRow.LayoutParams params = new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1.0f);
            int margin = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, getResources().getDisplayMetrics());
            params.setMargins(margin, margin, margin, margin);
            et.setLayoutParams(params);
            
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

        GradientDrawable background = (GradientDrawable) bg.findDrawableByLayerId(R.id.cell_background);

        if (value.isEmpty()) {
            // 未入力
            background.setColor(ContextCompat.getColor(this, R.color.gray_bg));
            et.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            et.setHintTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        } else {
            // 入力済み
            if (isModified) {
                // 変更あり: 薄い青背景にして目立たせる
                background.setColor(0xFFE3F2FD);
            } else {
                background.setColor(ContextCompat.getColor(this, R.color.cell_bg));
            }
            et.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
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
                    editors[i][0].setText(Util.normalizeNumber(open));
                    editors[i][1].setText(Util.normalizeNumber(close));
                    editors[i][2].setText(Util.normalizeNumber(bStart));
                    editors[i][3].setText(Util.normalizeNumber(bEnd));
                }
                for (EditText et : editors[i]) applyCellStyles(et, et.getText().toString(), false);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean parseAndFillLimitedService(String tag) {
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
                {editLsWdOpen, editLsWdClose, editLsWdBreakStart, editLsWdBreakEnd},
                {editLsSaOpen, editLsSaClose, editLsSaBreakStart, editLsSaBreakEnd},
                {editLsPhOpen, editLsPhClose, editLsPhBreakStart, editLsPhBreakEnd}
            };

            for (int i = 0; i < dayGroups.length; i++) {
                IDaySchedule daySchedule = weeklyTable.get(dayGroups[i]);
                android.widget.CheckBox checkOff = (i == 0) ? checkLsWdOff : (i == 1 ? checkLsSaOff : checkLsPhOff);
                
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
                    editors[i][0].setText(Util.normalizeNumber(open));
                    editors[i][1].setText(Util.normalizeNumber(close));
                    editors[i][2].setText(Util.normalizeNumber(bStart));
                    editors[i][3].setText(Util.normalizeNumber(bEnd));
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
                IDaySchedule dSched = weeklyTable.get(Days.valueOf(day));
                if ((wdSched == null ? null : wdSched.status()) != (dSched == null ? null : dSched.status())) return false;
                if (!weekday.equals(schedToList(dSched))) return false;
            }

            // 日曜と祝日が同じかチェック
            boolean hasPH = tag.contains("PH");
            if (hasPH) {
                if ((suSched == null ? null : suSched.status()) != (phSched == null ? null : phSched.status())) return false;
                if (!sunday.equals(holiday)) return false;
            }

            // 収集なし(off;)のチェック状態を反映
            checkColWdOff.setChecked(wdSched != null && wdSched.status() == pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser.DayStatus.CLOSED_DAY);
            checkColSaOff.setChecked(saSched != null && saSched.status() == pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser.DayStatus.CLOSED_DAY);
            checkColPhOff.setChecked(phSched != null && phSched.status() == pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser.DayStatus.CLOSED_DAY);

            if (!hasPH) {
                findViewById(R.id.layout_holiday_warning).setVisibility(View.VISIBLE);
                TextView header = findViewById(R.id.header_sun_ph);
                if (header != null) header.setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary));
                 
                findViewById(R.id.btn_apply_sun_to_ph).setOnClickListener(v -> {
                    findViewById(R.id.layout_holiday_warning).setVisibility(View.GONE);
                    if (header != null) header.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
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
    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startLocationUpdates(1000, 0);
            if (myLocationOverlay != null) {
                myLocationOverlay.enableMyLocation();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        viewModel.stopLocationUpdates();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }
}
