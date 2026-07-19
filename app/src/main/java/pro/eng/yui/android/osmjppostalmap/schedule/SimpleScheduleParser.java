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
            
            Map<String, List<String>> weeklyTable = parseToWeeklyTable(tagValue);
            
            int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
            String dayKey = getDayKey(dayOfWeek);
            List<String> todayTimes = weeklyTable.get(dayKey);

            ScheduleResult.CurrentState state = ScheduleResult.CurrentState.CLOSED;
            String todayStatus = "本日は営業終了または休業";
            ScheduleResult.Event nextEvent = null;
            ScheduleResult.Event followingEvent = null;

            if (todayTimes != null && !todayTimes.isEmpty()) {
                long currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
                
                for (String timeRange : todayTimes) {
                    if (timeRange.contains("-")) {
                        // 営業時間形式: 09:00-17:00
                        String[] range = timeRange.split("-");
                        int start = parseMinutes(range[0]);
                        int end = parseMinutes(range[1]);
                        
                        if (currentMinutes < start) {
                            if (nextEvent == null) {
                                nextEvent = createEvent(now, start, ScheduleResult.EventType.OPEN);
                                state = ScheduleResult.CurrentState.OPEN_SOON_FUTURE; // 営業開始前（本日）
                                todayStatus = "営業開始前 " + range[0];
                            }
                        } else if (currentMinutes < end) {
                            state = (end - currentMinutes <= 60) ? 
                                    ScheduleResult.CurrentState.OPEN_SOON : ScheduleResult.CurrentState.OPENING;
                            nextEvent = createEvent(now, end, ScheduleResult.EventType.CLOSE);
                            todayStatus = "営業中 (" + range[1] + "まで)";
                            // 次の枠を探す（もしあれば）
                        }
                    } else {
                        // 収集時刻形式: 09:00
                        int collectionTime = parseMinutes(timeRange);
                        if (currentMinutes < collectionTime) {
                            if (nextEvent == null) {
                                nextEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                                state = (collectionTime - currentMinutes <= 60) ? 
                                        ScheduleResult.CurrentState.OPEN_SOON : ScheduleResult.CurrentState.OPENING;
                                todayStatus = "次回収集 " + timeRange;
                            } else if (followingEvent == null) {
                                followingEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                            }
                        }
                    }
                }
                
                if (nextEvent == null && !isCollectionOnly(todayTimes)) {
                     state = ScheduleResult.CurrentState.TODAY_FINISHED;
                     todayStatus = "本日の営業は終了しました";
                } else if (nextEvent == null) {
                    state = ScheduleResult.CurrentState.TODAY_FINISHED;
                    todayStatus = "本日の収集は終了しました";
                }
            }
            
            // 明日以降のイベント検索（今日終わっている場合）
            if (nextEvent == null) {
                for (int i = 1; i <= 7; i++) {
                    int nextDayOfWeek = ((dayOfWeek + i - 1) % 7) + 1;
                    String nextDayKey = getDayKey(nextDayOfWeek);
                    List<String> nextDayTimes = weeklyTable.get(nextDayKey);
                    if (nextDayTimes != null && !nextDayTimes.isEmpty()) {
                        Calendar nextDay = (Calendar) now.clone();
                        nextDay.add(Calendar.DAY_OF_YEAR, i);
                        String firstTime = nextDayTimes.get(0);
                        int minutes = parseMinutes(firstTime.split("-")[0]);
                        nextEvent = createEvent(nextDay, minutes, isCollectionOnly(nextDayTimes) ? 
                                ScheduleResult.EventType.COLLECTION : ScheduleResult.EventType.OPEN);
                        if (nextDayTimes.size() > 1) {
                            String secondTime = nextDayTimes.get(1);
                            int m2 = parseMinutes(secondTime.split("-")[0]);
                            followingEvent = createEvent(nextDay, m2, isCollectionOnly(nextDayTimes) ? 
                                    ScheduleResult.EventType.COLLECTION : ScheduleResult.EventType.OPEN);
                        }
                        break;
                    }
                }
            }

            return new ScheduleResult(nextEvent, followingEvent, todayStatus, state, weeklyTable, tagValue);
        } catch (Exception e) {
            return new ScheduleResult(null, null, "解析エラー", ScheduleResult.CurrentState.CLOSED, new HashMap<>(), tagValue);
        }
    }

    private boolean isCollectionOnly(List<String> times) {
        if (times.isEmpty()) return false;
        return !times.get(0).contains("-");
    }

    private int parseMinutes(String time) {
        String[] parts = time.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private ScheduleResult.Event createEvent(Calendar baseDate, int totalMinutes, ScheduleResult.EventType type) {
        Calendar cal = (Calendar) baseDate.clone();
        cal.set(Calendar.HOUR_OF_DAY, totalMinutes / 60);
        cal.set(Calendar.MINUTE, totalMinutes % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return new ScheduleResult.Event(cal.getTimeInMillis(), type);
    }

    private Map<String, List<String>> parseToWeeklyTable(String tagValue) {
        Map<String, List<String>> table = new TreeMap<>();
        String[] parts = tagValue.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // 例: "Mo-Fr 09:00-17:00" または "Mo,Tu,We 09:00"
            String[] dayAndTime = part.split(" ", 2);
            if (dayAndTime.length < 2) continue;
            
            List<String> days = expandDays(dayAndTime[0]);
            String[] times = dayAndTime[1].split(",");
            
            for (String day : days) {
                List<String> dayTimes = table.get(day);
                if (dayTimes == null) {
                    dayTimes = new ArrayList<>();
                    table.put(day, dayTimes);
                }
                for (String t : times) {
                    dayTimes.add(t.trim());
                }
            }
        }
        return table;
    }

    private List<String> expandDays(String dayRange) {
        List<String> result = new ArrayList<>();
        String[] allDays = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        
        if (dayRange.contains("-")) {
            String[] startEnd = dayRange.split("-");
            int startIdx = -1, endIdx = -1;
            for (int i = 0; i < allDays.length; i++) {
                if (allDays[i].equals(startEnd[0])) startIdx = i;
                if (allDays[i].equals(startEnd[1])) endIdx = i;
            }
            if (startIdx != -1 && endIdx != -1) {
                for (int i = startIdx; i <= endIdx; i++) {
                    result.add(allDays[i]);
                }
            }
        } else if (dayRange.contains(",")) {
            for (String d : dayRange.split(",")) {
                result.add(d.trim());
            }
        } else {
            result.add(dayRange);
        }
        return result;
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
