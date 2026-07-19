package pro.eng.yui.android.osmjppostalmap.schedule;

import java.time.*;
import java.util.*;

import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.ManagerParameters;

import static de.focus_shift.jollyday.core.HolidayCalendar.JAPAN;

/**
 * 簡易版のopening_hours / collection_times パーサー
 * MVPでは主要な形式(Mo-Fr 09:00-17:00等)をサポートする
 */
public class SimpleScheduleParser implements ScheduleParser {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static HolidayManager holidayManager;

    private static synchronized HolidayManager getHolidayManager() {
        if (holidayManager == null) {
            try {
                holidayManager = HolidayManager.getInstance(ManagerParameters.create(JAPAN));
            } catch (Exception e) {
                // ignore
            }
        }
        return holidayManager;
    }

    @Override
    public ScheduleResult parse(String tagValue, long currentTime, Amenity amenity) {
        if (tagValue == null || tagValue.isEmpty()) {
            return new ScheduleResult(null, null, "不明", ScheduleResult.CurrentState.UNKNOWN, new HashMap<>(), tagValue);
        }

        String trimmedTag = tagValue.trim();
        if (trimmedTag.equals("24/7")) {
            return new ScheduleResult(null, null, "24時間営業", ScheduleResult.CurrentState.OPENING, new HashMap<>(), tagValue);
        }

        try {
            ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTime), JST);
            
            boolean isHoliday = isJapanHoliday(now.toLocalDate());
            Map<String, List<String>> weeklyTable = parseToWeeklyTable(tagValue);
            
            if (weeklyTable.isEmpty()) {
                return new ScheduleResult(null, null, "解析不能", ScheduleResult.CurrentState.UNKNOWN, new HashMap<>(), tagValue);
            }

            List<String> todayTimes = null;
            boolean hasSpecificDayRules = false;
            for (String key : weeklyTable.keySet()) {
                if (!key.equals("ALL")) {
                    hasSpecificDayRules = true;
                    break;
                }
            }

            if (isHoliday) {
                if (weeklyTable.containsKey("PH")) {
                    todayTimes = weeklyTable.get("PH");
                } else {
                    // 祝日の情報がない場合はUNKNOWN
                    return new ScheduleResult(null, null, "祝日のため不明", ScheduleResult.CurrentState.UNKNOWN, weeklyTable, tagValue);
                }
            } else {
                DayOfWeek dayOfWeek = now.getDayOfWeek();
                String dayKey = getDayKey(dayOfWeek);
                if (weeklyTable.containsKey(dayKey)) {
                    todayTimes = weeklyTable.get(dayKey);
                } else if (hasSpecificDayRules) {
                    // 判定できる曜日以外はUNKNOWN
                    return new ScheduleResult(null, null, "定休日または不明", ScheduleResult.CurrentState.UNKNOWN, weeklyTable, tagValue);
                }
            }

            ScheduleResult.CurrentState state = ScheduleResult.CurrentState.CLOSED;
            String todayStatus = "本日は営業終了または休業";
            ScheduleResult.Event nextEvent = null;
            ScheduleResult.Event followingEvent = null;

            if (todayTimes != null && !todayTimes.isEmpty()) {
                long currentMinutes = now.getHour() * 60 + now.getMinute();
                
                for (String timeRange : todayTimes) {
                    if (amenity == Amenity.POST_OFFICE || timeRange.contains("-")) {
                        // 営業時間形式: 09:00-17:00
                        String[] range = timeRange.split("-");
                        int start = parseMinutes(range[0]);
                        int end = parseMinutes(range[1]);
                        
                        if (currentMinutes < start) {
                            if (nextEvent == null) {
                                nextEvent = createEvent(now, start, ScheduleResult.EventType.OPEN);
                                state = ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON; 
                                todayStatus = "営業開始前 " + range[0];
                            }
                        } else if (currentMinutes < end) {
                            state = (end - currentMinutes <= 60) ? 
                                    ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON : ScheduleResult.CurrentState.OPENING;
                            nextEvent = createEvent(now, end, ScheduleResult.EventType.CLOSE);
                            todayStatus = "営業中 (" + range[1] + "まで)";
                            break; // 営業中なら確定
                        }
                    } else {
                        // 収集時刻形式: 09:00
                        int collectionTime = parseMinutes(timeRange);
                        if (currentMinutes < collectionTime) {
                            if (nextEvent == null || nextEvent.getType() != ScheduleResult.EventType.COLLECTION) {
                                if (nextEvent == null) {
                                    nextEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                                    if (collectionTime - currentMinutes <= 60) {
                                        state = ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON;
                                    } else {
                                        state = ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON;
                                    }
                                    todayStatus = "次回収集 " + timeRange;
                                } else if (followingEvent == null) {
                                    followingEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                                }
                            }
                        } else {
                            // 収集時刻を過ぎている場合、翌日の候補としてキープするための処理は122行目以降で行うが、
                            // もし今日の中にまだ他にも収集時刻があれば、上の if (currentMinutes < collectionTime) で拾われる
                        }
                    }
                }
                
                        if (nextEvent == null && state == ScheduleResult.CurrentState.CLOSED) {
                    if (amenity == Amenity.POST_OFFICE) {
                         state = ScheduleResult.CurrentState.TODAY_FINISHED;
                         todayStatus = "本日の営業は終了しました";
                    } else {
                        state = ScheduleResult.CurrentState.TODAY_FINISHED;
                        todayStatus = "本日の収集は終了しました";
                    }
                }
            }
            
            // 明日以降のイベント検索（今日終わっている場合）
            if (nextEvent == null) {
                for (int i = 1; i <= 7; i++) {
                    ZonedDateTime nextDay = now.plusDays(i);
                    boolean nextDayIsHoliday = isJapanHoliday(nextDay.toLocalDate());
                    List<String> nextDayTimes = null;
                    
                    if (nextDayIsHoliday) {
                        if (weeklyTable.containsKey("PH")) {
                            nextDayTimes = weeklyTable.get("PH");
                        }
                    } else {
                        String nextDayKey = getDayKey(nextDay.getDayOfWeek());
                        nextDayTimes = weeklyTable.get(nextDayKey);
                    }

                    if (nextDayTimes != null && !nextDayTimes.isEmpty()) {
                        // 最初のイベントを取得
                        String firstTime = nextDayTimes.get(0);
                        String firstStartTime = firstTime.contains("-") ? firstTime.split("-")[0] : firstTime;
                        int minutes = parseMinutes(firstStartTime);
                        nextEvent = createEvent(nextDay, minutes, amenity == Amenity.POST_BOX ? 
                                ScheduleResult.EventType.COLLECTION : ScheduleResult.EventType.OPEN);
                        
                        // 2つ目のイベントがあれば取得
                        if (nextDayTimes.size() > 1) {
                            String secondTime = nextDayTimes.get(1);
                            String secondStartTime = secondTime.contains("-") ? secondTime.split("-")[0] : secondTime;
                            int m2 = parseMinutes(secondStartTime);
                            followingEvent = createEvent(nextDay, m2, amenity == Amenity.POST_BOX ? 
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

    private ScheduleResult.Event createEvent(ZonedDateTime baseDate, int totalMinutes, ScheduleResult.EventType type) {
        ZonedDateTime eventTime = baseDate.withHour(totalMinutes / 60)
                .withMinute(totalMinutes % 60)
                .withSecond(0)
                .withNano(0);
        return new ScheduleResult.Event(eventTime.toInstant().toEpochMilli(), type);
    }

    private boolean isJapanHoliday(LocalDate date) {
        HolidayManager manager = getHolidayManager();
        if (manager != null) {
            try {
                return manager.isHoliday(date);
            } catch (Exception ignore) {
            }
        }
        // Fallback: Check if it's Sunday (many holiday schedules match Sunday)
        // However, OSM PH specifically means public holiday. 
        // If we can't detect holiday, we might be better off returning false and 
        // using day-of-week than guessing.
        return false;
    }

    private Map<String, List<String>> parseToWeeklyTable(String tagValue) {
        Map<String, List<String>> table = new TreeMap<>();
        String[] parts = tagValue.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // 例: "Mo-Fr 09:00-17:00" または "Mo,Tu,We 09:00" または "PH off" または "10:00-19:00"
            String[] dayAndTime = part.split(" ", 2);
            
            List<String> days;
            String timePart;
            
            if (dayAndTime.length < 2) {
                // 曜日なしのケース: "10:00-19:00"
                if (dayAndTime[0].contains(":") || dayAndTime[0].equalsIgnoreCase("off")) {
                    days = Arrays.asList("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");
                    timePart = dayAndTime[0];
                } else {
                    continue;
                }
            } else {
                if (dayAndTime[0].equalsIgnoreCase("ALL")) {
                    // ALL構文は受け付けない
                    continue;
                }
                days = expandDays(dayAndTime[0]);
                timePart = dayAndTime[1].trim();
            }
            
            if (timePart.equalsIgnoreCase("off")) {
                for (String day : days) {
                    table.put(day, new ArrayList<>()); // 空リスト = 休み
                }
                continue;
            }

            String[] times = timePart.split(",");
            
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
        
        // まずカンマで分割
        String[] parts = dayRange.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.equals("PH")) {
                result.add("PH");
            } else if (part.contains("-")) {
                // ハイフン指定の展開
                String[] startEnd = part.split("-");
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
            } else {
                // 単一の曜日指定
                result.add(part);
            }
        }
        return result;
    }

    private String getDayKey(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY: return "Mo";
            case TUESDAY: return "Tu";
            case WEDNESDAY: return "We";
            case THURSDAY: return "Th";
            case FRIDAY: return "Fr";
            case SATURDAY: return "Sa";
            case SUNDAY: return "Su";
            default: return "";
        }
    }
}
