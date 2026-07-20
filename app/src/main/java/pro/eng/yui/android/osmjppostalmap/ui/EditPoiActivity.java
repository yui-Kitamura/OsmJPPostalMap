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
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.AuthRepository;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EditPoiActivity extends AppCompatActivity {

    private MapView map;
    private Marker marker;
    private TextInputEditText tagInput;
    private TableLayout tableCollection;
    private TextView textFallback;
    private View layoutFallback;
    private final List<EditText[]> timeRows = new ArrayList<>();
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
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

        // Edge-to-Edge adjustment
        View mainLayout = findViewById(R.id.edit_scroll_view);
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
        ((PoiRepositoryImpl)repository).setAccessToken(authRepository.getAccessToken());

        // IntentからPOI情報を受け取る
        long id = getIntent().getLongExtra("POI_ID", 0);
        String type = getIntent().getStringExtra("POI_TYPE");
        
        // 既存の座標があればそれを使用、なければデフォルト
        double initialLat = getIntent().getDoubleExtra("POI_LAT", 35.6812);
        double initialLon = getIntent().getDoubleExtra("POI_LON", 139.7671);
        
        java.util.Map<String, String> tags = new java.util.HashMap<>();
        if (getIntent().hasExtra("TAG_AMENITY")) tags.put("amenity", getIntent().getStringExtra("TAG_AMENITY"));
        if (getIntent().hasExtra("TAG_NAME")) tags.put("name", getIntent().getStringExtra("TAG_NAME"));
        if (getIntent().hasExtra("TAG_OPENING_HOURS")) tags.put("opening_hours", getIntent().getStringExtra("TAG_OPENING_HOURS"));
        if (getIntent().hasExtra("TAG_COLLECTION_TIMES")) tags.put("collection_times", getIntent().getStringExtra("TAG_COLLECTION_TIMES"));

        targetPoi = new OsmPoi(id, initialLat, initialLon, type != null ? type : "node", tags);

        TextView title = findViewById(R.id.edit_title);
        tagInput = findViewById(R.id.edit_tag_value);
        View tagLayout = findViewById(R.id.edit_tag_layout);
        View collectionLayout = findViewById(R.id.layout_collection_edit);
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

        String amenity = targetPoi.getTag("amenity");
        boolean isPostBox = "post_box".equals(amenity);
        title.setText(isPostBox ? "郵便ポストの編集" : "郵便局の編集");

        if (isPostBox) {
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
            tagInput.setText(hours);
            
            // 営業時間も同様にフォールバックからの強制編集を可能にする
            // 現在は単純なテキスト入力だが、将来的にパースを導入した場合に備える
            collectionLayout.setVisibility(View.GONE);
            layoutFallback.setVisibility(View.GONE);
            tagLayout.setVisibility(View.VISIBLE);

            btnForceEdit.setOnClickListener(v -> {
                layoutFallback.setVisibility(View.GONE);
                tagLayout.setVisibility(View.VISIBLE);
            });
        }

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
            if (layoutFallback.getVisibility() == View.VISIBLE) {
                // パース失敗時（フォールバック表示中）は時刻タグを更新しない（位置のみ更新）
                // 既に targetPoi.getTags() には元の値が入っている
            } else {
                String collection = formatCollectionTimes();
                if (collection == null) return;
                targetPoi.getTags().put("collection_times", collection);
            }
        } else {
            if (layoutFallback.getVisibility() == View.VISIBLE) {
                // パース失敗時（フォールバック表示中）は営業時間タグを更新しない
            } else {
                String newValue = tagInput.getText() != null ? tagInput.getText().toString() : "";
                targetPoi.getTags().put("opening_hours", newValue);
            }
        }
        
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

    private boolean parseAndFillCollectionTimes(String tag) {
        String[] parts = tag.split(";");
        // 各曜日のスケジュールを個別に保持
        java.util.Map<String, List<String>> dailySchedules = new java.util.HashMap<>();
        String[] allDays = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (String d : allDays) dailySchedules.put(d, new ArrayList<>());
        List<String> holidayTimes = new ArrayList<>();

        try {
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                
                String[] dayAndTime = part.split(" ", 2);
                if (dayAndTime.length < 2) return false;
                
                String dayPart = dayAndTime[0].trim();
                String timePart = dayAndTime[1].trim();
                String[] times = timePart.split(",");
                
                List<String> expandedDays = new ArrayList<>();
                String[] dayTokens = dayPart.split(",");
                for (String token : dayTokens) {
                    token = token.trim();
                    if (token.equals("PH")) {
                        expandedDays.add("PH");
                    } else if (token.contains("-")) {
                        String[] range = token.split("-");
                        int start = -1, end = -1;
                        for (int i = 0; i < 7; i++) {
                            if (allDays[i].equals(range[0])) start = i;
                            if (allDays[i].equals(range[1])) end = i;
                        }
                        if (start != -1 && end != -1) {
                            for (int i = start; i <= end; i++) expandedDays.add(allDays[i]);
                        }
                    } else {
                        expandedDays.add(token);
                    }
                }

                for (String day : expandedDays) {
                    List<String> targetList;
                    if (day.equals("PH")) {
                        targetList = holidayTimes;
                    } else {
                        targetList = dailySchedules.get(day);
                    }
                    
                    if (targetList != null) {
                        for (String t : times) {
                            String trimmedTime = t.trim();
                            if (!targetList.contains(trimmedTime)) targetList.add(trimmedTime);
                        }
                    }
                }
            }
            // 各リストを昇順に並べ替え
            java.util.Comparator<String> timeComp = (a, b) -> Integer.compare(parseMinutes(a), parseMinutes(b));
            for (List<String> list : dailySchedules.values()) list.sort(timeComp);
            holidayTimes.sort(timeComp);

            List<String> monSchedule = dailySchedules.get("Mo");

            // 日曜と祝日が同じかチェック（UI上は「日祝」列にまとめているため）
            boolean hasPH = false;
            for (String part : parts) if (part.contains("PH")) hasPH = true;

            if (hasPH) {
                if (!dailySchedules.get("Su").equals(holidayTimes)) {
                    return false;
                }
            } else {
                // PHがない場合、警告を表示する準備をする
                findViewById(R.id.layout_holiday_warning).setVisibility(View.VISIBLE);
                TextView header = findViewById(R.id.header_sun_ph);
                if (header != null) header.setTextColor(android.graphics.Color.RED);
                
                findViewById(R.id.btn_apply_sun_to_ph).setOnClickListener(v -> {
                    findViewById(R.id.layout_holiday_warning).setVisibility(View.GONE);
                    if (header != null) header.setTextColor(android.graphics.Color.BLACK);
                    // 保存時にPHが含まれるようにするフラグなどは不要。
                    // formatCollectionTimes で holiday (列2) が空でなければ PH を出力するようにすれば良い。
                });
            }

            // 火〜金のスケジュールが月曜日と一致するか確認
            for (String day : new String[]{"Tu", "We", "Th", "Fr"}) {
                if (!monSchedule.equals(dailySchedules.get(day))) {
                    return false;
                }
            }

            List<String> weekday = monSchedule;
            List<String> saturday = dailySchedules.get("Sa");
            List<String> holiday = hasPH ? holidayTimes : dailySchedules.get("Su");

            int maxRows = Math.max(weekday.size(), Math.max(saturday.size(), holiday.size()));
            for (int i = 0; i < maxRows; i++) {
                addNewRow();
                if (i < weekday.size()) {
                    String val = weekday.get(i).trim();
                    timeRows.get(i)[0].setText(val);
                    applyCellStyles(timeRows.get(i)[0], val, false);
                }
                if (i < saturday.size()) {
                    String val = saturday.get(i).trim();
                    timeRows.get(i)[1].setText(val);
                    applyCellStyles(timeRows.get(i)[1], val, false);
                }
                if (i < holiday.size()) {
                    String val = holiday.get(i).trim();
                    timeRows.get(i)[2].setText(val);
                    applyCellStyles(timeRows.get(i)[2], val, false);
                }
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }
}
