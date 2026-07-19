package pro.eng.yui.android.osmjppostalmap.data.api;

import java.util.List;
import java.util.Map;

public class OverpassResponse {
    public List<Element> elements;

    public static class Element {
        public String type;
        public long id;
        public double lat;
        public double lon;
        public Center center;
        public Map<String, String> tags;
    }

    public static class Center {
        public double lat;
        public double lon;
    }
}
