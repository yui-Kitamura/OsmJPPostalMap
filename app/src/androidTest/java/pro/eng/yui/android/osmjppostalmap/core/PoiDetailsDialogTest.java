package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class PoiDetailsDialogTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void titleShowsBranchAndReferenceInSmallerText() {
        SpannableString title = PoiDetailsDialog.buildPostBoxTitle(context, "新宿郵便局", "00123");

        assertEquals("郵便ポスト（新宿郵便局：00123）", title.toString());
        assertManagementInfoIsSmaller(title);
    }

    @Test
    public void titleShowsOnlyAvailableValue() {
        assertEquals("郵便ポスト（新宿郵便局）", PoiDetailsDialog.buildPostBoxTitle(context, "新宿郵便局", "").toString());
        assertEquals("郵便ポスト（00123）", PoiDetailsDialog.buildPostBoxTitle(context, null, "00123").toString());
    }

    @Test
    public void titleOmitsMissingOrBlankInformation() {
        SpannableString title = PoiDetailsDialog.buildPostBoxTitle(context, "  ", null);

        assertEquals("郵便ポスト", title.toString());
        assertEquals(0, title.getSpans(0, title.length(), RelativeSizeSpan.class).length);
    }

    @Test
    public void titlePreservesLongValuesAndLeadingZeroes() {
        String branch = "とても長い管理郵便局名";
        String ref = "00000000000000000001";

        assertEquals("郵便ポスト（" + branch + "：" + ref + "）", PoiDetailsDialog.buildPostBoxTitle(context, branch, ref).toString());
    }

    private void assertManagementInfoIsSmaller(SpannableString title) {
        RelativeSizeSpan[] spans = title.getSpans(0, title.length(), RelativeSizeSpan.class);
        assertEquals(1, spans.length);
        assertEquals("郵便ポスト".length(), title.getSpanStart(spans[0]));
        assertEquals(title.length(), title.getSpanEnd(spans[0]));
        assertEquals(0.8f, spans[0].getSizeChange(), 0.0f);
    }
}
