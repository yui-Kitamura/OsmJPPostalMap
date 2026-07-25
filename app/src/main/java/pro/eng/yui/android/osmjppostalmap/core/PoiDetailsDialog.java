package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.ui.MainActivity;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.Days;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.IDaySchedule;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

public class PoiDetailsDialog {

    public static void show(Context context, OsmPoi poi, ScheduleResult schedule) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        ScheduleParser.Amenity amenity = 
                "post_office".equals(poi.getTag("amenity")) ? 
                ScheduleParser.Amenity.POST_OFFICE : 
                ScheduleParser.Amenity.POST_BOX;
        boolean isPostBox = (amenity == ScheduleParser.Amenity.POST_BOX);
        
        View titleView = LayoutInflater.from(context).inflate(R.layout.dialog_poi_details_title, null);
        TextView titleText = titleView.findViewById(R.id.dialog_title_text);
        titleText.setText(isPostBox ? "郵便ポスト" : poi.getTag("name"));
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
        TextView statusText = view.findViewById(R.id.dialog_status);
        TextView nextEventText = view.findViewById(R.id.dialog_next_event);
        TableLayout table = view.findViewById(R.id.dialog_weekly_table);
        TextView rawTagText = view.findViewById(R.id.dialog_raw_tag);
        TextView checkDateText = view.findViewById(R.id.dialog_check_date);
        TextView addressText = view.findViewById(R.id.dialog_address);

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
                        dayPrefix = "本日";
                    } else if (daysDiff == 1) {
                        dayPrefix = "明日";
                    } else {
                        dayPrefix = eventDate.format(DateTimeFormatter.ofPattern("dd日"));
                    }

                    long remainingMinutes = (timestamp - now) / 60000;
                    long h = remainingMinutes / 60;
                    long m = remainingMinutes % 60;
                    String diffStr = (h > 0 ? h + "時間" : "") + m + "分後";

                    if (schedule.getCurrentState() == ScheduleResult.CurrentState.TODAY_FINISHED ||
                        schedule.getNextEvent().getTimestamp().toLocalDate().isAfter(java.time.LocalDate.now())) {
                        msg.append("次回 ").append(dayPrefix).append(" ").append(timeStr).append(" (").append(diffStr).append(")");
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
                        fPrefix = "本日";
                    } else if (fDaysDiff == 1) {
                        fPrefix = "明日";
                    } else {
                        fPrefix = fEventDate.format(DateTimeFormatter.ofPattern("dd日"));
                    }

                    long fRemainingMinutes = (fTimestamp - now) / 60000;
                    long fh = fRemainingMinutes / 60;
                    long fm = fRemainingMinutes % 60;
                    String fDiffStr = (fh > 0 ? fh + "時間" : "") + fm + "分後";
                    
                    if (msg.length() > 0) msg.append("\n");
                    msg.append("逃した場合 ").append(fPrefix).append(" ").append(followTime).append(" (").append(fDiffStr).append(")");
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
                    String diffStr = (h > 0 ? h + "時間" : "") + m + "分後";
                    nextEventText.setText(diffStr);
                    nextEventText.setVisibility(View.VISIBLE);
                } else {
                    nextEventText.setVisibility(View.GONE);
                }
            }

            // スケジュール表の作成 (平日/土曜/日祝の形式)
            String[][] groupDays = {
                {"Mo", "Tu", "We", "Th", "Fr"},
                {"Sa"},
                {"Su", "PH"}
            };
            String[] groupNames = {"平日", "土曜", "日祝"};
            
            for (int i = 0; i < groupNames.length; i++) {
                TableRow row = new TableRow(context);
                TextView dayView = new TextView(context);
                dayView.setText(groupNames[i]);
                dayView.setPadding(8, 4, 16, 4);
                
                TextView timeView = new TextView(context);
                // そのグループの時間を取得（代表する曜日またはPHから）
                IDaySchedule daySchedule = null;
                boolean foundDay = false;
                for (String day : groupDays[i]) {
                    Days d = Days.getFromLabel(day);
                    if (schedule.getWeeklyTable().containsKey(d)) {
                        daySchedule = schedule.getWeeklyTable().get(d);
                        foundDay = true;
                        break;
                    }
                }
                
                String displayTime;
                if (!foundDay || daySchedule == null) {
                    displayTime = "不明";
                } else if (daySchedule.schedule().isEmpty()) {
                    if (isPostBox) {
                        displayTime = "収集なし";
                    } else {
                        displayTime = "休業";
                    }
                } else {
                    java.util.List<String> timeStrings = new java.util.ArrayList<>();
                    for (Object part : daySchedule.schedule()) {
                        timeStrings.add(part.toString());
                    }
                    displayTime = String.join(", ", timeStrings);
                }
                timeView.setText(displayTime);
                timeView.setPadding(8, 4, 8, 4);
                
                row.addView(dayView);
                row.addView(timeView);
                table.addView(row);
            }
            
            rawTagText.setText("Raw: " + schedule.getRawTagValue().getOrigin());
        } else {
            statusText.setText("解析不可");
            rawTagText.setText("Raw: " + poi.getTag(isPostBox ? "collection_times" : "opening_hours"));
        }

        String checkDate = poi.getTag("check_date");
        if (checkDate != null) {
            checkDateText.setText("最終確認日: " + checkDate);
        } else {
            checkDateText.setText("最終確認日: 不明");
        }
        checkDateText.setVisibility(View.VISIBLE);
        
        String displayAddress = JpPostalUtil.getAddressText(poi.getTags());
        if (displayAddress == null){ displayAddress = "データなし"; }
        addressText.setText(displayAddress);

        builder.setView(view);
        builder.setPositiveButton("閉じる", null);
        builder.setNeutralButton("編集", (dialog, which) -> {
            android.content.Intent intent = new android.content.Intent(context, pro.eng.yui.android.osmjppostalmap.ui.EditPoiActivity.class);
            intent.putExtra("POI_ID", poi.getId());
            intent.putExtra("POI_TYPE", poi.getType());
            intent.putExtra("POI_LAT", poi.getLat());
            intent.putExtra("POI_LON", poi.getLon());
            intent.putExtra("POI_VER", poi.getVer());
            
            // すべてのタグを渡す
            if (poi.getTags() instanceof Serializable) {
                intent.putExtra("POI_TAGS", (Serializable) poi.getTags());
            } else {
                // Serializable でない場合は HashMap にコピーして渡す
                intent.putExtra("POI_TAGS", new HashMap<>(poi.getTags()));
            }

            if (context instanceof MainActivity) {
                MainActivity activity = (MainActivity) context;
                org.osmdroid.views.MapView map = activity.findViewById(R.id.map);
                if (map != null) {
                    intent.putExtra("ZOOM_LEVEL", map.getZoomLevelDouble());
                }
            }
            context.startActivity(intent);
        });
        
        builder.show();
    }

}
