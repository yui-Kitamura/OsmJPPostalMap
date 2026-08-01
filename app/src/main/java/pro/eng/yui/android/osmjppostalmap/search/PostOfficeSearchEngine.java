package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.List;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
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
            if (!"post_office".equals(amenity) && !"post_box".equals(amenity)) continue;

            String name = poi.getTag("name");
            String address = JpPostalUtil.getAddressText(poi.getTags());
            
            boolean match = false;
            double weight = 0.0;
            
            if (name != null) {
                if (name.equals(q)) {
                    match = true;
                    weight = 1.0;
                } else if (name.contains(q)) {
                    match = true;
                    weight = 0.5;
                }
            }
            
            if (!match && !address.isEmpty()) {
                if (address.equals(q)) {
                    match = true;
                    weight = 0.8;
                } else if (address.contains(q)) {
                    match = true;
                    weight = 0.4;
                }
            }

            if (!match) continue;

            SearchResult.Type type = "post_box".equals(amenity) ? SearchResult.Type.POST_BOX : SearchResult.Type.POST_OFFICE;
            String displayTitle = (name != null) ? name : ("post_box".equals(amenity) ? "郵便ポスト" : "無名郵便局");
            String subTitle = !address.isEmpty() ? address : ("post_box".equals(amenity) ? "郵便ポスト" : "郵便局");

            results.add(new SearchResult(type, displayTitle, subTitle, poi.getLat(), poi.getLon(), weight, poi));
        }
        return results;
    }
}
