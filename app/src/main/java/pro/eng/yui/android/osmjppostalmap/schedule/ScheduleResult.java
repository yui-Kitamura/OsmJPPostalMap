package pro.eng.yui.android.osmjppostalmap.schedule;

import java.util.List;
import java.util.Map;

/**
 * opening_hoursやcollection_timesの解析結果を保持するモデル
 */
public class ScheduleResult {
    
    public enum EventType {
        COLLECTION, OPEN, CLOSE
    }
    
    public enum CurrentState {
        OPENING,      // 営業中/収集可能（残り1時間以上）
        OPEN_SOON,    // まもなく終了（残り1時間以内）
        TODAY_FINISHED, // 本日終了
        CLOSED        // 営業時間外
    }
    
    public static class Event {
        private final long timestamp;
        private final EventType type;

        public Event(long timestamp, EventType type) {
            this.timestamp = timestamp;
            this.type = type;
        }

        public long getTimestamp() { return timestamp; }
        public EventType getType() { return type; }
    }

    private final Event nextEvent;
    private final Event followingEvent;
    private final String todayStatus;
    private final CurrentState currentState;
    private final Map<String, List<String>> weeklyTable; // 曜日 -> 時間リスト
    private final String rawTagValue;

    public ScheduleResult(Event nextEvent, Event followingEvent, String todayStatus, 
                          CurrentState currentState, Map<String, List<String>> weeklyTable,
                          String rawTagValue) {
        this.nextEvent = nextEvent;
        this.followingEvent = followingEvent;
        this.todayStatus = todayStatus;
        this.currentState = currentState;
        this.weeklyTable = weeklyTable;
        this.rawTagValue = rawTagValue;
    }

    public Event getNextEvent() { return nextEvent; }
    public Event getFollowingEvent() { return followingEvent; }
    public String getTodayStatus() { return todayStatus; }
    public CurrentState getCurrentState() { return currentState; }
    public Map<String, List<String>> getWeeklyTable() { return weeklyTable; }
    public String getRawTagValue() { return rawTagValue; }
}
