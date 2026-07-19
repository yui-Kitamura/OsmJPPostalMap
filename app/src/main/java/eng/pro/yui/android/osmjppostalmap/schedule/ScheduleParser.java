package eng.pro.yui.android.osmjppostalmap.schedule;

/**
 * 営業中や収集時刻の解析を行うインターフェース
 */
public interface ScheduleParser {
    /**
     * rawタグ文字列を解析してScheduleResultを返す
     * @param tagValue 解析対象のタグ値 (opening_hours or collection_times)
     * @param currentTime 解析基準時刻 (ミリ秒)
     * @return 解析結果。失敗時は例外を投げず、rawTagValueを保持した最小限のモデルを返す
     */
    ScheduleResult parse(String tagValue, long currentTime);
}
