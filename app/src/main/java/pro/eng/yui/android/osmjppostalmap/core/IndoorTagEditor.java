package pro.eng.yui.android.osmjppostalmap.core;

import java.util.HashMap;
import java.util.Map;

public final class IndoorTagEditor {

    private IndoorTagEditor() {
    }

    public static boolean isIndoor(Map<String, String> tags) {
        return tags != null && "yes".equals(tags.get("indoor"));
    }

    public static void applyCheckState(Map<String, String> tags, boolean checked) {
        if (tags == null) {
            return;
        }

        if (checked) {
            tags.put("indoor", "yes");
        } else {
            tags.remove("indoor");
        }
    }

    public static Map<String, String> additionalTagsForNewPoi(Map<String, String> tags) {
        Map<String, String> additionalTags = new HashMap<>();
        if (tags == null) {
            return additionalTags;
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (entry.getKey().startsWith("addr:") || "indoor".equals(entry.getKey())) {
                additionalTags.put(entry.getKey(), entry.getValue());
            }
        }
        return additionalTags;
    }
}