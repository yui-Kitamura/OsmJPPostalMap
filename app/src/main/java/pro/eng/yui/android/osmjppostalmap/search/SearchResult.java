package pro.eng.yui.android.osmjppostalmap.search;

import androidx.annotation.NonNull;

public class SearchResult implements Comparable<SearchResult> {
    public enum Type {
        POST_OFFICE,
        POST_BOX,
        PLACE,
        ADDRESS
    }

    private final Type type;
    private final String title;
    private final String subTitle;
    private final double lat;
    private final double lon;
    private final double weight;
    private final Object originalData;

    public SearchResult(Type type, String title, String subTitle, double lat, double lon, double weight, Object originalData) {
        this.type = type;
        this.title = title;
        this.subTitle = subTitle;
        this.lat = lat;
        this.lon = lon;
        this.weight = weight;
        this.originalData = originalData;
    }

    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getSubTitle() { return subTitle; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getWeight() { return weight; }
    public Object getOriginalData() { return originalData; }

    @Override
    public int compareTo(@NonNull SearchResult other) {
        // Higher weight first
        return Double.compare(other.weight, this.weight);
    }
}
