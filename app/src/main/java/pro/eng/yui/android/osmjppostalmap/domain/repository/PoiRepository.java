package pro.eng.yui.android.osmjppostalmap.domain.repository;

import androidx.lifecycle.LiveData;
import java.util.List;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;

/**
 * POIデータの取得と保存を担当するリポジトリインターフェース
 */
public interface PoiRepository {
    /**
     * 指定された領域のPOIを取得する
     */
    LiveData<List<OsmPoi>> getPois(double minLat, double minLon, double maxLat, double maxLon);

    /**
     * 指定されたIDのPOIを取得する
     */
    LiveData<OsmPoi> getPoi(long id, String type);

    /**
     * POIをOSMに保存する
     */
    void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback);

    /**
     * 新規ポストを追加する
     */
    void addPostBox(double lat, double lon, String shape, String branch, String postboxRef, String collectionTimes, String note, PoiSaveCallback callback);

    /**
     * 地図メモ（Note）を追加する
     */
    void addNote(double lat, double lon, String text, PoiSaveCallback callback);

    /**
     * キーワードでPOIを検索する
     */

    /**
     * エラーメッセージを配信するLiveDataを取得する
     */
    LiveData<String> getError();

    /**
     * 成功メッセージを配信するLiveDataを取得する
     */
    LiveData<String> getSuccessMessage();

    /**
     * クールダウンの残り時間をミリ秒で取得するLiveDataを取得する
     */
    LiveData<Long> getCooldownRemaining();

    /**
     * クールダウンの全期間をミリ秒で取得する
     */
    long getCooldownInterval();

    interface PoiSaveCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * エラーメッセージをクリアする
     */
    void clearError();

    /**
     * 成功メッセージをクリアする
     */
    void clearSuccessMessage();
}
