package pro.eng.yui.android.osmjppostalmap.domain.model;

import java.io.Serializable;

public class PlaceInfo implements Serializable {
    private int prefCode;
    private String name;
    private Double lat;
    private Double lon;
    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;

    public PlaceInfo(int prefCode, String name, Double lat, Double lon, double minLat, double maxLat, double minLon, double maxLon) {
        this.prefCode = prefCode;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.maxLon = maxLon;
    }

    public int getPrefCode() { return prefCode; }
    public String getName() { return name; }
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public double getMinLat() { return minLat; }
    public double getMaxLat() { return maxLat; }
    public double getMinLon() { return minLon; }
    public double getMaxLon() { return maxLon; }
}
