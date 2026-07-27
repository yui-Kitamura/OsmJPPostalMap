package pro.eng.yui.android.osmjppostalmap.domain.repository;

import androidx.lifecycle.LiveData;
import java.util.List;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateResponse;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

/**
 * POIデータの取得と保存を担当するリポジトリインターフェース
 */
public interface PoiRepository {
    /**
     * 指定された領域のPOIを取得する
     * @param prefName 都道府県名
     */
    LiveData<List<OsmPoi>> getPois(String prefName);

    /**
     * 現在描画中のPOIリストを配信するLiveData（取得を伴わない購読用）。
     */
    LiveData<List<OsmPoi>> getPoisLiveData();

    /**
     * 表示範囲にかかる都道府県のPOIをキャッシュ優先で読み込む。
     * 未キャッシュの都道府県のみネットワーク取得し、SQLiteへ保存する。
     * 逆ジオコーディングとネットワークはバックグラウンドで実行される。
     *
     * @param latLonPoints 逆ジオコーディング対象の座標配列（各要素は {lat, lon}）。
     *                     通常は表示範囲の4隅＋中心を渡す。
     * @param forceNotify データが既にキャッシュされている場合でも LiveData を更新して通知するかどうか。
     */
    void loadPoisForArea(double[][] latLonPoints, boolean forceNotify);

    /**
     * 指定した都道府県をキャッシュ有無に関わらずネットワークから再取得し、
     * SQLiteを更新する（更新ダイアログの個別更新ボタン用）。
     */
    void refreshPrefecture(int prefCode, String prefName);

    /** 指定した都道府県のローカルキャッシュを削除する。 */
    void deletePrefectureCache(int prefCode);

    /**
     * ローカルに保存済みの都道府県メタ情報の一覧を返す（更新ダイアログ表示用）。
     */
    List<PrefMeta> getSavedPrefectures();

    /**
     * データ鮮度情報 (date.json) を取得する
     */
    void fetchDataDate(DataDateCallback callback);

    interface DataDateCallback {
        void onSuccess(DataDateResponse response);
        void onError(String message);
    }

    /**
     * POIをOSMに保存する
     */
    void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback);

    /**
     * 新規ポストを追加する
     */
    void addPostBox(double lat, double lon, String shape, String branch, String postboxRef, String collectionTimes, String note, java.util.Map<String, String> addressTags, PoiSaveCallback callback);

    /**
     * 地図メモ（Note）を追加する
     */
    void addNote(double lat, double lon, String text, PoiSaveCallback callback);

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

    /** リポジトリのバックグラウンド処理が実行中かどうか。 */
    LiveData<Boolean> getLoading();

    /** 現在実行中の処理内容を表すメッセージ。 */
    LiveData<String> getLoadingStatus();

    /**
     * クールダウンの全期間をミリ秒で取得する
     */
    long getCooldownInterval();

    interface PoiSaveCallback {
        void onSuccess();
        void onError(String message);
        default void onProgress(String message) {}
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
