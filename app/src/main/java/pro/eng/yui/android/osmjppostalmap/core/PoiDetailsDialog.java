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

        if (schedule != null) {
            statusText.setText(schedule.getTodayStatus());
            
            if (schedule.getNextEvent() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.JAPAN);
                String timeStr = sdf.format(new Date(schedule.getNextEvent().getTimestamp()));
                
                long remainingMillis = schedule.getNextEvent().getTimestamp() - System.currentTimeMillis();
                long remainingMinutes = remainingMillis / 60000;
                String diffStr = (remainingMinutes / 60) + ":" + String.format(Locale.US, "%02d", (remainingMinutes % 60)) + "後";

                if (isPostBox) {
                    long now = System.currentTimeMillis();
                    String prefix = "次回";
                    if (schedule.getNextEvent().getTimestamp() > getEndOfToday()) {
                        prefix = "明日";
                    }
                    String msg = prefix + " " + timeStr + " (" + diffStr + ")";
                    
                    if (schedule.getFollowingEvent() != null) {
                        String followTime = sdf.format(new Date(schedule.getFollowingEvent().getTimestamp()));
                        String fPrefix = schedule.getFollowingEvent().getTimestamp() > getEndOfToday() ? "明日" : "本日";
                        msg += "\n逃した場合 " + fPrefix + " " + followTime;
                    }
                    nextEventText.setText(msg);
                } else {
                    String prefix = "次回イベント: ";
                    if (schedule.getNextEvent().getTimestamp() > getEndOfToday()) {
                        prefix = "明日の営業開始: ";
                    }
                    nextEventText.setText(prefix + timeStr);
                }
            }

            // スケジュール表の作成 (平日/土曜/日祝の形式)
            String[][] groupDays = {
                {"Mo", "Tu", "We", "Th", "Fr"},
                {"Sa"},
                {"Su"}
            };
            String[] groupNames = {"平日", "土曜", "日祝"};
            
            for (int i = 0; i < groupNames.length; i++) {
                TableRow row = new TableRow(context);
                TextView dayView = new TextView(context);
                dayView.setText(groupNames[i]);
                dayView.setPadding(8, 4, 16, 4);
                
                TextView timeView = new TextView(context);
                // そのグループの最初の曜日の時間を代表として表示（要件に基づき簡略化、本来は差分チェックが必要）
                List<String> times = schedule.getWeeklyTable().get(groupDays[i][0]);
                timeView.setText(times != null && !times.isEmpty() ? String.join(", ", times) : "休業/収集なし");
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

        builder.setView(view);
        builder.setPositiveButton("閉じる", null);
        builder.setNeutralButton("編集", (dialog, which) -> {
            android.content.Intent intent = new android.content.Intent(context, pro.eng.yui.android.osmjppostalmap.ui.EditPoiActivity.class);
            // 実際にはIDやタグを渡す
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
