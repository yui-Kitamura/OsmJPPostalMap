package pro.eng.yui.android.osmjppostalmap.schedule;

import java.time.*;
import java.util.*;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;

/**
 * 簡易版のopening_hours / collection_times パーサー
 * MVPでは主要な形式(Mo-Fr 09:00-17:00等)をサポートする
 */
public class SimpleScheduleParser implements ScheduleParser {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    @Override
    public ScheduleResult parse(String tagValue, long currentTime, Amenity amenity) {
        if (tagValue == null || tagValue.isEmpty()) {
            return new ScheduleResult(null, null, "不明", ScheduleResult.CurrentState.UNKNOWN, new HashMap<>(), tagValue, false);
        }

        String trimmedTag = tagValue.trim();
        if (trimmedTag.equals("24/7")) {
            boolean isHoliday = JpPostalUtil.isHoliday();
            return new ScheduleResult(null, null, "24時間営業", ScheduleResult.CurrentState.OPENING, new HashMap<>(), tagValue, isHoliday);
        }

        try {
            ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTime), JST);
            
            boolean isHoliday = JpPostalUtil.isHoliday(LocalDate.from(now));
            Map<String, List<String>> weeklyTable = parseToWeeklyTable(tagValue);
            
            if (weeklyTable.isEmpty()) {
                return new ScheduleResult(null, null, "解析不能", ScheduleResult.CurrentState.UNKNOWN, new HashMap<>(), tagValue, isHoliday);
            }

            List<String> todayTimes = null;
            boolean hasSpecificDayRules = false;
            for (String key : weeklyTable.keySet()) {
                if (!key.equals("ALL")) {
                    hasSpecificDayRules = true;
                    break;
                }
            }

            // 深夜判定（前日の延長がないか確認）
            ZonedDateTime yesterday = now.minusDays(1);
            boolean yesterdayWasHoliday = JpPostalUtil.isHoliday(yesterday.toLocalDate());
            List<String> yesterdayTimes = null;
            if (yesterdayWasHoliday && weeklyTable.containsKey("PH")) {
                yesterdayTimes = weeklyTable.get("PH");
            } else {
                yesterdayTimes = weeklyTable.get(getDayKey(yesterday.getDayOfWeek()));
            }

            ScheduleResult.CurrentState state = ScheduleResult.CurrentState.CLOSED;
            String todayStatus = "本日は営業終了または休業";
            ScheduleResult.Event nextEvent = null;
            ScheduleResult.Event followingEvent = null;

            long currentMinutes = now.getHour() * 60 + now.getMinute();

            // 前日のスケジュールからの継続判定
            if (yesterdayTimes != null) {
                for (String timeRange : yesterdayTimes) {
                    if (timeRange.contains("-")) {
                        String[] range = timeRange.split("-");
                        int end = parseMinutes(range[1]);
                        if (end > 1440) { // 24:00 を超えている
                            int endOffset = end - 1440;
                            if (currentMinutes < endOffset) {
                                // 前日から継続して営業中
                                state = (endOffset - currentMinutes <= 60) ? 
                                        ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON : ScheduleResult.CurrentState.OPENING;
                                nextEvent = createEvent(now, endOffset, ScheduleResult.EventType.CLOSE);
                                todayStatus = (amenity == Amenity.POST_OFFICE ? "営業中" : "収集中") + 
                                        " (" + String.format(Locale.getDefault(), "%02d:%02d", endOffset / 60, endOffset % 60) + "まで)";
                                break;
                            }
                        }
                    } else {
                        // 収集時刻形式: 前日の 24:00 以降 (24:30など) が今日の 00:30 として扱われるべきか
                        int collectionTime = parseMinutes(timeRange);
                        if (collectionTime > 1440) {
                            int offset = collectionTime - 1440;
                            if (currentMinutes < offset) {
                                // 前日の 24:30 収集予定などは、今日の 00:30 収集として扱う
                                if (nextEvent == null) {
                                    nextEvent = createEvent(now, offset, ScheduleResult.EventType.COLLECTION);
                                    if (offset - currentMinutes <= 60) {
                                        state = ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON;
                                    } else {
                                        state = ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON;
                                    }
                                    todayStatus = "次回 " + String.format(Locale.getDefault(), "%02d:%02d", offset / 60, offset % 60);
                                }
                            }
                        }
                    }
                }
            }

            if (state == ScheduleResult.CurrentState.CLOSED) {
                if (isHoliday) {
                    if (weeklyTable.containsKey("PH")) {
                        todayTimes = weeklyTable.get("PH");
                    } else {
                        // 祝日の情報がない場合はUNKNOWN
                        return new ScheduleResult(null, null, "祝日のため不明", ScheduleResult.CurrentState.UNKNOWN, weeklyTable, tagValue, isHoliday);
                    }
                } else {
                    DayOfWeek dayOfWeek = now.getDayOfWeek();
                    String dayKey = getDayKey(dayOfWeek);
                    if (weeklyTable.containsKey(dayKey)) {
                        todayTimes = weeklyTable.get(dayKey);
                    } else if (hasSpecificDayRules) {
                        // 判定できる曜日以外はUNKNOWN
                        return new ScheduleResult(null, null, "定休日または不明", ScheduleResult.CurrentState.UNKNOWN, weeklyTable, tagValue, isHoliday);
                    }
                }

                if (todayTimes != null && !todayTimes.isEmpty()) {
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
                                todayStatus = (amenity == Amenity.POST_OFFICE ? "営業中" : "収集中") + 
                                        " (" + (end >= 1440 ? String.format(Locale.getDefault(), "%02d:%02d", (end - 1440) / 60, (end - 1440) % 60) : range[1]) + "まで)";
                                break; // 営業中なら確定
                            }
                        } else {
                            // 収集時刻形式: 09:00
                            int collectionTime = parseMinutes(timeRange);
                            
                            if (currentMinutes < collectionTime) {
                                if (nextEvent == null) {
                                    nextEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                                    // 10:00ちょうどは「手遅れ」なので、ここには入らない
                                    if (collectionTime - currentMinutes <= 60) {
                                        state = ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON;
                                    } else {
                                        state = ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON;
                                    }
                                    todayStatus = "次回 " + (collectionTime >= 1440 ? String.format(Locale.getDefault(), "%02d:%02d", (collectionTime - 1440) / 60, (collectionTime - 1440) % 60) : timeRange);
                                } else if (followingEvent == null) {
                                    followingEvent = createEvent(now, collectionTime, ScheduleResult.EventType.COLLECTION);
                                }
                            }
                        }
                    }
                    
                    if (nextEvent == null && state == ScheduleResult.CurrentState.CLOSED) {
                        state = ScheduleResult.CurrentState.TODAY_FINISHED;
                        todayStatus = (amenity == Amenity.POST_OFFICE ? "本日の営業は終了しました" : "本日の収集は終了しました");
                    }
                }
            }
            
            // 明日以降のイベント検索（今日終わっている場合）
            if (nextEvent == null) {
                for (int i = 1; i <= 7; i++) {
                    ZonedDateTime nextDay = now.plusDays(i);
                    boolean nextDayIsHoliday = JpPostalUtil.isHoliday(nextDay.toLocalDate());
                    List<String> nextDayTimes = null;
                    
                    if (nextDayIsHoliday) {
                        if (weeklyTable.containsKey("PH")) {
                            nextDayTimes = weeklyTable.get("PH");
                        } else {
                            // 祝日なのにPH指定がない場合は、そこで探索を打ち切る（今日の判定と整合性を取る）
                            break;
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
                        // 24:00 を超える分は、その日の深夜分として既に処理されているはずなのでスキップ
                        if (minutes < 1440) {
                            nextEvent = createEvent(nextDay, minutes, amenity == Amenity.POST_BOX ? 
                                    ScheduleResult.EventType.COLLECTION : ScheduleResult.EventType.OPEN);
                            
                            // 2つ目のイベントがあれば取得
                            if (nextDayTimes.size() > 1) {
                                String secondTime = nextDayTimes.get(1);
                                String secondStartTime = secondTime.contains("-") ? secondTime.split("-")[0] : secondTime;
                                int m2 = parseMinutes(secondStartTime);
                                if (m2 < 1440) {
                                    followingEvent = createEvent(nextDay, m2, amenity == Amenity.POST_BOX ? 
                                            ScheduleResult.EventType.COLLECTION : ScheduleResult.EventType.OPEN);
                                }
                            }
                            break;
                        }
                    }
                }
            }

            return new ScheduleResult(nextEvent, followingEvent, todayStatus, state, weeklyTable, tagValue, isHoliday);
        } catch (Exception e) {
            return new ScheduleResult(null, null, "エラー", ScheduleResult.CurrentState.UNKNOWN, new HashMap<>(), tagValue, false);
        }
    }

    private boolean isCollectionOnly(List<String> times) {
        if (times.isEmpty()) return false;
        return !times.get(0).contains("-");
    }

    public static int parseMinutes(String time) {
        String[] parts = time.trim().split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private ScheduleResult.Event createEvent(ZonedDateTime baseDate, int totalMinutes, ScheduleResult.EventType type) {
        ZonedDateTime eventTime = baseDate.withHour(0).withMinute(0).withSecond(0).withNano(0)
                .plusMinutes(totalMinutes);
        return new ScheduleResult.Event(eventTime.toInstant().toEpochMilli(), type);
    }

    private Map<String, List<String>> parseToWeeklyTable(String tagValue) {
        Map<String, List<String>> table = new TreeMap<>();
        String[] parts = tagValue.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            // 例: "Mo-Fr 09:00-17:00" または "Mo, Tu, We 09:00" または "PH off" または "10:00-19:00"
            // 曜日パートと時間パートの境界を見つける
            // 時間パートは数字で始まるか、"off" であることが多い
            
            String dayPart;
            String timePart;
            List<String> days;
            
            // 正規表現で時間（HH:mm）または "off" の開始位置を探す
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}:\\d{2}|off)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(part);
            
            if (matcher.find()) {
                int splitIdx = matcher.start();
                dayPart = part.substring(0, splitIdx).trim();
                timePart = part.substring(splitIdx).trim();
                
                if (dayPart.isEmpty()) {
                    // 曜日なしのケース: "10:00-19:00" または "off"
                    days = Arrays.asList("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su");
                } else {
                    if (dayPart.equalsIgnoreCase("ALL")) {
                        continue;
                    }
                    days = expandDays(dayPart);
                }
            } else {
                // 時間が見つからない場合はスキップ（不正な形式）
                continue;
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

    @Override
    public String format(Map<String, List<String>> weeklyTable, Amenity amenity) {
        if (amenity == Amenity.POST_BOX) {
            return formatCollectionTimes(weeklyTable);
        } else {
            return formatOpeningHours(weeklyTable);
        }
    }

    private String formatCollectionTimes(Map<String, List<String>> weeklyTable) {
        List<String> weekday = weeklyTable.getOrDefault("Mo", new ArrayList<>());
        List<String> saturday = weeklyTable.getOrDefault("Sa", new ArrayList<>());
        List<String> sunday = weeklyTable.getOrDefault("Su", new ArrayList<>());
        List<String> holiday = weeklyTable.getOrDefault("PH", new ArrayList<>());

        if (weekday.isEmpty() && saturday.isEmpty() && sunday.isEmpty() && holiday.isEmpty()) {
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
        if (!sunday.isEmpty() || !holiday.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("Su,PH ");
            List<String> combined = new ArrayList<>(sunday);
            for (String h : holiday) {
                if (!combined.contains(h)) combined.add(h);
            }
            Collections.sort(combined);
            sb.append(String.join(",", combined));
        }
        return sb.toString();
    }

    private String formatOpeningHours(Map<String, List<String>> weeklyTable) {
        StringBuilder sb = new StringBuilder();
        String[] dayKeys = {"Mo-Fr", "Sa", "Su,PH"};
        String[][] dayGroups = {
            {"Mo", "Tu", "We", "Th", "Fr"},
            {"Sa"},
            {"Su", "PH"}
        };

        for (int i = 0; i < dayKeys.length; i++) {
            String key = dayKeys[i];
            List<String> times = weeklyTable.getOrDefault(dayGroups[i][0], new ArrayList<>());
            
            if (sb.length() > 0) sb.append("; ");
            sb.append(key).append(" ");
            
            if (times.isEmpty()) {
                sb.append("off");
            } else {
                sb.append(String.join(", ", times));
            }
        }
        return sb.toString();
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
                if (startEnd.length == 2) {
                    String startDay = startEnd[0].trim();
                    String endDay = startEnd[1].trim();
                    int startIdx = -1, endIdx = -1;
                    for (int i = 0; i < allDays.length; i++) {
                        if (allDays[i].equals(startDay)) startIdx = i;
                        if (allDays[i].equals(endDay)) endIdx = i;
                    }
                    if (startIdx != -1 && endIdx != -1) {
                        if (startIdx <= endIdx) {
                            for (int i = startIdx; i <= endIdx; i++) {
                                result.add(allDays[i]);
                            }
                        } else {
                            // We-Mo のように週を跨ぐ場合
                            for (int i = startIdx; i < allDays.length; i++) {
                                result.add(allDays[i]);
                            }
                            for (int i = 0; i <= endIdx; i++) {
                                result.add(allDays[i]);
                            }
                        }
                    } else {
                        // 曜日として認識できない場合はそのまま追加（フォールバック）
                        result.add(part);
                    }
                } else {
                    result.add(part);
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
