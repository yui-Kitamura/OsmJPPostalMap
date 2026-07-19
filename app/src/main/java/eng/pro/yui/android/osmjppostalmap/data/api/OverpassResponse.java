package eng.pro.yui.android.osmjppostalmap.data.api;

import java.util.List;
import java.util.Map;

public class OverpassResponse {
    public List<Element> elements;

    public static class Element {
        public String type;
        public long id;
        public double lat;
        public double lon;
        public Map<String, String> tags;
    }
}
