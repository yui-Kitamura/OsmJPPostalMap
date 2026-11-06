package pro.eng.yui.android.osmjppostalmap.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IndoorTagEditorTest {

    @Test
    public void checkedAddsYesAndUncheckedRemovesOnlyIndoorTag() {
        Map<String, String> tags = new HashMap<>();
        tags.put("name", "ポスト");

        IndoorTagEditor.applyCheckState(tags, true);
        assertEquals("yes", tags.get("indoor"));
        assertEquals("ポスト", tags.get("name"));

        IndoorTagEditor.applyCheckState(tags, false);
        assertFalse(tags.containsKey("indoor"));
        assertEquals("ポスト", tags.get("name"));
    }

    @Test
    public void indoorIsTrueOnlyForYesValue() {
        Map<String, String> tags = new HashMap<>();
        assertFalse(IndoorTagEditor.isIndoor(tags));

        tags.put("indoor", "no");
        assertFalse(IndoorTagEditor.isIndoor(tags));

        tags.put("indoor", "yes");
        assertTrue(IndoorTagEditor.isIndoor(tags));
    }

    @Test
    public void newPoiAdditionalTagsIncludeIndoorAndAddressOnly() {
        Map<String, String> tags = new HashMap<>();
        tags.put("indoor", "yes");
        tags.put("addr:housename", "中央ビル");
        tags.put("name", "ポスト");

        Map<String, String> additionalTags = IndoorTagEditor.additionalTagsForNewPoi(tags);

        assertEquals("yes", additionalTags.get("indoor"));
        assertEquals("中央ビル", additionalTags.get("addr:housename"));
        assertFalse(additionalTags.containsKey("name"));
    }
}