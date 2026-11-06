package pro.eng.yui.android.osmjppostalmap.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IndoorMapNoteTest {

    @Test
    public void indoorLocationIsAppendedAsPlainTextOnlyWhenIndoor() {
        assertEquals("住所情報\n屋内にあります",
                IndoorMapNote.appendIndoorLocation("住所情報", true, "屋内にあります"));
        assertEquals("住所情報",
                IndoorMapNote.appendIndoorLocation("住所情報", false, "屋内にあります"));
    }
}