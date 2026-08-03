package pro.eng.yui.android.osmjppostalmap.search;

import androidx.annotation.NonNull;

import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

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
    private final Double lat;
    private final Double lon;
    private final double weight;
    private final Object originalData;
    private ScheduleResult schedule;
    private ScheduleResult limitedServiceSchedule;

    public SearchResult(Type type, String title, String subTitle, Double lat, Double lon, double weight, Object originalData) {
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
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public double getWeight() { return weight; }
    public Object getOriginalData() { return originalData; }

    public ScheduleResult getSchedule() {
        return schedule;
    }

    public void setSchedule(ScheduleResult schedule) {
        this.schedule = schedule;
    }

    public ScheduleResult getLimitedServiceSchedule() {
        return limitedServiceSchedule;
    }

    public void setLimitedServiceSchedule(ScheduleResult limitedServiceSchedule) {
        this.limitedServiceSchedule = limitedServiceSchedule;
    }

    @Override
    public int compareTo(@NonNull SearchResult other) {
        // Higher weight first
        return Double.compare(other.weight, this.weight);
    }
}
