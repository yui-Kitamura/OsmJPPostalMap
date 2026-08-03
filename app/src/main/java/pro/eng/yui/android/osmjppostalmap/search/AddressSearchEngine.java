package pro.eng.yui.android.osmjppostalmap.search;

import android.location.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTimes;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpeningHours;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.TextValue;

public class AddressSearchEngine implements SearchEngine {
    private final PoiRepository repository;

    public AddressSearchEngine(PoiRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SearchResult> search(String query, Location currentLoc) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;
        String q = query.trim();

        List<OsmPoi> matchingPois = repository.searchPois(q, false, true);
        for (OsmPoi poi : matchingPois) {
            boolean match = false;
            double weight = 0.0;

            Map<String, String> tags = poi.getTags();
            if (tags == null) continue;

            String amenity = poi.getTag("amenity");
            boolean isPO = "post_office".equals(amenity) || "post_box".equals(amenity);

            String fullAddress = getFullAddress(poi);
            if (fullAddress.equals(q)) {
                match = true;
                weight = isPO ? 60.0 : 30.0;
            } else if (fullAddress.startsWith(q)) {
                match = true;
                weight = isPO ? 55.0 : 25.0;
            } else if (fullAddress.contains(q)) {
                match = true;
                weight = isPO ? 50.0 : 20.0;
            }

            if (!match) {
                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    if (entry.getKey().startsWith("addr:")) {
                        String value = entry.getValue();
                        if (value == null) continue;
                        if (value.equals(q)) {
                            match = true;
                            weight = Math.max(weight, isPO ? 60.0 : 30.0);
                        } else if (value.startsWith(q)) {
                            match = true;
                            weight = Math.max(weight, isPO ? 55.0 : 25.0);
                        } else if (value.contains(q)) {
                            match = true;
                            weight = Math.max(weight, isPO ? 50.0 : 20.0);
                        }
                    }
                }
            }
            
            if (match) {
                // 距離による重みづけ (誘導用)
                if (currentLoc != null && poi.getLat() != 0.0 && poi.getLon() != 0.0) {
                    float[] distResults = new float[1];
                    Location.distanceBetween(currentLoc.getLatitude(), currentLoc.getLongitude(), poi.getLat(), poi.getLon(), distResults);
                    double distanceKm = distResults[0] / 1000.0;
                    weight += 1.0 / (1.0 + distanceKm);
                }

                String title = poi.getTag("name");
                SearchResult.Type resultType = SearchResult.Type.ADDRESS;
                // String amenity = poi.getTag("amenity"); // Moved up

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
                SearchResult result = new SearchResult(resultType, title, subTitle, poi.getLat(), poi.getLon(), weight, poi);
                
                if (resultType == SearchResult.Type.POST_OFFICE || resultType == SearchResult.Type.POST_BOX) {
                    SimpleScheduleParser parser = new SimpleScheduleParser();
                    long now = System.currentTimeMillis();
                    boolean isPostOffice = (resultType == SearchResult.Type.POST_OFFICE);
                    
                    String tagName = isPostOffice ? "opening_hours" : "collection_times";
                    ScheduleParser.TimeType timeType = isPostOffice ? 
                            ScheduleParser.TimeType.OPENING_HOURS : ScheduleParser.TimeType.COLLECTION_TIMES;
                    TextValue tagValue = isPostOffice ? 
                            new OpeningHours(poi.getTag(tagName)) : new CollectionTimes(poi.getTag(tagName));
                    
                    result.setSchedule(parser.parse(tagValue, now, timeType));
                    
                    if (isPostOffice) {
                        String lsTag = poi.getTag("opening_hours:limited_service");
                        if (lsTag != null) {
                            result.setLimitedServiceSchedule(parser.parse(new OpeningHours(lsTag), now, ScheduleParser.TimeType.OPENING_HOURS));
                        }
                    }
                }
                results.add(result);
            }
        }
        return results;
    }

    private String getFullAddress(OsmPoi poi) {
        String address = JpPostalUtil.getAddressText(poi.getTags());
        return address.isEmpty() ? "" : address;
    }
}
