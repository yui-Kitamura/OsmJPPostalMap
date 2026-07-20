package pro.eng.yui.android.osmjppostalmap.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
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
        // TODO: Dependency Injection
        this.repository = PoiRepositoryImpl.getInstance();
        
        repository.getPois(0,0,0,0).observeForever(pois -> applyFilter());
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
        return repository.getPois(35.68, 139.76, 35.69, 139.77);
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

    private void applyFilter() {
        List<OsmPoi> allPois = repository.getPois(0,0,0,0).getValue(); // 実装上の簡略化
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

    public void fetchPois(double minLat, double minLon, double maxLat, double maxLon) {
        repository.getPois(minLat, minLon, maxLat, maxLon);
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
