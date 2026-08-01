package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.List;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

public class PostOfficeSearchEngine implements SearchEngine {
    private final PoiRepository repository;

    public PostOfficeSearchEngine(PoiRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;
        String q = query.trim();

        List<OsmPoi> allPois = repository.getAllCachedPois();
        for (OsmPoi poi : allPois) {
            String amenity = poi.getTag("amenity");
            if (!"post_office".equals(amenity)) continue;

            String name = poi.getTag("name");
            if (name == null) continue;

            if (name.equals(q)) {
                results.add(new SearchResult(SearchResult.Type.POST_OFFICE, name, "郵便局", poi.getLat(), poi.getLon(), 1.0, poi));
            } else if (name.contains(q)) {
                results.add(new SearchResult(SearchResult.Type.POST_OFFICE, name, "郵便局", poi.getLat(), poi.getLon(), 0.5, poi));
            }
        }
        return results;
    }
}
