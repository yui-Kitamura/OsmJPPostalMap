package pro.eng.yui.android.osmjppostalmap.data.remote;

import java.util.List;

public class DataDateResponse {
    private String lastUpdated;
    private List<PrefectureDate> prefectures;

    public String getLastUpdated() {
        return lastUpdated;
    }

    public List<PrefectureDate> getPrefectures() {
        return prefectures;
    }

    public static class PrefectureDate {
        private String lastModified;
        private String name;

        public String getLastModified() {
            return lastModified;
        }

        public String getName() {
            return name;
        }
    }
}
