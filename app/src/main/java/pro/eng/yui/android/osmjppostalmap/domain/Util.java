package pro.eng.yui.android.osmjppostalmap.domain;

import android.text.InputFilter;
import android.view.View;
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

    /**
     * 文字列を時刻形式（HH:mm）にフォーマットする。
     * 数字のみの3-4桁の場合、コロンを挿入する。
     * @param s フォーマット対象文字列
     * @return フォーマット後文字列
     */
    public static String formatTime(String s) {
        if (s == null){ return null; }
        String normalized = normalizeNumber(s).trim();
        if (normalized.matches("^\\d{3,4}$")) {
            if (normalized.length() == 3) {
                // e.g. 930 -> 09:30
                int h = Integer.parseInt(normalized.substring(0, 1));
                int m = Integer.parseInt(normalized.substring(1));
                if (m < 60) {
                    return String.format("%02d:%02d", h, m);
                }
            } else if (normalized.length() == 4) {
                // e.g. 1115 -> 11:15
                int h = Integer.parseInt(normalized.substring(0, 2));
                int m = Integer.parseInt(normalized.substring(2));
                if (h < 24 && m < 60) {
                    return String.format("%02d:%02d", h, m);
                }
            }
        }
        return normalized;
    }

    /**
     * EditText にフォーカスが外れた際の時刻パースハンドラを追加する。
     * @param et 対象の EditText
     */
    public static void addTimeParseHandler(EditText et) {
        if (et == null){ return; }
        View.OnFocusChangeListener existingListener = et.getOnFocusChangeListener();
        et.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String text = et.getText().toString();
                String formatted = formatTime(text);
                if (!text.equals(formatted)) {
                    et.setText(formatted);
                }
            }
            if (existingListener != null) {
                existingListener.onFocusChange(v, hasFocus);
            }
        });
    }
}
