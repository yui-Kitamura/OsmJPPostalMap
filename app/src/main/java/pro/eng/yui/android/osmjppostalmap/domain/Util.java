package pro.eng.yui.android.osmjppostalmap.domain;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import androidx.core.content.ContextCompat;
import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OsmPoi;

/**
 * 共通ユーティリティクラス。
 */
public class Util {

    public static final String TAG_NAME_KANA = "name:ja-Hira";


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
                applyTimeFormat(et);
            }
            if (existingListener != null) {
                existingListener.onFocusChange(v, hasFocus);
            }
        });
    }

    /**
     * EditText の入力内容を時刻形式にフォーマットして反映する。
     * @param et 対象の EditText
     */
    public static void applyTimeFormat(EditText et) {
        if (et == null){ return; }
        String text = et.getText().toString();
        String formatted = formatTime(text);
        if (!text.equals(formatted)) {
            et.setText(formatted);
        }
    }

    /**
     * EditText にクリアボタンと復元ボタンの機能を追加する。
     * @param et 対象の EditText
     */
    public static void addClearRestoreHandler(final EditText et) {
        if (et == null) return;

        final Drawable clearIcon = ContextCompat.getDrawable(et.getContext(), R.drawable.ic_clear_24);
        final Drawable undoIcon = ContextCompat.getDrawable(et.getContext(), R.drawable.ic_undo_24);

        if (clearIcon != null) clearIcon.setTint(et.getCurrentTextColor());
        if (undoIcon != null) undoIcon.setTint(et.getCurrentTextColor());

        final Runnable updateIcon = () -> {
            String text = et.getText().toString();
            String restoreValue = (String) et.getTag(R.id.tag_restore_value);
            if (!text.isEmpty()) {
                et.setCompoundDrawablesWithIntrinsicBounds(null, null, clearIcon, null);
            } else if (restoreValue != null && !restoreValue.isEmpty()) {
                et.setCompoundDrawablesWithIntrinsicBounds(null, null, undoIcon, null);
            } else {
                et.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        };

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                String restoreValue = (String) et.getTag(R.id.tag_restore_value);
                if (!text.isEmpty() && !text.equals(restoreValue)) {
                    et.setTag(R.id.tag_restore_value, null);
                }
                updateIcon.run();
            }
        });

        View.OnTouchListener existingListener = null; // No way to get existing listener easily in Android < 29
        et.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable icon = et.getCompoundDrawables()[2];
                if (icon != null) {
                    int iconWidth = icon.getBounds().width();
                    int iconStart = et.getWidth() - et.getPaddingRight() - iconWidth;
                    if (event.getX() >= iconStart) {
                        String text = et.getText().toString();
                        String restoreValue = (String) et.getTag(R.id.tag_restore_value);
                        if (!text.isEmpty()) {
                            et.setTag(R.id.tag_restore_value, text);
                            et.setText("");
                        } else if (restoreValue != null && !restoreValue.isEmpty()) {
                            et.setText(restoreValue);
                            et.setTag(R.id.tag_restore_value, null);
                        }
                        return true;
                    }
                }
            }
            return false;
        });
    }
    
    /**
     * ルビ（読み仮名）付きの Spannable を作成する。
     * HTML の <ruby> っぽく表示するため、読み仮名を上に小さく表示する 2 行組みの Spannable を返す。
     * @param base 漢字などのベース文字列
     * @param ruby 読み仮名
     * @param baseTextSize ベース文字列のサイズ(px)
     * @return Spannable
     */
    public static CharSequence getRubySpannable(String base, String ruby, float baseTextSize) {
        if (ruby == null || ruby.isEmpty()) return base;
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(ruby).append("\n").append(base);
        
        int rubyEnd = ruby.length();
        
        // 読み仮名部分を小さくする (ベースの 60% 程度)
        ssb.setSpan(new AbsoluteSizeSpan((int)(baseTextSize * 0.6f)), 0, rubyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 読み仮名部分を細字にする
        ssb.setSpan(new StyleSpan(Typeface.NORMAL), 0, rubyEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        return ssb;
    }

    /**
     * OsmPoi から読み仮名を取得する。
     * name:ja-Hira タグを優先し、存在しない場合はキャッシュ用の kana タグをフォールバックとして使用する。
     * @param poi 対象の POI
     * @return 読み仮名。存在しない場合は null。
     */
    public static String getKana(OsmPoi poi) {
        if (poi == null) return null;
        return poi.getTag(TAG_NAME_KANA);
    }

    /**
     * 読み仮名が有効かどうかを判定する（ひらがなと長音符のみ）。
     * @param s 判定対象文字列
     * @return 有効な場合は true
     */
    public static boolean isValidReading(String s) {
        if (s == null || s.isEmpty()) return true;
        return s.matches("^[\\u3041-\\u309F\\u30FC]*$");
    }
}
