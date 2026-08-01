package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

public class AddressSearchEngine implements SearchEngine {
    private final PoiRepository repository;

    public AddressSearchEngine(PoiRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;
        String q = query.trim();

        List<OsmPoi> allPois = repository.getAllCachedPois();
        for (OsmPoi poi : allPois) {
            boolean match = false;
            double weight = 0.0;

            Map<String, String> tags = poi.getTags();
            if (tags == null) continue;

            for (Map.Entry<String, String> entry : tags.entrySet()) {
                if (entry.getKey().startsWith("addr:")) {
                    String value = entry.getValue();
                    if (value == null) continue;
                    if (value.equals(q)) {
                        match = true;
                        weight = Math.max(weight, 1.0);
                    } else if (value.contains(q)) {
                        match = true;
                        weight = Math.max(weight, 0.5);
                    }
                }
            }
            
            if (match) {
                String title = poi.getTag("name");
                if (title == null) {
                    String amenity = poi.getTag("amenity");
                    if ("post_office".equals(amenity)) {
                        title = "無名郵便局";
                    } else if ("post_box".equals(amenity)) {
                        title = "郵便ポスト";
                    } else {
                        title = "POI (" + poi.getId() + ")";
                    }
                }
                String subTitle = getFullAddress(poi);
                results.add(new SearchResult(SearchResult.Type.ADDRESS, title, subTitle, poi.getLat(), poi.getLon(), weight, poi));
            }
        }
        return results;
    }

    private String getFullAddress(OsmPoi poi) {
        String pref = poi.getTag("addr:province");
        if (pref == null) pref = poi.getTag("addr:prefecture");
        String city = poi.getTag("addr:city");
        String suburb = poi.getTag("addr:suburb");
        String street = poi.getTag("addr:street");
        String housenumber = poi.getTag("addr:housenumber");
        
        StringBuilder sb = new StringBuilder();
        if (pref != null) sb.append(pref);
        if (city != null) sb.append(city);
        if (suburb != null) sb.append(suburb);
        if (street != null) sb.append(street);
        if (housenumber != null) sb.append(housenumber);
        
        return sb.toString();
    }
}
