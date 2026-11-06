package pro.eng.yui.android.osmjppostalmap.core;

import java.util.Map;

public final class IndoorBuildingNameValidation {

    private IndoorBuildingNameValidation() {
    }

    public static boolean shouldConfirm(Map<String, String> tags) {
        if (tags == null || !"yes".equals(tags.get("indoor"))) {
            return false;
        }

        String buildingName = tags.get("addr:housename");
        return buildingName == null || buildingName.trim().isEmpty();
    }
}