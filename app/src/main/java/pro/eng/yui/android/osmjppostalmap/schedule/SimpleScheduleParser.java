package pro.eng.yui.android.osmjppostalmap.schedule;

import java.time.*;
import java.util.*;

import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.parser.OpeningHoursParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.*;

public class SimpleScheduleParser implements ScheduleParser {

    @Override
    public ScheduleResult parse(TextValue tagValue, long currentTime, TimeType timeType) {
        if (tagValue == null || tagValue.getOrigin() == null || tagValue.getOrigin().isEmpty()) {
            return new ScheduleResult(null, null, "不明",
                    ScheduleResult.CurrentState.UNKNOWN,
                    new HashMap<>(), tagValue);
        }

        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTime), JpPostalUtil.JST);

        switch (timeType) {
            case OPENING_HOURS: {
                Map<Days, OpeningHoursParser.DaySchedule> parsedMap = JpPostalUtil.decodeOpeningHours((OpeningHours) tagValue);
                if (parsedMap == null) {
                    return new ScheduleResult(null, null, "データ解釈エラー",
                            ScheduleResult.CurrentState.UNKNOWN,
                            new HashMap<>(), tagValue);
                }
                Days today = JpPostalUtil.getDays(now.toLocalDate());
                OpeningHoursParser.DaySchedule todaySchedule = parsedMap.get(today);
                int nowOnMinutes = now.getHour() * 60 + now.getMinute();

                if (todaySchedule.schedule().isEmpty()) {
                    //前日からの延長判定を要する
                    TimeStr closedAt = overNightOperation(parsedMap, now);
                    if (closedAt != null) {
                        int closedAtTodays = closedAt.totalMinute - 24 * 60;
                        if (nowOnMinutes < closedAtTodays) {
                            //延長営業時間内
                            ZonedDateTime closeAt = ZonedDateTime.of(
                                    LocalDate.now(), closedAt.getTime(), JpPostalUtil.JST
                            );
                            return new ScheduleResult(
                                    new ScheduleResult.Event(closeAt, ScheduleResult.EventType.CLOSE),
                                    null, "営業中",
                                    isOpeningHour(closedAt, null, now), parsedMap, tagValue
                            );
                        }
                    }
                    //off or UNKNOWN
                    if (todaySchedule.status() == OpeningHoursParser.DayStatus.CLOSED_DAY) {
                        return new ScheduleResult(null, null, "休業日",
                                ScheduleResult.CurrentState.CLOSED,
                                parsedMap, tagValue);
                    } else {
                        if (today.dayType == Days.WeekDay.HOLIDAY) {
                            return new ScheduleResult(null, null, "祝日データなし",
                                    ScheduleResult.CurrentState.UNKNOWN,
                                    parsedMap, tagValue);
                        } else {
                            return new ScheduleResult(null, null, "本日分のデータなし",
                                    ScheduleResult.CurrentState.UNKNOWN,
                                    parsedMap, tagValue);
                        }
                    }
                } else {
                    for (int i=0; i<todaySchedule.schedule().size(); i++) {
                        OpenCloseTime focus = todaySchedule.schedule().get(i);
                        OpenCloseTime nextHours = null;
                        if (i<todaySchedule.schedule().size()-1) {
                            nextHours = todaySchedule.schedule().get(i+1);
                        }
                        ScheduleResult.CurrentState state = isOpeningHour(focus, nextHours, now);
                        if (state == null) {
                            continue;
                        }
                        ZonedDateTime openAt = ZonedDateTime.of(
                                LocalDate.now(), focus.openAt.getTime(), JpPostalUtil.JST
                        );
                        ZonedDateTime closeAt = ZonedDateTime.of(
                                LocalDate.now(), focus.closeAt.getTime(), JpPostalUtil.JST
                        );
                        if (state == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON) {
                            return new ScheduleResult(
                                    new ScheduleResult.Event(openAt, ScheduleResult.EventType.OPEN),
                                    null, "営業開始前（"+focus.openAt.value+"から）",
                                    state, parsedMap, tagValue
                            );
                        }else if (state == ScheduleResult.CurrentState.OPENING ||
                                state == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON) {
                            return new ScheduleResult(
                                    new ScheduleResult.Event(closeAt, ScheduleResult.EventType.CLOSE),
                                    null, "営業中（"+focus.closeAt.value+"まで）",
                                    state, parsedMap, tagValue
                            );
                        }
                    }
                    
                    ZonedDateTime nextOpen = null;
                    ZonedDateTime search = now.toLocalDate().plusDays(1).atStartOfDay(JpPostalUtil.JST);
                    int limit = 7;
                    while (0 < --limit) {
                        Days day = JpPostalUtil.getDays(search.toLocalDate());
                        OpeningHoursParser.DaySchedule daySchedule = parsedMap.get(day);
                        if (daySchedule != null && !daySchedule.schedule().isEmpty()) {
                            nextOpen = search.with(daySchedule.schedule().get(0).openAt.getTime());
                            break;
                        }
                        search = search.plusDays(1);
                    }

                    return new ScheduleResult(
                            new ScheduleResult.Event(nextOpen, ScheduleResult.EventType.OPEN), null, "本日は営業終了",
                            ScheduleResult.CurrentState.TODAY_FINISHED,
                            parsedMap, tagValue
                    );
                }
            }
            case COLLECTION_TIMES: {
                //深夜延長表示判定をしない

                Map<Days, CollectionTimeParser.DaySchedule> parsedMap = JpPostalUtil.decodeCollectionTimes((CollectionTimes) tagValue);
                if (parsedMap == null) {
                    return new ScheduleResult(null, null, "データ解釈エラー",
                            ScheduleResult.CurrentState.UNKNOWN,
                            new HashMap<>(), tagValue);
                }

                ScheduleResult.Event next = null;
                ScheduleResult.Event overTheNext = null;

                ZonedDateTime current = now;
                int limit = Days.values().length; //不明な祝日続きだとしても1週間を限度とする
                while(0 < --limit) {
                    Days currentDay = JpPostalUtil.getDays(current.toLocalDate());
                    CollectionTimeParser.DaySchedule currentSchedule = parsedMap.get(currentDay);
                    ZonedDateTime collectTime = findNext(current, currentSchedule);
                    if (collectTime != null) {
                        if (next != null) {
                            overTheNext = new ScheduleResult.Event(collectTime, ScheduleResult.EventType.COLLECTION);
                            break;
                        }
                        next = new ScheduleResult.Event(collectTime, ScheduleResult.EventType.COLLECTION);
                        current = collectTime;
                        continue;
                    }
                    current = current.toLocalDate().plusDays(1).atStartOfDay(JpPostalUtil.JST);
                }

                if (next == null) {
                    return new ScheduleResult(null, null, "データなし",
                            ScheduleResult.CurrentState.UNKNOWN,
                            parsedMap, tagValue);
                }

                String todayStats;
                ScheduleResult.CurrentState currentState;
                if (next.getTimestamp().toLocalDate().isEqual(now.toLocalDate())) {
                    if (now.plusHours(1).isBefore(next.getTimestamp())){
                        currentState = ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON;
                    }else {
                        currentState = ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON;
                    }
                    todayStats = "次回 " + String.format("%02d:%02d", next.getTimestamp().getHour(), next.getTimestamp().getMinute());
                }else {
                    Days today = JpPostalUtil.getDays(now.toLocalDate());
                    CollectionTimeParser.DaySchedule todaySchedule = parsedMap.get(today);
                    if (todaySchedule == null || todaySchedule.status() == CollectionTimeParser.DayStatus.UNKNOWN) {
                        currentState = ScheduleResult.CurrentState.UNKNOWN;
                        if (today.dayType == Days.WeekDay.HOLIDAY) {
                            todayStats = "祝日のため不明";
                        }else {
                            todayStats = "本日のデータなし";
                        }
                    } else if (todaySchedule.status() == CollectionTimeParser.DayStatus.CLOSED_DAY || todaySchedule.schedule().isEmpty()) {
                        currentState = ScheduleResult.CurrentState.CLOSED;
                        todayStats = "本日の収集なし";
                    } else {
                        currentState = ScheduleResult.CurrentState.TODAY_FINISHED;
                        todayStats = "本日の収集終了";
                    }
                }
                
                return new ScheduleResult(
                        next, overTheNext, todayStats,
                        currentState, parsedMap, tagValue
                );

            }
            default:
                return new ScheduleResult(null, null, "処理エラー",
                        ScheduleResult.CurrentState.UNKNOWN,
                        new HashMap<>(), tagValue);
        }
    }

    /** 前夜からの日跨ぎ営業をしている場合、その終了時刻を返す
     * @return 日跨ぎ営業している場合終了時刻。していない場合<code>null</code> */
    private TimeStr overNightOperation(Map<Days, OpeningHoursParser.DaySchedule> scheduleMap, ZonedDateTime today){
        ZonedDateTime yesterday = today.minusDays(1L);
        Days yesterdayDay = JpPostalUtil.getDays(yesterday.toLocalDate());
        OpeningHoursParser.DaySchedule schedule = scheduleMap.get(yesterdayDay);
        
        if (schedule.status() != OpeningHoursParser.DayStatus.OPEN_DAY ) {
            return null; // off, UNKNOWN, -24:00
        }
        
        OpenCloseTime lastOperation = schedule.schedule().get(schedule.schedule().size()-1);
        if (24*60 < lastOperation.closeAt.totalMinute) {
            return lastOperation.closeAt;
        }
        return null;
    }
    
    private ScheduleResult.CurrentState isOpeningHour(TimeStr closeAt, OpenCloseTime todayFirst, ZonedDateTime now){
        return isOpeningHour(new OpenCloseTime("0:00", closeAt.value), todayFirst, now);
    }
    private ScheduleResult.CurrentState isOpeningHour(OpenCloseTime current, OpenCloseTime nextZone, ZonedDateTime now){
        int nowOnMinutes = now.getHour() * 60 + now.getMinute();
        if (current.openAt.totalMinute <= nowOnMinutes &&
                nowOnMinutes < current.closeAt.totalMinute) {
            if (nowOnMinutes + 60 < current.closeAt.totalMinute) {
                return ScheduleResult.CurrentState.OPENING;
            } else {
                //but close soon
                return ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON;
            }
        }else {
            if (nowOnMinutes < current.openAt.totalMinute) {
                return ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON;
            } else {
                return null;
            }
        }
    }

    private ZonedDateTime findNext(ZonedDateTime current, CollectionTimeParser.DaySchedule todaySchedule){
        if (todaySchedule.schedule().isEmpty()){ return null; }
        for (CollectionTime scheduled : todaySchedule.schedule()) {
            if (scheduled.collectAt.getTime().isAfter(current.toLocalTime())) {
                return current.with(scheduled.collectAt.getTime());
            }
        }
        return null;
    }
    
    public static int parseMinutes(String time) {
        String[] part = time.split(":");
        return Integer.parseInt(part[0]) * 60 + Integer.parseInt(part[1]);
    }

    @Override
    public String format(Map<Days, List<? extends ITagPart>> weeklyTable, TimeType timeType) {
        switch (timeType) {
            case OPENING_HOURS: {
                Map<Days, OpeningHoursParser.DaySchedule> data = new HashMap<>();
                for (Days d : Days.values()) {
                    OpeningHoursParser.DayStatus status;
                    List<OpenCloseTime> inner = new ArrayList<>();
                    List<OpenCloseTime> dateSchedule = (List<OpenCloseTime>) weeklyTable.get(d);
                    if (dateSchedule == null) {
                        status = OpeningHoursParser.DayStatus.UNKNOWN;
                    } else {
                        if (dateSchedule.isEmpty()) {
                            status = OpeningHoursParser.DayStatus.CLOSED_DAY;
                        } else {
                            if (dateSchedule.size() == 1 &&
                                    dateSchedule.get(0).equals(new OpenCloseTime("0:00", "24:00"))) {
                                status = OpeningHoursParser.DayStatus.OPEN24;
                            } else {
                                status = OpeningHoursParser.DayStatus.OPEN_DAY;
                            }
                            for (OpenCloseTime part : dateSchedule) {
                                inner.add(part);
                            }
                        }
                    }
                    data.put(d, new OpeningHoursParser.DaySchedule(status, inner));
                }
                return JpPostalUtil.encodeOpeningHours(data).getOrigin();
            }
            case COLLECTION_TIMES: {
                Map<Days, CollectionTimeParser.DaySchedule> data = new HashMap<>();
                for (Days d : Days.values()) {
                    CollectionTimeParser.DayStatus status;
                    List<CollectionTime> inner = new ArrayList<>();
                    List<CollectionTime> dateSchedule = (List<CollectionTime>) weeklyTable.get(d);
                    if (dateSchedule == null) {
                        status = CollectionTimeParser.DayStatus.UNKNOWN;
                    } else {
                        if (dateSchedule.isEmpty()) {
                            status = CollectionTimeParser.DayStatus.CLOSED_DAY;
                        } else {
                            status = CollectionTimeParser.DayStatus.OPEN_DAY;
                            for (CollectionTime part : dateSchedule) {
                                inner.add(part);
                            }
                        }
                    }
                    data.put(d, new CollectionTimeParser.DaySchedule(status, inner));
                }
                return JpPostalUtil.encodeCollectionTimes(data).getOrigin();
            }
            default: {
                return "";
            }
        }
    }

}
