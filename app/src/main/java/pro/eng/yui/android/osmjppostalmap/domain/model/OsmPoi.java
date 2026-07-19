package pro.eng.yui.android.osmjppostalmap.domain.model;

import java.util.Map;

/**
 * OpenStreetMapのPOIを表す基底モデル
 */
public class OsmPoi {
    private final long id;
    private final double lat;
    private final double lon;
    private final String type; // "node" or "way"
    private final Map<String, String> tags;

    public OsmPoi(long id, double lat, double lon, String type, Map<String, String> tags) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        this.type = type;
        this.tags = tags;
    }

    public long getId() { return id; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getType() { return type; }
    public Map<String, String> getTags() { return tags; }
    
    public String getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }
}
