package pro.eng.yui.android.osmjppostalmap.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;

public class MainViewModel extends ViewModel {

    private final PoiRepository repository;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> filterOpenOnly = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> filterPostOfficeOnly = new MutableLiveData<>(false);
    private final MutableLiveData<List<OsmPoi>> filteredPois = new MutableLiveData<>();

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

                String tag = (amenity == ScheduleParser.Amenity.POST_OFFICE) ?
                    "opening_hours" : "collection_times";
                ScheduleResult res = parser.parse(poi.getTag(tag), now, amenity);

                if (!(res.getCurrentState() == ScheduleResult.CurrentState.OPENING ||
                    res.getCurrentState() == ScheduleResult.CurrentState.OPENING_BUT_EVENT_SOON ||
                    res.getCurrentState() == ScheduleResult.CurrentState.CLOSING_BUT_OPEN_SOON)) {
                    continue;
                }
            }
            
            filtered.add(poi);
        }
        filteredPois.postValue(filtered);
    }

    /**
     * 表示範囲にかかる都道府県のPOIをキャッシュ優先で読み込む。
     * @param latLonPoints 逆ジオコーディング対象の座標（各要素 {lat, lon}）。通常は4隅＋中心。
     */
    public void fetchPoisForArea(double[][] latLonPoints) {
        repository.loadPoisForArea(latLonPoints);
    }

    /** 指定都道府県を強制的に再取得する（更新ダイアログの個別更新用） */
    public void refreshPrefecture(int prefCode, String prefName) {
        repository.refreshPrefecture(prefCode, prefName);
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
        repository.clearError();
    }

    public void clearSuccessMessage() {
        repository.clearSuccessMessage();
    }

    public LiveData<Long> getCooldownRemaining() {
        return repository.getCooldownRemaining();
    }

    public long getCooldownInterval() {
        return repository.getCooldownInterval();
    }
}
