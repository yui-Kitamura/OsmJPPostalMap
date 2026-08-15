package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.Util;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.ui.EditPoiActivity;
import pro.eng.yui.android.osmjppostalmap.ui.MainActivity;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.Days;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.IDaySchedule;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

public class PoiDetailsDialog {

    private final Context context;
    private final OsmPoi poi;
    private ScheduleResult schedule;
    private ScheduleResult limitedServiceSchedule;
    private Location currentLocation;

    private AlertDialog dialog;
    private TextView statusText;
    private TextView nextEventText;
    private TableLayout table;
    private TextView rawTagText;
    private TextView checkDateText;
    private Button confirmNoChangeButton;
    private TextView addressText;

    private View lsLayout;
    private TextView lsStatus;
    private TableLayout lsTable;

    public PoiDetailsDialog(Context context, OsmPoi poi, ScheduleResult schedule, ScheduleResult limitedServiceSchedule, Location currentLocation) {
        this.context = context;
        this.poi = poi;
        this.schedule = schedule;
        this.limitedServiceSchedule = limitedServiceSchedule;
        this.currentLocation = currentLocation;
    }

    public static PoiDetailsDialog show(Context context, OsmPoi poi, ScheduleResult schedule, ScheduleResult limitedServiceSchedule, Location currentLocation) {
        PoiDetailsDialog detailsDialog = new PoiDetailsDialog(context, poi, schedule, limitedServiceSchedule, currentLocation);
        detailsDialog.show();
        return detailsDialog;
    }

    public void show() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);

        ScheduleParser.Amenity amenity =
                "post_office".equals(poi.getTag("amenity")) ? 
                ScheduleParser.Amenity.POST_OFFICE : 
                ScheduleParser.Amenity.POST_BOX;
        boolean isPostBox = (amenity == ScheduleParser.Amenity.POST_BOX);
        
        View titleView = LayoutInflater.from(context).inflate(R.layout.dialog_poi_details_title, null);
        TextView titleText = titleView.findViewById(R.id.dialog_title_text);
        
        String name = poi.getTag("name");
        String reading = Util.getKana(poi);
        if (isPostBox) {
            titleText.setText(R.string.amenity_postbox);
        } else if (reading != null && !reading.isEmpty()) {
            titleText.setText(Util.getRubySpannable(name, reading, titleText.getTextSize()));
        } else {
            titleText.setText(name);
        }
        
        ImageButton openOsmButton = titleView.findViewById(R.id.dialog_open_osm);

        openOsmButton.setOnClickListener(v -> {
            String type = poi.getType(); // node or way
            long id = poi.getId();
            String url = "https://www.openstreetmap.org/" + type + "/" + id;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        });

        builder.setCustomTitle(titleView);
        
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_poi_details, null);
        statusText = view.findViewById(R.id.dialog_status);
        nextEventText = view.findViewById(R.id.dialog_next_event);
        table = view.findViewById(R.id.dialog_weekly_table);
        rawTagText = view.findViewById(R.id.dialog_raw_tag);
        checkDateText = view.findViewById(R.id.dialog_check_date);
        addressText = view.findViewById(R.id.dialog_address);
        
        lsLayout = view.findViewById(R.id.dialog_limited_service_layout);
        lsStatus = view.findViewById(R.id.dialog_limited_service_status);
        lsTable = view.findViewById(R.id.dialog_limited_service_weekly_table);
        confirmNoChangeButton = view.findViewById(R.id.dialog_btn_no_change);

        confirmNoChangeButton.setOnClickListener(v -> {
            int maxDist = 50;

            if (currentLocation == null) {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.error_location_not_found)
                        .setMessage(R.string.error_location_required)
                        .setPositiveButton(R.string.btn_close, null)
                        .show();
                return;
            }

            float[] results = new float[1];
            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(),
                    poi.getLat(), poi.getLon(), results);
            float distance = results[0];
            float accuracy = currentLocation.getAccuracy();

            if (distance > maxDist || accuracy > maxDist) {
                String tooFarMsg = context.getString(R.string.error_location_required);
                tooFarMsg += String.format("\n(現在の精度: %.1fm, 距離: %.1fm)", accuracy, distance);
                new MaterialAlertDialogBuilder(context)
                        .setTitle("位置情報エラー")
                        .setMessage(tooFarMsg)
                        .setPositiveButton(R.string.btn_close, null)
                        .show();
                return;
            }

            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.btn_confirm_no_change)
                    .setMessage(R.string.confirm_no_change_dialog_message)
                    .setPositiveButton("OK", (dialogInterface, i) -> {
                        confirmNoChangeButton.setEnabled(false);

                        Map<String, String> newTags = poi.getTags() != null ? new HashMap<>(poi.getTags()) : new HashMap<>();
                        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        newTags.put("check_date", today);

                        OsmPoi updatedPoi = new OsmPoi(
                                poi.getId(),
                                poi.getLat(),
                                poi.getLon(),
                                poi.getType(),
                                newTags,
                                poi.getVer()
                        );

                        String comment = context.getString(R.string.changeset_comment_confirm_no_change);

                        PoiRepository repository = PoiRepositoryImpl.getInstance();
                        repository.savePoi(updatedPoi, comment, new PoiRepository.PoiSaveCallback() {
                            @Override
                            public void onSuccess() {
                                if (dialog != null && dialog.isShowing()) {
                                    dialog.dismiss();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                confirmNoChangeButton.post(() -> confirmNoChangeButton.setEnabled(true));
                            }
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        updateUI();

        builder.setView(view);
        builder.setPositiveButton(R.string.btn_close, null);
        builder.setNeutralButton(R.string.edit, (dialog, which) -> {
            android.content.Intent intent = new android.content.Intent(context, EditPoiActivity.class);
            intent.putExtra("POI_ID", poi.getId());
            intent.putExtra("POI_TYPE", poi.getType());
            intent.putExtra("POI_LAT", poi.getLat());
            intent.putExtra("POI_LON", poi.getLon());
            intent.putExtra("POI_VER", poi.getVer());
            
            // すべてのタグを渡す（住所は編集画面側の住所編集ダイアログで編集する）
            intent.putExtra("POI_TAGS",
                    poi.getTags() != null ? new HashMap<>(poi.getTags()) : new HashMap<String, String>());

            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                org.osmdroid.views.MapView map = activity.findViewById(R.id.map);
                if (map != null) {
                    intent.putExtra("ZOOM_LEVEL", map.getZoomLevelDouble());
                }
                activity.launchEditPoi(intent);
            } else {
                context.startActivity(intent);
            }
        });
        
        dialog = builder.create();
        dialog.show();
    }

    public void update(ScheduleResult schedule, ScheduleResult limitedServiceSchedule, Location currentLocation) {
        this.schedule = schedule;
        this.limitedServiceSchedule = limitedServiceSchedule;
        this.currentLocation = currentLocation;
        if (dialog != null && dialog.isShowing()) {
            updateUI();
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    public OsmPoi getPoi() {
        return poi;
    }

    private void updateUI() {
        ScheduleParser.Amenity amenity =
                "post_office".equals(poi.getTag("amenity")) ? 
                ScheduleParser.Amenity.POST_OFFICE : 
                ScheduleParser.Amenity.POST_BOX;
        boolean isPostBox = (amenity == ScheduleParser.Amenity.POST_BOX);

        if (schedule != null) {
            statusText.setText(schedule.getTodayStatus());
            
            if (isPostBox) {
                StringBuilder msg = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.JAPAN);
                long now = System.currentTimeMillis();

                if (schedule.getNextEvent() != null) {
                    long timestamp = schedule.getNextEvent().getTimestamp().toInstant().toEpochMilli();
                    String timeStr = sdf.format(new Date(timestamp));
                    LocalDate eventDate = schedule.getNextEvent().getTimestamp().toLocalDate();
                    long daysDiff = ChronoUnit.DAYS.between(LocalDate.now(), eventDate);

                    String dayPrefix;
                    if (daysDiff == 0) {
                        dayPrefix = context.getString(R.string.day_today);
                    } else if (daysDiff == 1) {
                        dayPrefix = context.getString(R.string.day_tomorrow);
                    } else {
                        dayPrefix = eventDate.format(DateTimeFormatter.ofPattern("dd日"));
                    }

                    long remainingMinutes = (timestamp - now) / 60000;
                    long h = remainingMinutes / 60;
                    long m = remainingMinutes % 60;
                    String diffStr = (h > 0) ? context.getString(R.string.time_duration_hm, h, m) : context.getString(R.string.time_duration_m, m);

                    if (schedule.getCurrentState() == ScheduleResult.CurrentState.TODAY_FINISHED ||
                        schedule.getNextEvent().getTimestamp().toLocalDate().isAfter(java.time.LocalDate.now())) {
                        msg.append(context.getString(R.string.next_event_prefix)).append(" ").append(dayPrefix).append(" ").append(timeStr).append(" (").append(diffStr).append(")");
                    } else {
                        msg.append(diffStr);
                    }
                }

                if (schedule.getFollowingEvent() != null) {
                    long fTimestamp = schedule.getFollowingEvent().getTimestamp().toInstant().toEpochMilli();
                    String followTime = sdf.format(new Date(fTimestamp));
                    LocalDate fEventDate = schedule.getFollowingEvent().getTimestamp().toLocalDate();
                    long fDaysDiff = ChronoUnit.DAYS.between(LocalDate.now(), fEventDate);

                    String fPrefix;
                    if (fDaysDiff == 0) {
                        fPrefix = context.getString(R.string.day_today);
                    } else if (fDaysDiff == 1) {
                        fPrefix = context.getString(R.string.day_tomorrow);
                    } else {
                        fPrefix = fEventDate.format(DateTimeFormatter.ofPattern("dd日"));
                    }

                    long fRemainingMinutes = (fTimestamp - now) / 60000;
                    long fh = fRemainingMinutes / 60;
                    long fm = fRemainingMinutes % 60;
                    String fDiffStr = (fh > 0) ? context.getString(R.string.time_duration_hm, fh, fm) : context.getString(R.string.time_duration_m, fm);
                    
                    if (msg.length() > 0) msg.append("\n");
                    msg.append(context.getString(R.string.missed_event_prefix)).append(" ").append(fPrefix).append(" ").append(followTime).append(" (").append(fDiffStr).append(")");
                }

                if (msg.length() > 0) {
                    nextEventText.setText(msg.toString());
                    nextEventText.setVisibility(View.VISIBLE);
                } else {
                    nextEventText.setVisibility(View.GONE);
                }
            } else {
                // ポスト以外（郵便局など）
                if (schedule.getNextEvent() != null) {
                    long remainingMinutes = (schedule.getNextEvent().getTimestamp().toInstant().toEpochMilli() - System.currentTimeMillis()) / 60000;
                    long h = remainingMinutes / 60;
                    long m = remainingMinutes % 60;
                    String diffStr = (h > 0) ? context.getString(R.string.time_duration_hm, h, m) : context.getString(R.string.time_duration_m, m);
                    nextEventText.setText(diffStr);
                    nextEventText.setVisibility(View.VISIBLE);
                } else {
                    nextEventText.setVisibility(View.GONE);
                }
            }

            // スケジュール表の作成
            populateWeeklyTable(context, table, schedule, isPostBox);
            
            rawTagText.setText("Raw: " + schedule.getRawTagValue().getOrigin() + " (v" + poi.getVer() + ")");
        } else if (schedule.getCurrentState() == ScheduleResult.CurrentState.PARSE_ERROR) {
            statusText.setText(schedule.getTodayStatus());
            statusText.setTextColor(ContextCompat.getColor(context, R.color.brand_red));
            statusText.setTypeface(null, android.graphics.Typeface.BOLD);
            populateWeeklyTable(context, table, schedule, isPostBox);
            rawTagText.setText("Raw: " + schedule.getRawTagValue().getOrigin() + " (v" + poi.getVer() + ")");
        } else {
            statusText.setText(R.string.status_unparseable);
            rawTagText.setText("Raw: " + poi.getTag(isPostBox ? "collection_times" : "opening_hours") + " (v" + poi.getVer() + ")");
        }

        // ゆうゆう窓口の表示
        if (!isPostBox) {
            String lsMail = poi.getTag("limited_service:mail");
            
            if ("yes".equals(lsMail) || limitedServiceSchedule != null) {
                lsLayout.setVisibility(View.VISIBLE);
                if (limitedServiceSchedule != null) {
                    lsStatus.setText(limitedServiceSchedule.getTodayStatus());
                    if (limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING ||
                            limitedServiceSchedule.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                        lsStatus.setTextColor(ContextCompat.getColor(context, R.color.brand_red));
                        lsStatus.setTypeface(null, android.graphics.Typeface.BOLD);
                    } else {
                        lsStatus.setTypeface(null, android.graphics.Typeface.NORMAL);
                    }
                    populateWeeklyTable(context, lsTable, limitedServiceSchedule, false);
                } else {
                    lsStatus.setText(R.string.limited_service_available);
                    lsStatus.setTypeface(null, android.graphics.Typeface.NORMAL);
                    lsTable.removeAllViews();
                }
                
                if (limitedServiceSchedule != null) {
                    String raw = rawTagText.getText().toString();
                    if (!raw.contains("LS: ")) {
                        rawTagText.setText(raw + "\nLS: " + limitedServiceSchedule.getRawTagValue().getOrigin() + " (v" + poi.getVer() + ")");
                    }
                }
            } else if ("no".equals(lsMail)) {
                lsLayout.setVisibility(View.VISIBLE);
                lsStatus.setText(R.string.none);
                lsStatus.setTypeface(null, android.graphics.Typeface.NORMAL);
                lsTable.removeAllViews();
            } else {
                // 不明
                lsLayout.setVisibility(View.GONE);
            }
        } else {
            lsLayout.setVisibility(View.GONE);
        }

        String checkDate = poi.getTag("check_date");
        if (checkDate != null) {
            checkDateText.setText(context.getString(R.string.label_check_date, checkDate));
        } else {
            checkDateText.setText(R.string.label_check_date_unknown);
        }
        checkDateText.setVisibility(View.VISIBLE);

        if (isPostBox) {
            confirmNoChangeButton.setVisibility(View.VISIBLE);
        } else {
            confirmNoChangeButton.setVisibility(View.GONE);
        }
        
        String displayAddress = JpPostalUtil.getAddressText(poi.getTags());
        if (displayAddress.isEmpty()) displayAddress = context.getString(R.string.data_none);

        if (currentLocation != null) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(),
                    poi.getLat(), poi.getLon(), results);
            float distanceMeters = results[0];

            if (distanceMeters <= 50000) { // 50km
                String distanceStr;
                if (distanceMeters < 1000) {
                    distanceStr = context.getString(R.string.distance_meters, (int) distanceMeters);
                } else {
                    distanceStr = context.getString(R.string.distance_kilometers, distanceMeters / 1000.0);
                }
                displayAddress += distanceStr;
            }
        }
        addressText.setText(displayAddress);
    }

    private static void populateWeeklyTable(Context context, TableLayout table, ScheduleResult schedule, boolean isPostBox) {
        Days today = JpPostalUtil.getDays();

        // 平日の差異チェック
        boolean weekdayDifferent = false;
        Map<Days, ? extends IDaySchedule> weeklyTable = schedule.getWeeklyTable();
        if (weeklyTable != null && !weeklyTable.isEmpty()) {
            IDaySchedule mondaySched = weeklyTable.get(Days.MONDAY);
            for (Days d : new Days[]{Days.TUESDAY, Days.WEDNESDAY, Days.THURSDAY, Days.FRIDAY}) {
                if (!isSchedulesEqual(mondaySched, weeklyTable.get(d))) {
                    weekdayDifferent = true;
                    break;
                }
            }
        }

        List<String[]> groupDays = new ArrayList<>();
        List<String> groupNames = new ArrayList<>();

        if (weekdayDifferent) {
            groupDays.add(new String[]{"Mo"}); groupNames.add("月曜");
            groupDays.add(new String[]{"Tu"}); groupNames.add("火曜");
            groupDays.add(new String[]{"We"}); groupNames.add("水曜");
            groupDays.add(new String[]{"Th"}); groupNames.add("木曜");
            groupDays.add(new String[]{"Fr"}); groupNames.add("金曜");
        } else {
            groupDays.add(new String[]{"Mo", "Tu", "We", "Th", "Fr"});
            groupNames.add(context.getString(R.string.day_weekday));
        }
        groupDays.add(new String[]{"Sa"}); groupNames.add(context.getString(R.string.day_saturday));
        groupDays.add(new String[]{"Su", "PH"}); groupNames.add(context.getString(R.string.day_holiday));
        
        table.removeAllViews();
        for (int i = 0; i < groupNames.size(); i++) {
            boolean isToday = false;
            for (String dayLabel : groupDays.get(i)) {
                if (Days.getFromLabel(dayLabel) == today) {
                    isToday = true;
                    break;
                }
            }

            TableRow row = new TableRow(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            
            TextView dayView = new TextView(context);
            dayView.setText(groupNames.get(i));
            dayView.setPadding(8, 4, 16, 4);
            if (isToday) {
                dayView.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            
            TextView timeView = new TextView(context);
            IDaySchedule daySchedule = null;
            boolean foundDay = false;
            for (String day : groupDays.get(i)) {
                Days d = Days.getFromLabel(day);
                if (schedule.getWeeklyTable().containsKey(d)) {
                    daySchedule = schedule.getWeeklyTable().get(d);
                    foundDay = true;
                    break;
                }
            }
            
            String displayTime;
            if (!foundDay || daySchedule == null) {
                displayTime = context.getString(R.string.unknown);
            } else if (daySchedule.schedule().isEmpty()) {
                if (isPostBox) {
                    if (daySchedule.status() == pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser.DayStatus.CLOSED_DAY) {
                        displayTime = context.getString(R.string.postbox_no_collection);
                    } else {
                        displayTime = context.getString(R.string.unknown);
                    }
                } else {
                    if (daySchedule.status() == pro.eng.yui.oss.osm.lib.jppostalcore.parser.OpeningHoursParser.DayStatus.CLOSED_DAY) {
                        displayTime = context.getString(R.string.postoffice_closed);
                    } else {
                        displayTime = context.getString(R.string.unknown);
                    }
                }
            } else {
                java.util.List<String> timeStrings = new java.util.ArrayList<>();
                for (Object part : daySchedule.schedule()) {
                    timeStrings.add(part.toString());
                }
                displayTime = String.join(", ", timeStrings);
            }
            timeView.setText(displayTime);
            timeView.setPadding(0, 4, 8, 4);
            timeView.setSingleLine(true);
            if (isToday) {
                timeView.setTypeface(null, android.graphics.Typeface.BOLD);
            }

            HorizontalScrollView scrollView = new HorizontalScrollView(context);
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.setFillViewport(true);
            scrollView.addView(timeView);

            TableRow.LayoutParams dayParams = new TableRow.LayoutParams(
                    TableRow.LayoutParams.WRAP_CONTENT,
                    TableRow.LayoutParams.WRAP_CONTENT
            );
            dayParams.gravity = Gravity.CENTER_VERTICAL;
            dayView.setLayoutParams(dayParams);

            TableRow.LayoutParams scrollParams = new TableRow.LayoutParams(
                    0,
                    TableRow.LayoutParams.WRAP_CONTENT
            );
            scrollParams.gravity = Gravity.CENTER_VERTICAL;
            scrollView.setLayoutParams(scrollParams);

            row.addView(dayView);
            row.addView(scrollView);
            table.addView(row);
        }
    }

    private static boolean isSchedulesEqual(IDaySchedule s1, IDaySchedule s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;
        if (s1.status() != s2.status()) return false;
        
        List<String> list1 = new ArrayList<>();
        for (Object p : s1.schedule()) list1.add(p.toString());
        List<String> list2 = new ArrayList<>();
        for (Object p : s2.schedule()) list2.add(p.toString());
        
        return list1.equals(list2);
    }
}
