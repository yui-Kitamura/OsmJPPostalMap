package pro.eng.yui.android.osmjppostalmap.ui;

import android.location.Location;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateResponse;
import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTimes;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpeningHours;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.TextValue;

public class MainViewModel extends ViewModel {

    private final PoiRepository repository;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> filterOpenOnly = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> filterPostOfficeOnly = new MutableLiveData<>(false);
    private final MutableLiveData<List<OsmPoi>> filteredPois = new MutableLiveData<>();
    private final MutableLiveData<DataDateResponse> dataDate = new MutableLiveData<>();
    private final ExecutorService filterExecutor = Executors.newSingleThreadExecutor();

    public MainViewModel() {
        this.repository = PoiRepositoryImpl.getInstance();

        repository.getPoisLiveData().observeForever(pois -> applyFilter());
        filterOpenOnly.observeForever(filter -> applyFilter());
        filterPostOfficeOnly.observeForever(filter -> applyFilter());
        repository.getError().observeForever(errorMessage::postValue);
        repository.getSuccessMessage().observeForever(successMessage::postValue);
    }

    public LiveData<Boolean> getFilterOpenOnly() {
        return filterOpenOnly;
    }

    public LiveData<Boolean> getFilterPostOfficeOnly() {
        return filterPostOfficeOnly;
    }

    public LiveData<List<OsmPoi>> getPois() {
        return repository.getPoisLiveData();
    }

    public LiveData<List<OsmPoi>> getFilteredPois() {
        return filteredPois;
    }

    public void setFilterOpenOnly(boolean openOnly) {
        filterOpenOnly.setValue(openOnly);
    }

    public void setFilterPostOfficeOnly(boolean postOfficeOnly) {
        filterPostOfficeOnly.setValue(postOfficeOnly);
    }

    /** マーカーの状態（営業中/収集時間など）を現在時刻で再評価し、LiveDataを更新する */
    public void forceRefresh() {
        applyFilter();
    }

    private void applyFilter() {
        List<OsmPoi> allPois = repository.getPoisLiveData().getValue();
        if (allPois == null) return;

        boolean openOnly = filterOpenOnly.getValue() != null && filterOpenOnly.getValue();
        boolean postOfficeOnly = filterPostOfficeOnly.getValue() != null && filterPostOfficeOnly.getValue();

        if (!openOnly && !postOfficeOnly) {
            filteredPois.postValue(allPois);
            return;
        }

        filterExecutor.execute(() -> {
            List<OsmPoi> filtered = new java.util.ArrayList<>();
            SimpleScheduleParser parser = new SimpleScheduleParser();
            long now = System.currentTimeMillis();

            for (OsmPoi poi : allPois) {
                String amenityStr = poi.getTag("amenity");
                boolean isPostOffice = "post_office".equals(amenityStr);

                // 郵便局フィルタ
                if (postOfficeOnly && !isPostOffice) {
                    continue;
                }

                // 開店中フィルタ
                if (openOnly) {
                    ScheduleParser.Amenity amenity = isPostOffice ?
                        ScheduleParser.Amenity.POST_OFFICE :
                        ScheduleParser.Amenity.POST_BOX;

                    String tagName = (amenity == ScheduleParser.Amenity.POST_OFFICE) ?
                        "opening_hours" : "collection_times";
                    ScheduleParser.TimeType timeType = (amenity == ScheduleParser.Amenity.POST_OFFICE) ?
                            ScheduleParser.TimeType.OPENING_HOURS : ScheduleParser.TimeType.COLLECTION_TIMES;

                    TextValue tagValue = (amenity == ScheduleParser.Amenity.POST_OFFICE) ?
                            new OpeningHours(poi.getTag(tagName)) :
                            new CollectionTimes(poi.getTag(tagName));

                    ScheduleResult res = parser.parse(tagValue, now, timeType);

                    boolean isOpen = (res.getCurrentState() == ScheduleResult.CurrentState.OPENING ||
                        res.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON ||
                        res.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON);

                    // 郵便局の場合、ゆうゆう窓口もチェック
                    if (!isOpen && isPostOffice) {
                        String lsTag = poi.getTag("opening_hours:limited_service");
                        if (lsTag != null && !lsTag.isEmpty()) {
                            ScheduleResult lsRes = parser.parse(new OpeningHours(lsTag), now, ScheduleParser.TimeType.OPENING_HOURS);
                            if (lsRes.getCurrentState() == ScheduleResult.CurrentState.OPENING ||
                                    lsRes.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON ||
                                    lsRes.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON) {
                                isOpen = true;
                            }
                        }
                    }

                    if (!isOpen) {
                        continue;
                    }
                }

                filtered.add(poi);
            }
            filteredPois.postValue(filtered);
        });
    }

    /**
     * 表示範囲にかかる都道府県のPOIをキャッシュ優先で読み込む。
     * @param latLonPoints 逆ジオコーディング対象の座標（各要素 {lat, lon}）。通常は4隅＋中心。
     * @param forceNotify データが既にキャッシュされている場合でも通知を強制するかどうか。
     */
    public void fetchPoisForArea(double[][] latLonPoints, boolean forceNotify) {
        repository.loadPoisForArea(latLonPoints, forceNotify);
    }

    public void fetchPoisForArea(double[][] latLonPoints, boolean forceNotify, String hintPrefName, String hintSubName) {
        repository.loadPoisForArea(latLonPoints, forceNotify, hintPrefName, hintSubName);
    }

    /** 互換用 */
    public void fetchPoisForArea(double[][] latLonPoints) {
        fetchPoisForArea(latLonPoints, false);
    }

    /** 指定都道府県を強制的に再取得する（更新ダイアログの個別更新用） */
    public void refreshPrefecture(int prefCode, String prefName, String subName) {
        repository.refreshPrefecture(prefCode, prefName, subName);
    }

    /** 指定都道府県のローカルキャッシュを削除する。 */
    public void deletePrefectureCache(int prefCode, String subName) {
        repository.deletePrefectureCache(prefCode, subName);
    }

    public void fetchDataDate() {
        repository.fetchDataDate(new PoiRepository.DataDateCallback() {
            @Override
            public void onSuccess(DataDateResponse response) {
                dataDate.postValue(response);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
            }
        });
    }

    public void fetchCityData() {
        repository.fetchCityData();
    }

    public String getPrefectureName(int prefCode) {
        return repository.getPrefectureName(prefCode);
    }

    public void fetchOfficeData() {
        repository.fetchOfficeData();
    }

    public LiveData<DataDateResponse> getDataDate() {
        return dataDate;
    }

    public LiveData<Location> getLocation() {
        return repository.getLocationLiveData();
    }

    public LiveData<String> getCurrentPrefecture() {
        return repository.getCurrentPrefecture();
    }

    public LiveData<String> getCurrentSubArea() {
        return repository.getCurrentSubArea();
    }

    public void startLocationUpdates() {
        repository.startLocationUpdates();
    }

    public void startLocationUpdates(long minTimeMs, float minDistanceM) {
        repository.startLocationUpdates(minTimeMs, minDistanceM);
    }

    public void stopLocationUpdates() {
        repository.stopLocationUpdates();
    }

    /** ローカルに保存済みの都道府県一覧を返す（更新ダイアログ用） */
    public List<PrefMeta> getSavedPrefectures() {
        return repository.getSavedPrefectures();
    }

    public void updateAccessToken(String token) {
        if (repository instanceof PoiRepositoryImpl) {
            ((PoiRepositoryImpl) repository).setAccessToken(token);
        }
    }


    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public void clearError() {
        errorMessage.setValue(null);
        repository.clearError();
    }

    public void clearSuccessMessage() {
        successMessage.setValue(null);
        repository.clearSuccessMessage();
    }

    public void setSuccessMessage(String msg) {
        successMessage.setValue(msg);
    }

    public void setErrorMessage(String msg) {
        errorMessage.setValue(msg);
    }

    public LiveData<Long> getCooldownRemaining() {
        return repository.getCooldownRemaining();
    }

    public LiveData<Boolean> getLoading() {
        return repository.getLoading();
    }

    public LiveData<String> getLoadingStatus() {
        return repository.getLoadingStatus();
    }

    public long getCooldownInterval() {
        return repository.getCooldownInterval();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        filterExecutor.shutdownNow();
    }
}
