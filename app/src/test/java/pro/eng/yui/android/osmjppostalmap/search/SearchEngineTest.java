package pro.eng.yui.android.osmjppostalmap.search;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateResponse;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import android.location.Location;
import androidx.lifecycle.LiveData;

class SearchEngineTest {
    private PoiRepository repository;
    private List<OsmPoi> mockPois;

    class TestPoiRepository implements PoiRepository {
        @Override public List<OsmPoi> getAllCachedPois() { return mockPois; }
        @Override public LiveData<List<OsmPoi>> getPoisLiveData() { return null; }
        @Override public void loadPoisForArea(double[][] latLonPoints, boolean forceNotify) {}
        @Override public void refreshPrefecture(int prefCode, String prefName, String subName) {}
        @Override public void deletePrefectureCache(int prefCode, String subName) {}
        @Override public List<PrefMeta> getSavedPrefectures() { return null; }
        @Override public void fetchDataDate(DataDateCallback callback) {}
        @Override public void savePoi(OsmPoi poi, String comment, PoiSaveCallback callback) {}
        @Override public void addPostBox(double lat, double lon, String shape, String branch, String postboxRef, String collectionTimes, String note, Map<String, String> addressTags, PoiSaveCallback callback) {}
        @Override public void addNote(double lat, double lon, String text, PoiSaveCallback callback) {}
        @Override public LiveData<String> getError() { return null; }
        @Override public LiveData<String> getSuccessMessage() { return null; }
        @Override public LiveData<Long> getCooldownRemaining() { return null; }
        @Override public LiveData<Boolean> getLoading() { return null; }
        @Override public LiveData<String> getLoadingStatus() { return null; }
        @Override public long getCooldownInterval() { return 0; }
        @Override public LiveData<Location> getLocationLiveData() { return null; }
        @Override public void startLocationUpdates() {}
        @Override public void startLocationUpdates(long minTimeMs, float minDistanceM) {}
        @Override public void stopLocationUpdates() {}
        @Override public LiveData<String> getCurrentPrefecture() { return null; }
        @Override public LiveData<String> getCurrentSubArea() { return null; }
        @Override public void clearError() {}
        @Override public void clearSuccessMessage() {}
    }

    @BeforeEach
    void setUp() {
        mockPois = new ArrayList<>();
        
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("amenity", "post_office");
        tags1.put("name", "甲府郵便局");
        tags1.put("addr:city", "甲府市");
        mockPois.add(new OsmPoi(1L, 35.666, 138.568, "node", tags1, 1L));

        Map<String, String> tags2 = new HashMap<>();
        tags2.put("amenity", "post_box");
        tags2.put("addr:city", "山梨市");
        tags2.put("addr:street", "中央通り");
        mockPois.add(new OsmPoi(2L, 35.684, 138.681, "node", tags2, 1L));

        repository = new TestPoiRepository();
    }

    @Test
    void testPostOfficeSearch() {
        PostOfficeSearchEngine engine = new PostOfficeSearchEngine(repository);
        
        // Exact match
        List<SearchResult> results = engine.search("甲府郵便局");
        assertEquals(1, results.size());
        assertEquals(1.0, results.get(0).getWeight());
        
        // Partial match
        results = engine.search("甲府");
        assertEquals(1, results.size());
        assertEquals(0.5, results.get(0).getWeight());
        
        // No match
        results = engine.search("東京");
        assertEquals(0, results.size());
    }

    @Test
    void testAddressSearch() {
        AddressSearchEngine engine = new AddressSearchEngine(repository);
        
        // Exact match on city
        List<SearchResult> results = engine.search("甲府市");
        assertEquals(1, results.size());
        assertEquals(1.0, results.get(0).getWeight());
        
        // Partial match on street
        results = engine.search("中央");
        assertEquals(1, results.size());
        assertEquals(0.5, results.get(0).getWeight());
    }

    @Test
    void testWeightSorting() {
        Map<String, String> tags3 = new HashMap<>();
        tags3.put("amenity", "post_office");
        tags3.put("name", "甲府北郵便局");
        mockPois.add(new OsmPoi(3L, 35.670, 138.570, "node", tags3, 1L));

        PostOfficeSearchEngine engine = new PostOfficeSearchEngine(repository);
        List<SearchResult> results = engine.search("甲府北");
        
        // "甲府北郵便局" should be partial match (0.5)
        // If I searched "甲府北郵便局", it would be exact.
        
        results = engine.search("甲府");
        // "甲府郵便局" (contains "甲府") -> 0.5
        // "甲府北郵便局" (contains "甲府") -> 0.5
        // Both are partial.
        
        // Let's add an exact match
        Map<String, String> tags4 = new HashMap<>();
        tags4.put("amenity", "post_office");
        tags4.put("name", "甲府");
        mockPois.add(new OsmPoi(4L, 35.660, 138.560, "node", tags4, 1L));
        
        results = engine.search("甲府");
        assertEquals(3, results.size());
        
        // Sort results
        java.util.Collections.sort(results);
        assertEquals(1.0, results.get(0).getWeight());
        assertEquals("甲府", results.get(0).getTitle());
    }
}
