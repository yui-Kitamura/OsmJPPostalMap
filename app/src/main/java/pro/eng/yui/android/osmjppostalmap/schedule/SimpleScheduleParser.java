package pro.eng.yui.android.osmjppostalmap.schedule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 簡易版のopening_hours / collection_times パーサー
 * MVPでは主要な形式(Mo-Fr 09:00-17:00等)をサポートする
 */
public class SimpleScheduleParser implements ScheduleParser {

    @Override
    public ScheduleResult parse(String tagValue, long currentTime) {
        if (tagValue == null || tagValue.isEmpty()) {
            return new ScheduleResult(null, null, "不明", ScheduleResult.CurrentState.CLOSED, new HashMap<>(), tagValue);
        }

        try {
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis(currentTime);
            
            // 簡易解析: "Mo-Fr 09:00-17:00; Sa 09:00-12:00" のような形式を想定
            // 本来は複雑なパーサーが必要だが、MVPでは分割して基本的なマッチングを行う
            Map<String, List<String>> weeklyTable = parseToWeeklyTable(tagValue);
            
            // 現在の曜日の時間を取得
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            String dayKey = getDayKey(dayOfWeek);
            List<String> todayTimes = weeklyTable.get(dayKey);

            ScheduleResult.CurrentState state = ScheduleResult.CurrentState.CLOSED;
            String todayStatus = "本日は営業終了または休業";
            ScheduleResult.Event nextEvent = null;

            if (todayTimes != null && !todayTimes.isEmpty()) {
                // TODO: 現在時刻とtodayTimesを比較して、営業中かどうか判定
                // MVPでは最初の時間枠のみ考慮するなどの簡略化を行う
                todayStatus = String.join(", ", todayTimes);
            }

            return new ScheduleResult(
                nextEvent, 
                null, 
                todayStatus, 
                state, 
                weeklyTable, 
                tagValue
            );
        } catch (Exception e) {
            return new ScheduleResult(null, null, "解析エラー", ScheduleResult.CurrentState.CLOSED, new HashMap<>(), tagValue);
        }
    }

    private Map<String, List<String>> parseToWeeklyTable(String tagValue) {
        Map<String, List<String>> table = new TreeMap<>();
        // 簡易分割実装
        String[] parts = tagValue.split(";");
        for (String part : parts) {
            part = part.trim();
            // TODO: "Mo-Fr 09:00-17:00" などのパース
        }
        return table;
    }

    private String getDayKey(int calendarDay) {
        switch (calendarDay) {
            case Calendar.MONDAY: return "Mo";
            case Calendar.TUESDAY: return "Tu";
            case Calendar.WEDNESDAY: return "We";
            case Calendar.THURSDAY: return "Th";
            case Calendar.FRIDAY: return "Fr";
            case Calendar.SATURDAY: return "Sa";
            case Calendar.SUNDAY: return "Su";
            default: return "";
        }
    }
}
