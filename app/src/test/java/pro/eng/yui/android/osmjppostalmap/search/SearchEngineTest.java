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
        private List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> mockPlaces = new ArrayList<>();
        public void setMockPlaces(List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> places) { this.mockPlaces = places; }

        @Override public List<OsmPoi> getAllCachedPois() { return mockPois; }
        @Override public void fetchCityData() {}
        @Override public List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> searchPlaces(String query) {
            List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> results = new ArrayList<>();
            for (pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo p : mockPlaces) {
                if (p.getName().contains(query) || p.getNameKana().contains(query)) {
                    results.add(p);
                }
            }
            return results;
        }
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
    void testAddressSearchPostBoxType() {
        AddressSearchEngine engine = new AddressSearchEngine(repository);
        List<SearchResult> results = engine.search("山梨市");
        assertEquals(1, results.size());
        assertEquals(SearchResult.Type.POST_BOX, results.get(0).getType());
    }

    @Test
    void testAddressSearchPostOfficeType() {
        AddressSearchEngine engine = new AddressSearchEngine(repository);
        List<SearchResult> results = engine.search("甲府市");
        assertEquals(1, results.size());
        assertEquals(SearchResult.Type.POST_OFFICE, results.get(0).getType());
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
        assertEquals(100.0, results.get(0).getWeight());
        assertEquals("甲府", results.get(0).getTitle());
    }

    @Test
    void testPlaceSearch() {
        List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> mockPlaces = new ArrayList<>();
        mockPlaces.add(new pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo(
                19, "山梨県", "甲府市", "コウフシ", 35.666, 138.568, 35.6, 35.7, 138.5, 138.6
        ));
        ((TestPoiRepository)repository).setMockPlaces(mockPlaces);

        PlaceSearchEngine engine = new PlaceSearchEngine(repository);

        // Exact match
        List<SearchResult> results = engine.search("甲府市");
        assertEquals(1, results.size());
        assertEquals(60.0, results.get(0).getWeight());
        assertEquals("甲府市", results.get(0).getTitle());
        assertTrue(results.get(0).getOriginalData() instanceof pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo);

        // Partial match
        results = engine.search("甲府");
        assertEquals(1, results.size());
        assertEquals(40.0, results.get(0).getWeight());

        // Kana match
        results = engine.search("コウフ");
        assertEquals(1, results.size());
        assertEquals(40.0, results.get(0).getWeight());
    }
    
    @Test
    void testScoringStrategy() {
        // 1. PO Name Match (Partial)
        Map<String, String> tags1 = new HashMap<>();
        tags1.put("amenity", "post_office");
        tags1.put("name", "甲府中央郵便局");
        mockPois.add(new OsmPoi(1L, 35.0, 138.0, "node", tags1, 1L));
        
        // 2. Place Name Match (Exact)
        List<pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo> mockPlaces = new ArrayList<>();
        mockPlaces.add(new pro.eng.yui.android.osmjppostalmap.domain.model.PlaceInfo(
                19, "山梨県", "甲府市", "コウフシ", 35.0, 138.0, 35.0, 35.0, 138.0, 138.0
        ));
        ((TestPoiRepository)repository).setMockPlaces(mockPlaces);
        
        // 3. Address Match (PO)
        Map<String, String> tags3 = new HashMap<>();
        tags3.put("amenity", "post_office");
        tags3.put("name", "竜王郵便局");
        tags3.put("addr:city", "甲府市");
        mockPois.add(new OsmPoi(3L, 35.0, 138.0, "node", tags3, 1L));
        
        String query = "甲府市";
        List<SearchResult> allResults = new ArrayList<>();
        allResults.addAll(new PostOfficeSearchEngine(repository).search(query));
        allResults.addAll(new PlaceSearchEngine(repository).search(query));
        allResults.addAll(new AddressSearchEngine(repository).search(query));
        
        java.util.Collections.sort(allResults);
        
        // Hierarchy: PO Name (80.0) > Place Name (60.0) > PO Address (20.0)
        assertEquals("甲府中央郵便局", allResults.get(0).getTitle());
        assertEquals(80.0, allResults.get(0).getWeight());
        
        assertEquals("甲府市", allResults.get(1).getTitle());
        assertEquals(SearchResult.Type.PLACE, allResults.get(1).getType());
        assertEquals(60.0, allResults.get(1).getWeight());
        
        assertEquals("竜王郵便局", allResults.get(2).getTitle());
        assertEquals(20.0, allResults.get(2).getWeight());
    }
}
