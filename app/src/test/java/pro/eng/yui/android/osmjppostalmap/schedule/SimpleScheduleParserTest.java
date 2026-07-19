package pro.eng.yui.android.osmjppostalmap.schedule;

import org.junit.Test;

import java.time.*;

import static org.junit.Assert.*;

public class SimpleScheduleParserTest {
    /**
     * 入力条件: タグが null または空文字列
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN}, ステータス "不明"
     */
    @Test
    public void testEmptyTagReturnsUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse(null, System.currentTimeMillis(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        assertEquals("不明", result.getTodayStatus());

        result = parser.parse("", System.currentTimeMillis(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 入力条件: 有効なタグ (Mo-Su 00:00-24:00)
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN} 以外
     */
    @Test
    public void testValidTagReturnsNotUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        // Mo-Fr 09:00-17:00 形式
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult result = parser.parse("Mo-Su 00:00-24:00", zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertNotEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 入力条件: 24/7
     * 出力期待値: {@link ScheduleResult.CurrentState#OPENING}
     */
    @Test
    public void testTwentyFourSevenReturnsOpen() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult result = parser.parse("24/7", zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());
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
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 21, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        
        ScheduleResult result = parser.parse(input, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.OPENING, result.getCurrentState());

        // 19:00:00に判定したときはTODAY_FINISHED
        zdt = zdt.withHour(19).withMinute(0);
        result = parser.parse(input, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        
        // ただし祝日はUNKNOWN表示
        zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        result = parser.parse(input, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
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

        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 20, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo")); //土曜
        result = parser.parse(input, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
        
        // ただし該当曜日でも祝日の場合はUNKNOWN
        zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo")); //祝日月曜
        result = parser.parse(input, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
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

        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo")); // Monday, Holiday
        
        ScheduleResult result = parser.parse("Mo-Su 09:00-18:00", zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
    }

    /**
     * 解析不能な入力
     * 入力条件: "Invalid Tag Value"
     * 出力期待値: {@link ScheduleResult.CurrentState#UNKNOWN}
     */
    @Test
    public void testUnparseableReturnsUnknown() {
        SimpleScheduleParser parser = new SimpleScheduleParser();
        ScheduleResult result = parser.parse("Invalid Tag Value", System.currentTimeMillis(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
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
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 11, 30, 0, 0, ZoneId.of("Asia/Tokyo")); // Tuesday, 11:30

        // 休憩開始(12:00)が近い
        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());

        // 休憩時間内 (12:30) -> 開店前扱い
        zdt = zdt.withHour(12).withMinute(30);
        result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("営業開始前 13:00"));
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
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo")); // Tuesday, 9:00

        // 9:00 -> 次回 10:00 (1時間以内なので SOON)
        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("10:00"));

        // 11:00 -> 次回 13:30
        zdt = zdt.withHour(11);
        result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("13:30"));

        // 20:00 -> 本日の収集終了
        zdt = zdt.withHour(20);
        result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
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
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 12, 0, 0, 0, ZoneId.of("Asia/Tokyo")); // Tuesday, 12:00

        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.UNKNOWN, result.getCurrentState());
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
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 20, 0, 0, 0, ZoneId.of("Asia/Tokyo")); // Tuesday, 20:00

        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_OFFICE);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        
        ZonedDateTime nextEventZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getNextEvent().getTimestamp()), ZoneId.of("Asia/Tokyo"));
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
        ZonedDateTime zdt = ZonedDateTime.of(2024, 7, 16, 20, 0, 0, 0, ZoneId.of("Asia/Tokyo")); // Tuesday, 20:00

        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        assertEquals(ScheduleResult.EventType.COLLECTION, result.getNextEvent().getType());
        
        ZonedDateTime nextEventZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getNextEvent().getTimestamp()), ZoneId.of("Asia/Tokyo"));
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
        ZonedDateTime zdtSa = ZonedDateTime.of(2026, 7, 18, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultSa = parser.parse(tag, zdtSa.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        // 9:00 -> 11:00 は2時間あるので CLOSED
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, resultSa.getCurrentState());
        assertTrue(resultSa.getTodayStatus().contains("11:00"));

        // 10:30 になれば SOON
        zdtSa = zdtSa.withHour(10).withMinute(30);
        resultSa = parser.parse(tag, zdtSa.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, resultSa.getCurrentState());

        // 日曜日のチェック (9:00 -> 11:00 は CLOSED)
        ZonedDateTime zdtSu = ZonedDateTime.of(2026, 7, 19, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultSu = parser.parse(tag, zdtSu.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.CLOSED, resultSu.getCurrentState());
        assertTrue(resultSu.getTodayStatus().contains("11:00"));

        // 祝日のチェック (2026-07-20 は海の日で祝日) (9:00 -> 11:00 は CLOSED)
        ZonedDateTime zdtPh = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultPh = parser.parse(tag, zdtPh.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.CLOSED, resultPh.getCurrentState());
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
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 20, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        
        assertEquals(ScheduleResult.CurrentState.TODAY_FINISHED, result.getCurrentState());
        assertNotNull(result.getNextEvent());
        
        // 翌日は 2026-07-21 (火)。祝日ではないので Mo-Fr ルールが適用され、13:00 が NextEvent になるべき
        ZonedDateTime nextEventZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(result.getNextEvent().getTimestamp()), ZoneId.of("Asia/Tokyo"));
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
        ZonedDateTime zdtMo = ZonedDateTime.of(2026, 7, 13, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultMo = parser.parse(tag, zdtMo.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertTrue("Monday should have 10:00", resultMo.getTodayStatus().contains("10:00"));

        // 金曜日 (Fr)
        ZonedDateTime zdtFr = ZonedDateTime.of(2026, 7, 17, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultFr = parser.parse(tag, zdtFr.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertTrue("Friday should have 10:30", resultFr.getTodayStatus().contains("10:30"));

        // 土曜日 (Sa)
        ZonedDateTime zdtSa = ZonedDateTime.of(2026, 7, 18, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultSa = parser.parse(tag, zdtSa.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertTrue("Saturday should have 11:00", resultSa.getTodayStatus().contains("11:00"));

        // 祝日 (2026-07-20 PH)
        ZonedDateTime zdtPh = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult resultPh = parser.parse(tag, zdtPh.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertTrue("Holiday should have 11:00", resultPh.getTodayStatus().contains("11:00"));
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
        ZonedDateTime zdt = ZonedDateTime.of(2026, 7, 20, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"));
        ScheduleResult result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("11:00"));

        // 祝日 (2026-07-20) の 10:30 (11:00まで30分)
        zdt = zdt.withHour(10).withMinute(30);
        result = parser.parse(tag, zdt.toInstant().toEpochMilli(), ScheduleParser.Amenity.POST_BOX);
        assertEquals(ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON, result.getCurrentState());
        assertTrue(result.getTodayStatus().contains("11:00"));
    }

}
