package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

public class PoiDetailsDialog {

    public static void show(Context context, OsmPoi poi, ScheduleResult schedule) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        String amenity = poi.getTag("amenity");
        boolean isPostBox = "post_box".equals(amenity);
        
        builder.setTitle(isPostBox ? "郵便ポスト" : poi.getTag("name"));
        
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_poi_details, null);
        TextView statusText = view.findViewById(R.id.dialog_status);
        TextView nextEventText = view.findViewById(R.id.dialog_next_event);
        TableLayout table = view.findViewById(R.id.dialog_weekly_table);
        TextView rawTagText = view.findViewById(R.id.dialog_raw_tag);
        TextView checkDateText = view.findViewById(R.id.dialog_check_date);

        if (schedule != null) {
            statusText.setText(schedule.getTodayStatus());
            
            if (isPostBox) {
                StringBuilder msg = new StringBuilder();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.JAPAN);
                long now = System.currentTimeMillis();

                if (schedule.getNextEvent() != null) {
                    long timestamp = schedule.getNextEvent().getTimestamp();
                    String timeStr = sdf.format(new Date(timestamp));
                    String dayPrefix = timestamp > getEndOfToday() ? "明日" : "本日";
                    
                    long remainingMinutes = (timestamp - now) / 60000;
                    long h = remainingMinutes / 60;
                    long m = remainingMinutes % 60;
                    String diffStr = (h > 0 ? h + "時間" : "") + m + "分後";

                    if (schedule.getCurrentState() == ScheduleResult.CurrentState.TODAY_FINISHED) {
                        msg.append("次回 ").append(dayPrefix).append(" ").append(timeStr).append(" (").append(diffStr).append(")");
                    } else {
                        msg.append(diffStr);
                    }
                }

                if (schedule.getFollowingEvent() != null) {
                    long fTimestamp = schedule.getFollowingEvent().getTimestamp();
                    String followTime = sdf.format(new Date(fTimestamp));
                    String fPrefix = fTimestamp > getEndOfToday() ? "明日" : "本日";
                    
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
                    long remainingMinutes = (schedule.getNextEvent().getTimestamp() - System.currentTimeMillis()) / 60000;
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
                List<String> times = null;
                boolean foundDay = false;
                for (String day : groupDays[i]) {
                    if (schedule.getWeeklyTable().containsKey(day)) {
                        times = schedule.getWeeklyTable().get(day);
                        foundDay = true;
                        break;
                    }
                }
                
                String displayTime;
                if (!foundDay) {
                    displayTime = "不明";
                } else if (times == null || times.isEmpty()) {
                    displayTime = "休業/収集なし";
                } else {
                    displayTime = String.join(", ", times);
                }
                timeView.setText(displayTime);
                timeView.setPadding(8, 4, 8, 4);
                
                row.addView(dayView);
                row.addView(timeView);
                table.addView(row);
            }
            
            rawTagText.setText("Raw: " + schedule.getRawTagValue());
        } else {
            statusText.setText("解析不可");
            rawTagText.setText("Raw: " + poi.getTag(isPostBox ? "collection_times" : "opening_hours"));
        }

        String checkDate = poi.getTag("check_date");
        if (checkDate == null) {
            checkDate = poi.getTag("lastcheck");
        }
        if (checkDate != null) {
            checkDateText.setText("最終確認日: " + checkDate);
            checkDateText.setVisibility(View.VISIBLE);
        } else {
            checkDateText.setVisibility(View.GONE);
        }

        builder.setView(view);
        builder.setPositiveButton("閉じる", null);
        builder.setNeutralButton("編集", (dialog, which) -> {
            android.content.Intent intent = new android.content.Intent(context, pro.eng.yui.android.osmjppostalmap.ui.EditPoiActivity.class);
            intent.putExtra("POI_ID", poi.getId());
            intent.putExtra("POI_TYPE", poi.getType());
            intent.putExtra("POI_LAT", poi.getLat());
            intent.putExtra("POI_LON", poi.getLon());
            // タグは量が多い可能性があるが、MVPの編集対象タグに絞るか、Serializable/Parcelableが必要
            // ここでは簡易的に amenity と編集対象タグのみ渡す
            intent.putExtra("TAG_AMENITY", poi.getTag("amenity"));
            intent.putExtra("TAG_NAME", poi.getTag("name"));
            intent.putExtra("TAG_OPENING_HOURS", poi.getTag("opening_hours"));
            intent.putExtra("TAG_COLLECTION_TIMES", poi.getTag("collection_times"));
            intent.putExtra("TAG_REF", poi.getTag("ref"));
            context.startActivity(intent);
        });
        
        builder.show();
    }

    private static long getEndOfToday() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23);
        cal.set(java.util.Calendar.MINUTE, 59);
        cal.set(java.util.Calendar.SECOND, 59);
        return cal.getTimeInMillis();
    }
}
