package pro.eng.yui.android.osmjppostalmap.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
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

            String fullAddress = getFullAddress(poi);
            if (fullAddress.equals(q)) {
                match = true;
                weight = 1.0;
            } else if (fullAddress.contains(q)) {
                match = true;
                weight = 0.8; // フルアドレス一致は個別のタグ一致より優先度高めにする
            }

            if (!match) {
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
            }
            
            if (match) {
                String title = poi.getTag("name");
                SearchResult.Type resultType = SearchResult.Type.ADDRESS;
                String amenity = poi.getTag("amenity");

                if (title == null) {
                    if ("post_office".equals(amenity)) {
                        title = "無名郵便局";
                        resultType = SearchResult.Type.POST_OFFICE;
                    } else if ("post_box".equals(amenity)) {
                        title = "郵便ポスト";
                        resultType = SearchResult.Type.POST_BOX;
                    } else {
                        title = "POI (" + poi.getId() + ")";
                    }
                } else {
                    if ("post_office".equals(amenity)) {
                        resultType = SearchResult.Type.POST_OFFICE;
                    } else if ("post_box".equals(amenity)) {
                        resultType = SearchResult.Type.POST_BOX;
                    }
                }
                String subTitle = getFullAddress(poi);
                results.add(new SearchResult(resultType, title, subTitle, poi.getLat(), poi.getLon(), weight, poi));
            }
        }
        return results;
    }

    private String getFullAddress(OsmPoi poi) {
        String address = JpPostalUtil.getAddressText(poi.getTags());
        return address.isEmpty() ? "" : address;
    }
}
