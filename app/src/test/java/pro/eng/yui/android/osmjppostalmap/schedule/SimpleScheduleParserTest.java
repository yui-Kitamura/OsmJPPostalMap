package pro.eng.yui.android.osmjppostalmap.schedule;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.*;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleScheduleParserTest {

    @BeforeAll
    static void waitHolidays() throws InterruptedException {
        int retry = 0;
        while (!JpPostalUtil.isHolidaysLoaded() && retry < 5) {
            Thread.sleep(100);
            retry++;
        }
    }
    
    /**
     * 入力条件: タグが null または空文字列
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN}, ステータス "不明"
     */
    @Test
    public void testMoFrExpansion() {
        // Mo-Fr 10:00 が正しく展開されるか確認
        String tag = "Mo-Fr 10:00";
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 13, 12, 0, 0, 0,JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        Map<Days, ? extends IDaySchedule> table = result.getWeeklyTable();
        
        assertTrue(table.containsKey(Days.MONDAY));
        assertTrue(table.containsKey(Days.TUESDAY));
        assertTrue(table.containsKey(Days.WEDNESDAY));
        assertTrue(table.containsKey(Days.THURSDAY));
        assertTrue(table.containsKey(Days.FRIDAY));
        assertTrue(table.containsKey(Days.SATURDAY));
        assertEquals( CollectionTimeParser.DayStatus.UNKNOWN, table.get(Days.SATURDAY).status());
    }

    @Test
    public void testCommaSpaceExpansion() {
        // Mo, We-Fr 10:00 が正しく展開されるか確認
        String tag = "Mo, We-Fr 10:00";
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 13, 12, 0, 0, 0,JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        Map<Days, ? extends IDaySchedule> table = result.getWeeklyTable();
        
        assertTrue(table.containsKey(Days.MONDAY));
        assertTrue(table.containsKey(Days.TUESDAY));
        assertEquals( CollectionTimeParser.DayStatus.UNKNOWN, table.get(Days.TUESDAY).status());
        assertTrue(table.containsKey(Days.WEDNESDAY));
        assertTrue(table.containsKey(Days.THURSDAY));
        assertTrue(table.containsKey(Days.FRIDAY));
    }

    @Test
    public void testOffDistinctionInWeeklyTable() {
        // Mo-Fr 10:00; Sa off; (Su/PH は記述なし)
        String tag = "Mo-Fr 10:00; Sa off;";
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0,JpPostalUtil.JST); // Tue
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        Map<Days, ? extends IDaySchedule> table = result.getWeeklyTable();
        
        // Mo-Fr はデータあり
        assertTrue(table.containsKey(Days.MONDAY));
        assertFalse(table.get(Days.MONDAY).schedule().isEmpty());
        
        // Sa は explicit off (empty list)
        assertTrue(table.containsKey(Days.SATURDAY));
        assertTrue(table.get(Days.SATURDAY).schedule().isEmpty());
        
        // Su, PH はキー自体がない (unknown)
        assertTrue(table.containsKey(Days.SUNDAY));
        assertEquals( CollectionTimeParser.DayStatus.UNKNOWN, table.get(Days.SUNDAY).status());
        assertTrue(table.containsKey(Days.PUBLIC_HOLIDAY));
        assertEquals( CollectionTimeParser.DayStatus.UNKNOWN, table.get(Days.PUBLIC_HOLIDAY).status());
    }

    @Test
    public void testMultipleCollectionsToday() {
        // Mo-Su 10:00, 15:00
        String tag = "Mo-Su 10:00, 15:00";
        // 2026-07-20 09:00 (Mon)
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 21, 9, 0, 0, 0, JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new CollectionTimes(tag), now.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        assertNotNull(result.getNextEvent());
        assertEquals(10, result.getNextEvent().getTimestamp().getHour());
        
        assertNotNull(result.getFollowingEvent(), "Following event should not be null");
        assertEquals(15, result.getFollowingEvent().getTimestamp().getHour());
    }

    @Test
    public void testEmptyTagReturnsUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(null, System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        assertEquals("不明", result.getTodayStatus());

        result = parser.parse(new OpeningHours(""), System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 入力条件: 有効なタグ (Mo-Su 00:00-24:00)
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN} 以外
     */
    @Test
    public void testValidTagReturnsNotUnknown() {
        // Mo-Fr 09:00-17:00 形式
        // 2026-07-21 (火) - 平日
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0,JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new OpeningHours("Mo-Su 00:00-24:00"), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertNotEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 入力条件: 24/7
     * 出力期待値: {@link ScheduleResult.CurrentState#OPENING}
     */
    @Test
    public void testTwentyFourSevenReturnsOpen() {
        // 2026-07-21 (火) - 平日
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0,JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new OpeningHours("24/7"), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());
    }

    @Test
    public void testTwentyFourSevenNextEventReproduction() {
        // 2026-07-21 (火) 12:00
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0, JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new OpeningHours("24/7"), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);

        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());
        // 修正後: 24/7 の場合、nextEvent は null であるべき
        assertNull(result.getNextEvent());
    }

    @Test
    public void testMidnightHandling() {
        // 2026-07-21 (火) 12:00
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0, JpPostalUtil.JST);
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new OpeningHours("Mo-Su 10:00-24:00"), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);

        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        // 期待値: 2026-07-22 00:00
        ZonedDateTime expected = ZonedDateTime.of(2026, 7, 22, 0, 0, 0, 0, JpPostalUtil.JST);
        assertEquals(expected, result.getNextEvent().getTimestamp());
    }

    /**
     * 曜日指定なしのケース
     * <p>
     * 入力条件: 10:00-19:00
     * 出力期待値:
     * <ul>
     *   <li>平日12:00 -> {@link ScheduleResult.CurrentState#OPENING}</li>
     *   <li>平日19:00 -> {@link ScheduleResult.CurrentState#TODAY_FINISHED}</li>
     *   <li>祝日12:00 -> {@link ScheduleResult.CurrentState#UNKNOWN}</li>
     * </ul>
     */
    @Test
    public void testNoDaySpec() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // 10:00-19:00; (曜日なし) -> 曜日考慮せず。
        final String input = "10:00-19:00";
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0,JpPostalUtil.JST);
        
        ScheduleResult result = parser.parse(new OpeningHours(input), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());

        // 19:00:00に判定したときはTODAY_FINISHED
        zdt = zdt.withHour(19).withMinute(0);
        result = parser.parse(new OpeningHours(input), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        
        // ただし祝日はUNKNOWN表示
        zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0,JpPostalUtil.JST);
        result = parser.parse(new OpeningHours(input), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 指定外の曜日のケース
     * <p>
     * 入力条件: Mo-Fr 10:00-17:00
     * 出力期待値:
     * <ul>
     *   <li>土曜日 -> {@link ScheduleResult.CurrentState#UNKNOWN}</li>
     *   <li>祝日月曜日 -> {@link ScheduleResult.CurrentState#UNKNOWN}</li>
     * </ul>
     */
    @Test
    public void testMissingDaysReturnUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        final String input = "Mo-Fr 10:00-17:00"; // (週末の情報なし) -> 判定できる曜日以外はUNKNOWN
        ScheduleResult result;

        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 20, 12, 0, 0, 0,JpPostalUtil.JST); //土曜
        result = parser.parse(new OpeningHours(input), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        
        // ただし該当曜日でも祝日の場合はUNKNOWN
        zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0,JpPostalUtil.JST); //祝日月曜
        result = parser.parse(new OpeningHours(input), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 祝日の情報が欠落しているケース
     * <p>
     * 入力条件: Mo-Su 09:00-18:00
     * 出力期待値: 祝日月曜日 -> {@link ScheduleResult.CurrentState#UNKNOWN}
     */
    @Test
    public void testHolidayMissingReturnsUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0,JpPostalUtil.JST); // Monday, Holiday
        
        ScheduleResult result = parser.parse(new OpeningHours("Mo-Su 09:00-18:00"), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 解析不能な入力
     * 入力条件: "Invalid Tag Value"
     * 出力期待値: {@link ScheduleResult.CurrentState#PARSE_ERROR}
     */
    @Test
    public void testUnparseableReturnsParseError() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(new OpeningHours("Invalid Tag Value"), System.currentTimeMillis(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.PARSE_ERROR, result.getCurrentState());
    }

    /**
     * 休憩時間のケース
     * 入力条件: 09:00-12:00, 13:00-17:00
     * 出力期待値:
     * <ul>
     *   <li>11:30 -> {@link ScheduleResult.CurrentState#OPENING_BUT_EVENT_SOON} (休憩開始が近い)</li>
     *   <li>12:30 -> {@link ScheduleResult.CurrentState#CLOSING_BUT_OPEN_SOON} (休憩時間内)</li>
     * </ul>
     */
    @Test
    public void testBreakTime() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Su 09:00-12:00, 13:00-17:00";
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 11, 30, 0, 0,JpPostalUtil.JST); // Tuesday, 11:30

        // 休憩開始(12:00)が近い
        ScheduleResult result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());

        // 休憩時間内 (12:30) -> 開店前扱い
        zdt = zdt.withHour(12).withMinute(30);
        result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertEquals("営業開始前（13:00から）", result.getTodayStatus());
    }

    /**
     * 収集時間形式のケース
     * 入力条件: 10:00,13:30,19:00;
     * 出力期待値:
     * <ul>
     *   <li>09:00 -> {@link ScheduleResult.CurrentState#OPENING_BUT_EVENT_SOON} (次回 10:00)</li>
     *   <li>11:00 -> {@link ScheduleResult.CurrentState#OPENING} (次回 13:30)</li>
     *   <li>20:00 -> {@link ScheduleResult.CurrentState#TODAY_FINISHED} (本日の収集終了)</li>
     * </ul>
     */
    @Test
    public void testCollectionTimeList() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "10:00,13:30,19:00;";
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 9, 0, 0, 0,JpPostalUtil.JST); // Tuesday, 9:00

        // 9:00 -> 次回 10:00 (1時間以内なので SOON)
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("10:00"));
        assertTrue(result.getTodayStatus().startsWith("次回 "));

        // 10:00 -> 本日の収集終了または次回
        // 指示により 10:00 は「手遅れ」扱いにする
        zdt = zdt.withHour(10).withMinute(0);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        // 10:00 ちょうどは 13:30 が次回になるべき
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("13:30"));
        assertTrue(result.getTodayStatus().startsWith("次回 "));

        // 11:00 -> 次回 13:30
        zdt = zdt.withHour(11);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("13:30"));

        // 20:00 -> 本日の収集終了
        zdt = zdt.withHour(20);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("終了"));
    }

    /**
     * ALL構文が無視されることの確認
     * 入力条件: ALL 10:00-19:00
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN}
     */
    @Test
    public void testAllSyntaxIgnored() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "ALL 10:00-19:00";
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 12, 0, 0, 0,JpPostalUtil.JST); // Tuesday, 12:00

        ScheduleResult result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.PARSE_ERROR, result.getCurrentState());
    }

    /**
     * 本日終了後に翌日のイベントが取得できることの確認
     * 入力条件: 10:00-19:00, 現在時刻 20:00
     * 出力期待値:
     * <ul>
     *   <li>{@link ScheduleResult.CurrentState#TODAY_FINISHED}</li>
     *   <li>nextEvent が翌日の 10:00 であること</li>
     * </ul>
     */
    @Test
    public void testNextDayEventAfterFinished() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Su 10:00-19:00";
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 20, 0, 0, 0,JpPostalUtil.JST); // Tuesday, 20:00

        ScheduleResult result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        
        ZonedDateTime nextEventZdt = result.getNextEvent().getTimestamp();
        assertEquals(17, nextEventZdt.getDayOfMonth()); // Wednesday
        assertEquals(10, nextEventZdt.getHour());
        assertEquals(0, nextEventZdt.getMinute());
    }

    /**
     * 収集時刻形式のケースで本日終了後に翌日の収集時刻が取得できることの確認
     * 入力条件: 10:00,13:30,19:00, 現在時刻 20:00
     * 出力期待値:
     * <ul>
     *   <li>{@link ScheduleResult.CurrentState#TODAY_FINISHED}</li>
     *   <li>nextEvent が翌日の 10:00 であること</li>
     * </ul>
     */
    @Test
    public void testNextDayCollectionAfterFinished() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Su 10:00,13:30,19:00";
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 20, 0, 0, 0,JpPostalUtil.JST); // Tuesday, 20:00

        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        assertEquals(ScheduleResult.EventType.COLLECTION, result.getNextEvent().getType());
        
        ZonedDateTime nextEventZdt = result.getNextEvent().getTimestamp();
        assertEquals(17, nextEventZdt.getDayOfMonth()); // Wednesday
        assertEquals(10, nextEventZdt.getHour());
    }

    /**
     * 混在する曜日指定のケース
     * 入力条件: Mo-Fr 10:00; Sa-Su,PH 11:00;
     */
    @Test
    public void testMixedDaySpec() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Fr 10:00; Sa-Su,PH 11:00;";
        
        // 土曜日のチェック
        ZonedDateTime zdtSa = ZonedDateTime.of(2026, 7, 18, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultSa = parser.parse(new CollectionTimes(tag), zdtSa.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        // 9:00 -> 11:00 は2時間あるので CLOSED
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, resultSa.getCurrentState());
        assertTrue(resultSa.getTodayStatus().contains("11:00"));

        // 10:30 になれば SOON
        zdtSa = zdtSa.withHour(10).withMinute(30);
        resultSa = parser.parse(new CollectionTimes(tag), zdtSa.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, resultSa.getCurrentState());

        // 日曜日のチェック (9:00 -> 11:00 は CLOSED)
        ZonedDateTime zdtSu = ZonedDateTime.of(2026, 7, 19, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultSu = parser.parse(new CollectionTimes(tag), zdtSu.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, resultSu.getCurrentState());
        assertTrue(resultSu.getTodayStatus().contains("11:00"));

        // 祝日のチェック (2026-07-20 は海の日で祝日) (9:00 -> 11:00 は CLOSED)
        ZonedDateTime zdtPh = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultPh = parser.parse(new CollectionTimes(tag), zdtPh.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, resultPh.getCurrentState());
        assertTrue(resultPh.getTodayStatus().contains("11:00"));
    }

    /**
     * 祝日かつ今日の収集が終わっている場合の NextEvent 取得テスト
     * 入力条件: Mo-Fr 13:00,16:30; Sa-Su,PH 8:00;
     */
    @Test
    public void testNextEventAfterHolidayFinished() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Fr 13:00,16:30; Sa-Su,PH 8:00;";
        
        // 祝日の20:00 (今日の収集 8:00 は終わっている)
        // 2026-07-20 (月・祝)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        
        // 翌日は 2026-07-21 (火)。祝日ではないので Mo-Fr ルールが適用され、13:00 が NextEvent になるべき
        ZonedDateTime nextEventZdt = result.getNextEvent().getTimestamp();
        assertEquals(21, nextEventZdt.getDayOfMonth());
        assertEquals(13, nextEventZdt.getHour());
        assertEquals(0, nextEventZdt.getMinute());
    }
    
    
    /**
     * ユーザー提示のケース: Mo-Th 10:00; Fr 10:30; Sa-Su,PH 11:00;
     */
    @Test
    public void testUserCaseMoThFrSaSuPH() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Th 10:00; Fr 10:30; Sa-Su,PH 11:00;";
        
        // 月曜日 (Mo)
        ZonedDateTime zdtMo = ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultMo = parser.parse(new CollectionTimes(tag), zdtMo.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertTrue(resultMo.getTodayStatus().contains("10:00"), "Monday should have 10:00");

        // 金曜日 (Fr)
        ZonedDateTime zdtFr = ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultFr = parser.parse(new CollectionTimes(tag), zdtFr.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertTrue(resultFr.getTodayStatus().contains("10:30"), "Friday should have 10:30");

        // 土曜日 (Sa)
        ZonedDateTime zdtSa = ZonedDateTime.of(2026, 7, 18, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultSa = parser.parse(new CollectionTimes(tag), zdtSa.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertTrue(resultSa.getTodayStatus().contains("11:00"), "Saturday should have 11:00");

        // 祝日 (2026-07-20 PH)
        ZonedDateTime zdtPh = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultPh = parser.parse(new CollectionTimes(tag), zdtPh.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertTrue(resultPh.getTodayStatus().contains("11:00"), "Holiday should have 11:00");
    }

    /**
     * 今日が祝日でまだ時間が到来していないパターンのテスト
     * <p>
     * 入力条件: Sa-Su,PH 11:00, 祝日 9:00
     * 出力期待値:
     * <ul>
     *   <li>9:00 -> {@link ScheduleResult.CurrentState#CLOSED} (11:00まで2時間あり)</li>
     *   <li>10:30 -> {@link ScheduleResult.CurrentState#OPENING_BUT_EVENT_SOON} (11:00まで30分)</li>
     * </ul>
     */
    @Test
    public void testHolidayBeforeEventTime() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Sa-Su,PH 11:00;";

        // 祝日 (2026-07-20) の 9:00 (11:00まで2時間あり)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("11:00"));

        // 祝日 (2026-07-20) の 10:30 (11:00まで30分)
        zdt = zdt.withHour(10).withMinute(30);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("11:00"));
    }

    @Test
    public void testReproductionHolidayIssue() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // 平日は 10:00, 祝日は 11:00 のスケジュール
        String tag = "Mo-Fr 10:00; Sa-Su,PH 11:00;";

        // 2026-07-20 02:58 (月・祝)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 2, 58, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);

        // 祝日なので 11:00 が採用されるべき
        assertTrue(result.getTodayStatus().contains("11:00"));
        assertFalse(result.getTodayStatus().contains("10:00"));
    }

    @Test
    public void testHolidayNoPHShouldBeUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // PH の指定がないが Mo はある場合
        String tag = "Mo-Fr 10:00; Sa 11:00; Su 12:00;";

        // 2026-07-20 (月・祝)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);

        // PHがないので、祝日判定されると UNKNOWN になるべき
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        assertEquals("祝日のため不明", result.getTodayStatus());
    }

    @Test
    public void testNextEventWhenNextDayIsHolidayMissingPH() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // PH の指定がないが Mo はある場合
        String tag = "Mo-Fr 10:00; Sa 11:00; Su 12:00;";

        // 2026-07-19 (日) 20:00
        // 翌日 7/20 は祝日(月)だが PH 指定がない。
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 19, 20, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);

        // 今日の収集は終了している
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        
        // 翌日が祝日で PH がない場合、NextEvent はその日をスキップする
        assertEquals(DayOfWeek.TUESDAY, result.getNextEvent().getTimestamp().getDayOfWeek());
    }

    @Test
    public void testHolidayMidnightIssueReproduction() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // Mo-Fr 10:00 のみ (PHなし)
        String tag = "Mo-Fr 10:00;";

        // 2026-07-20 03:21 (月・祝)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 3, 21, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);

        // 期待値: 祝日判定され、PHがないため UNKNOWN になり、NextEvent は翌営業日の収集時刻 になるべき
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        assertEquals(new ScheduleResult.Event(ZonedDateTime.of(2026,7,21,10,0,0,0,JpPostalUtil.JST), ScheduleResult.EventType.COLLECTION), result.getNextEvent());
    }

    @Test
    public void testWeekCrossingRange() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // 2026-07-21 (火) - 平日
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0,JpPostalUtil.JST);
        
        // We-Mo 10:00; Tu 11:00;
        // 火曜日(Tu)は 11:00 のはず
        String tag = "We-Mo 10:00; Tu 11:00;";
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertEquals("本日の収集終了", result.getTodayStatus());
        
        // 10:59 なら OPENING_BUT_EVENT_SOON
        zdt = zdt.withHour(10).withMinute(59);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        
        // 水曜日(We)は 10:00
        zdt = ZonedDateTime.of(2026, 7, 22, 9, 0, 0, 0,JpPostalUtil.JST);
        result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("10:00"));
    }

    @Test
    public void testCollectionTimeExactMatchHandled() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "10:00;";
        // 10:00 ちょうどに判定
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 10, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        // 10:00ちょうどは「手遅れ（終了）」扱いで、次のイベント（翌日など）を探しに行くべき
        // 今回はタグに10:00しかないため、本日の収集は終了扱いになる
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
    }

    @Test
    public void testOverlappingRulesPriority() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // 9:00-19:00; Sa-Su 9:00-12:00
        // 土日は 9:00-12:00 が優先されるべき
        String tag = "9:00-19:00; Sa-Su 9:00-12:00";
        
        // 土曜日の 15:00
        ZonedDateTime zdtSa = ZonedDateTime.of(2026, 7, 18, 15, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new OpeningHours(tag), zdtSa.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        
        // 12:00 で終了しているはずなので TODAY_FINISHED
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        
        // 24/7; PH off;
        // 祝日は休みであるべき
        String tag2 = "24/7; PH off;";
        // 2026-07-20 (月・祝)
        ZonedDateTime zdtPh = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultPh = parser.parse(new OpeningHours(tag2), zdtPh.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        
        // PH off なので CLOSED
        assertEquals(ScheduleResult.CurrentState.CLOSED, resultPh.getCurrentState());
    }

    @Test
    public void testCommaListPriority() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // Mo-Fr 9:00-17:00; Mo,We 9:00-12:00
        // 月、水は 9:00-12:00 が優先されるべき
        String tag = "Mo-Fr 9:00-17:00; Mo,We 9:00-12:00";
        
        // 月曜日の 15:00
        // 2026-07-13 (月)
        ZonedDateTime zdtMo = ZonedDateTime.of(2026, 7, 13, 15, 0, 0, 0,JpPostalUtil.JST);
        
        ScheduleResult result = parser.parse(new OpeningHours(tag), zdtMo.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        
        // 火曜日の 15:00 (火曜日は上書きされていないので 17:00 まで)
        ZonedDateTime zdtTu = ZonedDateTime.of(2026, 7, 14, 15, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult resultTu = parser.parse(new OpeningHours(tag), zdtTu.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);
        assertEquals(ScheduleResult.CurrentState.OPENING, resultTu.getCurrentState());
    }

    @Test
    public void testUnknownStatusLabel() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // Mo-Fr 09:00-17:00 (土日は指定なし = UNKNOWN)
        String tag = "Mo-Fr 09:00-17:00";

        // 2026-07-25 (土曜日)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 25, 12, 0, 0, 0, JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);

        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        assertEquals("不明", result.getTodayStatus());
    }

    @Test
    public void testOffStatusLabel() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // Mo-Fr 09:00-17:00; Sa-Su off (土日は off)
        String tag = "Mo-Fr 09:00-17:00; Sa-Su off";

        // 2026-07-25 (土曜日)
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 25, 12, 0, 0, 0, JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new OpeningHours(tag), zdt.toInstant().toEpochMilli(), ScheduleParser.TimeType.OPENING_HOURS);

        assertEquals(ScheduleResult.CurrentState.CLOSED, result.getCurrentState());
        assertEquals("休業日", result.getTodayStatus());
    }

    @Test
    public void testFollowingEventAcrossDays() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Su 10:00, 15:00";

        // 2026-07-21 (火) 11:00
        // 次回は本日 15:00。その次は翌日 10:00。
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 21, 11, 0, 0, 0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), now.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);

        assertNotNull(result.getNextEvent());
        assertEquals(15, result.getNextEvent().getTimestamp().getHour());

        assertNotNull(result.getFollowingEvent());
        ZonedDateTime followZdt = result.getFollowingEvent().getTimestamp();
        assertEquals(22, followZdt.getDayOfMonth());
        assertEquals(10, followZdt.getHour());
    }
    
    @Test
    public void testEventALot(){
        SimpleScheduleParser parser = new SimpleScheduleParser();
        String tag = "Mo-Su,PH 7:30,8:50,12:30,13:55,15:40,19:00;";
        ZonedDateTime now = ZonedDateTime.of(2026,7,25,9,0,0,0,JpPostalUtil.JST);
        ScheduleResult result = parser.parse(new CollectionTimes(tag), now.toInstant().toEpochMilli(), ScheduleParser.TimeType.COLLECTION_TIMES);
        
        assertNotNull(result.getNextEvent());
    
    }

    @Test
    public void testFormatOffAndUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        Map<Days, List<? extends pro.eng.yui.oss.osm.lib.jppostalcore.types.ITagPart>> weeklyTable = new java.util.HashMap<>();
        
        // Mo-Fr is 10:00
        List<CollectionTime> wdTimes = new java.util.ArrayList<>();
        wdTimes.add(new pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTime("10:00"));
        weeklyTable.put(Days.MONDAY, wdTimes);
        weeklyTable.put(Days.TUESDAY, wdTimes);
        weeklyTable.put(Days.WEDNESDAY, wdTimes);
        weeklyTable.put(Days.THURSDAY, wdTimes);
        weeklyTable.put(Days.FRIDAY, wdTimes);
        
        // Sa is off
        weeklyTable.put(Days.SATURDAY, new java.util.ArrayList<>());
        
        // Su is unknown (not in map)
        
        String formatted = parser.format(weeklyTable, ScheduleParser.TimeType.COLLECTION_TIMES);
        // 期待値: Mo-Fr 10:00; Sa off; (Su/PH はなし)
        // 実際のフォーマットはライブラリ依存だが、off が含まれ、Su が含まれないことを期待
        assertTrue(formatted.contains("10:00"));
        assertTrue(formatted.contains("Sa off"));
        assertFalse(formatted.contains("Su"));
    }
}
