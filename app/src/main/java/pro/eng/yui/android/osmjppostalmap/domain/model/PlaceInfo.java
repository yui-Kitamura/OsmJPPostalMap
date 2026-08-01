package pro.eng.yui.android.osmjppostalmap.domain.model;

import java.io.Serializable;

public class PlaceInfo implements Serializable {
    private int prefCode;
    private String isIn;
    private String name;
    private String nameKana;
    private double lat;
    private double lon;
    private double minLat;
    private double maxLat;
    private double minLon;
    private double maxLon;

    public PlaceInfo(int prefCode, String isIn, String name, String nameKana, double lat, double lon, double minLat, double maxLat, double minLon, double maxLon) {
        this.prefCode = prefCode;
        this.isIn = isIn;
        this.name = name;
        this.nameKana = nameKana;
        this.lat = lat;
        this.lon = lon;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLon = minLon;
        this.maxLon = maxLon;
    }

    public int getPrefCode() { return prefCode; }
    public String getIsIn() { return isIn; }
    public String getName() { return name; }
    public String getNameKana() { return nameKana; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getMinLat() { return minLat; }
    public double getMaxLat() { return maxLat; }
    public double getMinLon() { return minLon; }
    public double getMaxLon() { return maxLon; }
}
