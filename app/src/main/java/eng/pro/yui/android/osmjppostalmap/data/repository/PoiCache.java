package eng.pro.yui.android.osmjppostalmap.data.repository;

import java.util.HashMap;
import java.util.Map;

import eng.pro.yui.android.osmjppostalmap.domain.model.OsmPoi;

/**
 * POIの一時的なキャッシュ
 */
public class PoiCache {
    private final Map<String, OsmPoi> cache = new HashMap<>();

    public void put(OsmPoi poi) {
        cache.put(poi.getType() + poi.getId(), poi);
    }

    public OsmPoi get(long id, String type) {
        return cache.get(type + id);
    }
}
