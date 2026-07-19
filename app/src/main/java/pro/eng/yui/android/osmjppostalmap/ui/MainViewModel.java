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

    public MainViewModel() {
        // TODO: Dependency Injection (Dagger/Hilt or manual)
        this.repository = new PoiRepositoryImpl();
    }

    public LiveData<List<OsmPoi>> getPois() {
        return repository.getPois(35.68, 139.76, 35.69, 139.77); // 初期表示用
    }

    public void fetchPois(double minLat, double minLon, double maxLat, double maxLon) {
        repository.getPois(minLat, minLon, maxLat, maxLon);
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
}
