package pro.eng.yui.android.osmjppostalmap.schedule;

import pro.eng.yui.oss.osm.lib.jppostalcore.types.Days;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.ITagPart;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.TextValue;

import java.util.List;
import java.util.Map;

/**
 * opening_hours または collection_times タグを解析・生成するためのインターフェース。
 * 独立したモジュールとして、Android SDKに依存せずにJava標準ライブラリのみで動作するように実装されています。
 */
public interface ScheduleParser {
    
    enum TimeType {
        OPENING_HOURS,
        COLLECTION_TIMES;
    }

    enum Amenity {
        POST_BOX,
        POST_OFFICE
    }

    /**
     * rawタグ文字列を解析してScheduleResultを返す。
     * @param tagValue 解析対象のタグ値 (opening_hours or collection_times)
     * @param currentTime 解析基準時刻 (ミリ秒)
     * @param timeType 解析対象の種類 (COLLECTION_TIMES: 収集時刻、OPENING_HOURS: 営業時間)
     * @return 解析結果。失敗時は例外を投げず、rawTagValueを保持した最小限のモデルを返す
     */
    ScheduleResult parse(TextValue tagValue, long currentTime, TimeType timeType);

    /**
     * 曜日ごとの時間リストからタグ文字列を生成する。
     * @param weeklyTable 曜日 -> 時間帯リスト (例: "Mo" -> ["09:00-12:00", "13:00-18:00"])
     * @param timeType 解析対象の種類
     * @return 生成されたタグ文字列
     */
    String format(Map<Days, List<? extends ITagPart>> weeklyTable, TimeType timeType);
}
