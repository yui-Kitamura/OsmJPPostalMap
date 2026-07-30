package pro.eng.yui.android.osmjppostalmap.domain;

import android.text.InputFilter;
import android.widget.EditText;

/**
 * 共通ユーティリティクラス。
 */
public class Util {

    /** 全角数字を半角数字に変換するフィルタ。 */
    public static final InputFilter NUMBER_CONVERSION_FILTER = (source, start, end, dest, dstart, dend) -> {
        boolean changed = false;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - 0xFEE0));
                changed = true;
            } else {
                sb.append(c);
            }
        }
        return changed ? sb.toString() : null;
    };

    /**
     * 文字列内の全角数字（０-９）を半角（0-9）に変換する。
     * @param s 変換対象文字列
     * @return 変換後文字列。null の場合は null。
     */
    public static String normalizeNumber(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * EditText に全角数字→半角変換フィルタを追加する。
     * @param et 対象の EditText
     */
    public static void addNumberFilter(EditText et) {
        if (et == null) return;
        InputFilter[] current = et.getFilters();
        if (current == null) {
            et.setFilters(new InputFilter[]{NUMBER_CONVERSION_FILTER});
            return;
        }
        InputFilter[] newFilters = new InputFilter[current.length + 1];
        System.arraycopy(current, 0, newFilters, 0, current.length);
        newFilters[current.length] = NUMBER_CONVERSION_FILTER;
        et.setFilters(newFilters);
    }
}
