package pro.eng.yui.android.osmjppostalmap.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class UtilTest {

    @Test
    public void testFormatTime() {
        assertEquals("11:15", Util.formatTime("1115"));
        assertEquals("09:30", Util.formatTime("930"));
        assertEquals("00:00", Util.formatTime("000"));
        assertEquals("00:00", Util.formatTime("0000"));
        assertEquals("23:59", Util.formatTime("2359"));
        
        // 無効な時間はそのまま
        assertEquals("2400", Util.formatTime("2400"));
        assertEquals("1260", Util.formatTime("1260"));
        
        // 既にフォーマット済み
        assertEquals("11:15", Util.formatTime("11:15"));
        
        // 全角
        assertEquals("11:15", Util.formatTime("１１１５"));
        
        // 空文字・null
        assertEquals("", Util.formatTime(""));
        assertNull(Util.formatTime(null));
        
        // その他
        assertEquals("abc", Util.formatTime("abc"));
        assertEquals("12", Util.formatTime("12"));
        assertEquals("12345", Util.formatTime("12345"));
    }
}
