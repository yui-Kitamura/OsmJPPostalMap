package pro.eng.yui.android.osmjppostalmap.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndoorBuildingNameValidationTest {

    @Test
    public void confirmsOnlyWhenIndoorYesHasNoBuildingName() {
        Map<String, String> tags = new HashMap<>();
        tags.put("indoor", "yes");
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("addr:housename", "");
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("addr:housename", " \t ");
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("addr:housename", "中央ビル");
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));
    }

    @Test
    public void ignoresNonIndoorAndMissingIndoorTags() {
        Map<String, String> tags = new HashMap<>();
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("addr:housename", "");
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("indoor", "no");
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));
    }

    @Test
    public void checksTheCurrentAddressTagsAfterTheyAreUpdated() {
        Map<String, String> tags = new HashMap<>();
        tags.put("indoor", "yes");
        tags.put("addr:full", "東京都新宿区...");
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));

        tags.put("addr:housename", "新宿ビル");
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));
    }

    @Test
    public void validationTracksIndoorCheckboxChanges() {
        Map<String, String> tags = new HashMap<>();
        IndoorTagEditor.applyCheckState(tags, true);
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));

        IndoorTagEditor.applyCheckState(tags, false);
        assertFalse(IndoorBuildingNameValidation.shouldConfirm(tags));

        IndoorTagEditor.applyCheckState(tags, true);
        assertTrue(IndoorBuildingNameValidation.shouldConfirm(tags));
    }
}