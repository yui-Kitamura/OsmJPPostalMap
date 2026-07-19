package pro.eng.yui.android.osmjppostalmap.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

import pro.eng.yui.android.osmjppostalmap.data.repository.PoiRepositoryImpl;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;

public class MainViewModel extends ViewModel {

    private final PoiRepository repository;
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<List<OsmPoi>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> filterOpenOnly = new MutableLiveData<>(false);
    private final MutableLiveData<List<OsmPoi>> filteredPois = new MutableLiveData<>();

    public MainViewModel() {
        // TODO: Dependency Injection
        this.repository = new PoiRepositoryImpl();
        
        getPois().observeForever(pois -> applyFilter());
        filterOpenOnly.observeForever(filter -> applyFilter());
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

    private void applyFilter() {
        List<OsmPoi> allPois = repository.getPois(0,0,0,0).getValue(); // 実装上の簡略化
        if (allPois == null) return;

        boolean openOnly = filterOpenOnly.getValue() != null && filterOpenOnly.getValue();
        if (!openOnly) {
            filteredPois.postValue(allPois);
            return;
        }

        List<OsmPoi> filtered = new java.util.ArrayList<>();
        pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser parser = 
            new pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser();
        long now = System.currentTimeMillis();

        for (OsmPoi poi : allPois) {
            String amenity = poi.getTag("amenity");
            String tag = "post_office".equals(amenity) ? "opening_hours" : "collection_times";
            pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult res = 
                parser.parse(poi.getTag(tag), now);
            
            if (res.getCurrentState() == pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult.CurrentState.OPENING ||
                res.getCurrentState() == pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult.CurrentState.OPEN_SOON) {
                filtered.add(poi);
            }
        }
        filteredPois.postValue(filtered);
    }

    public void fetchPois(double minLat, double minLon, double maxLat, double maxLon) {
        repository.getPois(minLat, minLon, maxLat, maxLon);
    }

    public void search(String query) {
        repository.searchPois(query).observeForever(results -> {
            searchResults.postValue(results);
        });
    }

    public LiveData<List<OsmPoi>> getSearchResults() {
        return searchResults;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
}
