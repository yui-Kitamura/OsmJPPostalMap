package pro.eng.yui.android.osmjppostalmap.search;

import android.location.Location;
import java.util.ArrayList;
import java.util.List;
import pro.eng.yui.android.osmjppostalmap.domain.repository.PoiRepository;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleParser;
import pro.eng.yui.android.osmjppostalmap.schedule.SimpleScheduleParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTimes;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpeningHours;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.TextValue;

public class PostOfficeSearchEngine implements SearchEngine {
    private final PoiRepository repository;

    public PostOfficeSearchEngine(PoiRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SearchResult> search(String query, Location currentLoc) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return results;
        String q = query.trim();

        List<OsmPoi> matchingPois = repository.searchPois(q, true, false);
        for (OsmPoi poi : matchingPois) {
            String amenity = poi.getTag("amenity");
            if (!"post_office".equals(amenity) && !"post_box".equals(amenity)) continue;

            String name = poi.getTag("name");
            String address = JpPostalUtil.getAddressText(poi.getTags());
            if (address.isEmpty()) {
                String pref = poi.getTag("addr:prefecture");
                if (pref != null) {
                    address = pref;
                }
            }
            
            boolean match = false;
            double weight = 0.0;
            
            if (name != null) {
                if (name.equals(q)) {
                    match = true;
                    weight = 80.0;
                } else if (name.startsWith(q)) {
                    match = true;
                    weight = 75.0;
                } else if (name.contains(q)) {
                    match = true;
                    weight = 70.0;
                }
            }
            
            if (!match && !address.isEmpty()) {
                if (address.equals(q)) {
                    match = true;
                    weight = 30.0;
                } else if (address.contains(q)) {
                    match = true;
                    weight = 20.0;
                }
            }

            if (!match) continue;

            // 距離による重みづけ (誘導用)
            if (currentLoc != null && poi.getLat() != 0.0 && poi.getLon() != 0.0) {
                float[] distResults = new float[1];
                Location.distanceBetween(currentLoc.getLatitude(), currentLoc.getLongitude(), poi.getLat(), poi.getLon(), distResults);
                double distanceKm = distResults[0] / 1000.0;
                // 最大1.0のボーナス。近いほど大きく、100kmで約0.01になる
                weight += 1.0 / (1.0 + distanceKm);
            }

            SearchResult.Type type = "post_box".equals(amenity) ? SearchResult.Type.POST_BOX : SearchResult.Type.POST_OFFICE;
            String displayTitle = (name != null) ? name : ("post_box".equals(amenity) ? "郵便ポスト" : "無名郵便局");
            String subTitle = !address.isEmpty() ? address : ("post_box".equals(amenity) ? "郵便ポスト" : "郵便局");
            String reading = Util.getKana(poi);

            SearchResult result = new SearchResult(type, displayTitle, subTitle, reading, poi.getLat(), poi.getLon(), weight, poi);
            
            SimpleScheduleParser parser = new SimpleScheduleParser();
            long now = System.currentTimeMillis();
            boolean isPostOffice = (type == SearchResult.Type.POST_OFFICE);
            
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
            results.add(result);
        }
        return results;
    }
}
