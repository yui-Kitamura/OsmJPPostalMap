package pro.eng.yui.android.osmjppostalmap.schedule;

import java.util.List;
import java.util.Map;

/**
 * opening_hours または collection_times タグを解析・生成するためのインターフェース。
 * 独立したモジュールとして、Android SDKに依存せずにJava標準ライブラリのみで動作するように実装されています。
 */
public interface ScheduleParser {
    
    enum Amenity {
        POST_BOX,
        POST_OFFICE
    }

    /**
     * rawタグ文字列を解析してScheduleResultを返す。
     * @param tagValue 解析対象のタグ値 (opening_hours or collection_times)
     * @param currentTime 解析基準時刻 (ミリ秒)
     * @param amenity 施設種別 (POST_BOX: 収集時刻、POST_OFFICE: 営業時間)
     * @return 解析結果。失敗時は例外を投げず、rawTagValueを保持した最小限のモデルを返す
     */
    ScheduleResult parse(String tagValue, long currentTime, Amenity amenity);

    /**
     * 曜日ごとの時間リストからタグ文字列を生成する。
     * @param weeklyTable 曜日 -> 時間帯リスト (例: "Mo" -> ["09:00-12:00", "13:00-18:00"])
     * @param amenity 施設種別
     * @return 生成されたタグ文字列
     */
    String format(Map<String, List<String>> weeklyTable, Amenity amenity);
}
