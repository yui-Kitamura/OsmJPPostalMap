package pro.eng.yui.android.osmjppostalmap.schedule;

import androidx.annotation.Nullable;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.*;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * opening_hoursやcollection_timesの解析結果を保持するモデル
 */
public class ScheduleResult {
    
    public enum EventType {
        COLLECTION, OPEN, CLOSE
    }

    public enum CurrentState {
        /** 営業中/収集可能（残り1時間以上） */
        OPENING,
        /** まもなく終了（残り1時間以内） */
        OPENING_BUT_EVENT_SOON,
        /** 本日終了 */
        TODAY_FINISHED,
        /** 営業時間外 */
        CLOSED,
        /** 営業開始前（本日開始予定あり） */
        CLOSING_BUT_OPEN_SOON,
        /** 情報なし（タグ未登録など） */
        UNKNOWN
    }
    
    public static class Event {
        private final ZonedDateTime timestamp;
        private final EventType type;

        public Event(ZonedDateTime timestamp, EventType type) {
            this.timestamp = timestamp;
            this.type = type;
        }

        public ZonedDateTime getTimestamp() { return timestamp; }
        public EventType getType() { return type; }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Event other = (Event) obj;
            return timestamp.equals(other.timestamp) && type == other.type;
        }
    }

    private final Event nextEvent;
    private final Event followingEvent;
    private final String todayStatus;
    private final CurrentState currentState;
    private final Map<Days, ? extends IDaySchedule> weeklyTable; // 曜日 -> 時間リスト
    private final TextValue rawTagValue;

    public ScheduleResult(Event nextEvent, Event followingEvent, String todayStatus,
                          CurrentState currentState, Map<Days, ? extends IDaySchedule> weeklyTable,
                          TextValue rawTagValue) {
        this.nextEvent = nextEvent;
        this.followingEvent = followingEvent;
        this.todayStatus = todayStatus;
        this.currentState = currentState;
        this.weeklyTable = weeklyTable;
        this.rawTagValue = rawTagValue;
    }

    public Event getNextEvent() { return nextEvent; }
    public Event getFollowingEvent() { return followingEvent; }
    /** UI用状態ラベル */
    public String getTodayStatus() { return todayStatus; }
    public CurrentState getCurrentState() { return currentState; }
    public Map<Days, ? extends IDaySchedule> getWeeklyTable() { return weeklyTable; }
    public TextValue getRawTagValue() { return rawTagValue; }
}
