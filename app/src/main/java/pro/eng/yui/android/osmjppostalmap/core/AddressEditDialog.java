package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.JpAddress;

/**
 * 住所編集ダイアログ。
 *
 * <p>POI詳細ダイアログ（{@link PoiDetailsDialog}）の住所欄の編集アイコンから開き、
 * {@link JpAddress} を入出力として住所情報を双方向にやり取りする。</p>
 *
 * <ul>
 *   <li>{@code addr:full} が設定済みなら full 1行を表示し、
 *       「タグ階層で入力をする」で各タグの入力欄を展開する。</li>
 *   <li>未設定なら最初から階層入力を展開する。</li>
 *   <li>入力値が {@link JpAddress.Avail#NO} のフィールドは赤下線で警告する。</li>
 * </ul>
 */
public class AddressEditDialog {

    /** 保存ボタン押下時に編集済みの住所を受け取る。 */
    public interface OnAddressSavedListener {
        void onSaved(JpAddress address);
    }

    /** addr:full のタグキー。階層入力に切り替えた場合はこのタグを削除する。 */
    private static final String TAG_FULL = "addr:full";

    /** 階層展開中に確保する、画面高さに対するダイアログ本文の割合。 */
    private static final float HIERARCHY_HEIGHT_RATIO = 0.85f;

    /**
     * JpAddress の各フィールドと、対応するタグキー・アクセサ・検証メソッドの対応。
     *
     * <p>jppostalcore には汎用の検証メソッドが無く、フィールドごとに
     * {@code isXxxAvail(String)} が分かれているためここで束ねる。
     * 専用の検証を持たないフィールドは空判定のみの {@code isAvail(String)} を使う。</p>
     *
     * <p>検証関数の第1引数には、その時点の全入力欄から組み立てたスナップショットが渡る。
     * county / suburb のように他フィールドを見る複合検証はここから参照する。</p>
     */
    private enum Field {
        POSTCODE("addr:postcode", "郵便番号",
                "000-0000 の形式",
                JpAddress::getPostcode, JpAddress::setPostcode,
                (form, v) -> form.isPostcodeAvail(v)),
        PROVINCE("addr:province", "都道府県",
                "「都」「道」「府」「県」で終わる値",
                JpAddress::getProvince, JpAddress::setProvince,
                (form, v) -> form.isProvinceAvail(v)),
        COUNTY("addr:county", "郡",
                "「郡」で終わり、かつ 市区町村 が「町」「村」で終わること",
                JpAddress::getCounty, JpAddress::setCounty,
                AddressEditDialog::countyAvail),
        CITY("addr:city", "市区町村",
                "「市」「区」「町」「村」で終わる値",
                JpAddress::getCity, JpAddress::setCity,
                (form, v) -> form.isCityAvail(v)),
        SUBURB("addr:suburb", "行政区",
                "「区」で終わり、かつ 市区町村 が「市」で終わること（東京都の特別区は 市区町村 へ）",
                JpAddress::getSuburb, JpAddress::setSuburb,
                AddressEditDialog::suburbAvail),
        QUARTER("addr:quarter", "大字",
                "任意の文字列",
                JpAddress::getQuarter, JpAddress::setQuarter,
                (form, v) -> form.isAvail(v)),
        NEIGHBOURHOOD("addr:neighbourhood", "丁目・町名・小字",
                "任意の文字列",
                JpAddress::getNeighbourhood, JpAddress::setNeighbourhood,
                (form, v) -> form.isAvail(v)),
        BLOCK_NUMBER("addr:block_number", "番地・街区符号",
                "任意の文字列",
                JpAddress::getBlockNumber, JpAddress::setBlockNumber,
                (form, v) -> form.isAvail(v)),
        HOUSENUMBER("addr:housenumber", "号・住居番号",
                "任意の文字列",
                JpAddress::getHousenumber, JpAddress::setHousenumber,
                (form, v) -> form.isAvail(v)),
        HOUSENAME("addr:housename", "建物名",
                "任意の文字列",
                JpAddress::getHousename, JpAddress::setHousename,
                (form, v) -> form.isAvail(v)),
        FLOOR("addr:floor", "階",
                "数値（0.5刻み・負数可、末尾の「F」「階」は任意）",
                JpAddress::getFloor, JpAddress::setFloor,
                (form, v) -> form.isAvailFloor(v)),
        ROOM("addr:room", "部屋番号",
                "任意の文字列",
                JpAddress::getRoom, JpAddress::setRoom,
                (form, v) -> form.isAvail(v));

        final String tagKey;
        final String label;
        final String rule;
        final Function<JpAddress, String> getter;
        final BiConsumer<JpAddress, String> setter;
        final BiFunction<JpAddress, String, JpAddress.Avail> validator;

        Field(String tagKey, String label, String rule,
              Function<JpAddress, String> getter,
              BiConsumer<JpAddress, String> setter,
              BiFunction<JpAddress, String, JpAddress.Avail> validator) {
            this.tagKey = tagKey;
            this.label = label;
            this.rule = rule;
            this.getter = getter;
            this.setter = setter;
            this.validator = validator;
        }
    }

    /**
     * 郡の複合検証。郡は町村を包含する単位なので、
     * 市区町村が「町」「村」で終わっていなければ整合しない。
     */
    private static JpAddress.Avail countyAvail(JpAddress form, String value) {
        JpAddress.Avail base = form.isCountyAvail(value);
        if (base != JpAddress.Avail.YES) {
            return base; // UNSET（空欄）と NO はそのまま
        }
        String city = form.getCity();
        boolean cityIsTownOrVillage = city != null && (city.endsWith("町") || city.endsWith("村"));
        return cityIsTownOrVillage ? JpAddress.Avail.YES : JpAddress.Avail.NO;
    }

    /**
     * 行政区の複合検証。政令指定都市の区なので市区町村が「市」で終わる必要がある。
     * 東京都の特別区は市区町村側に入れるため、都道府県が東京都なら常にNGとする。
     */
    private static JpAddress.Avail suburbAvail(JpAddress form, String value) {
        JpAddress.Avail base = form.isSuburbAvail(value);
        if (base != JpAddress.Avail.YES) {
            return base; // UNSET（空欄）と NO はそのまま
        }
        if ("東京都".equals(form.getProvince())) {
            return JpAddress.Avail.NO;
        }
        String city = form.getCity();
        return (city != null && city.endsWith("市")) ? JpAddress.Avail.YES : JpAddress.Avail.NO;
    }

    /**
     * 編集結果の addr:* タグを既存のタグマップへ反映する。
     * 値が無いタグはマップから削除するため、階層入力へ移行した場合は addr:full が消える。
     */
    public static void applyTo(Map<String, String> tags, JpAddress address) {
        putOrRemove(tags, TAG_FULL, address.getFull());
        for (Field field : Field.values()) {
            putOrRemove(tags, field.tagKey, field.getter.apply(address));
        }
    }

    private static void putOrRemove(Map<String, String> tags, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            tags.remove(key);
        } else {
            tags.put(key, value.trim());
        }
    }

    /**
     * @param context  表示コンテキスト
     * @param current  編集対象の現在値
     * @param listener 保存時に編集済みの住所を受け取るリスナ
     */
    public static void show(Context context, JpAddress current, OnAddressSavedListener listener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_address_edit, null);

        TextView currentText = view.findViewById(R.id.address_current);
        View fullRow = view.findViewById(R.id.address_full_row);
        EditText fullInput = view.findViewById(R.id.address_full_input);
        Button expandButton = view.findViewById(R.id.address_expand_hierarchy);
        Button collapseButton = view.findViewById(R.id.address_collapse_hierarchy);
        LinearLayout hierarchy = view.findViewById(R.id.address_hierarchy);
        applyPlaceholderStyle(context, fullInput);

        String currentLabel = current.toString();
        currentText.setText(currentLabel.isEmpty() ? "データなし" : currentLabel);

        boolean hasFull = current.isFullAvail() != JpAddress.Avail.UNSET;
        if (hasFull) {
            fullInput.setText(current.getFull());
        }
        // full が無いPOIは階層入力しか選べないため、full行と展開ボタンごと隠す
        fullRow.setVisibility(hasFull ? View.VISIBLE : View.GONE);
        expandButton.setVisibility(hasFull ? View.VISIBLE : View.GONE);
        hierarchy.setVisibility(hasFull ? View.GONE : View.VISIBLE);

        Map<Field, EditText> inputs = new EnumMap<>(Field.class);
        Map<Field, ColorStateList> defaultTints = new EnumMap<>(Field.class);
        for (Field field : Field.values()) {
            hierarchy.addView(buildRow(context, current, field, inputs, defaultTints));
        }

        // 階層は縦に長いため、展開中はダイアログを画面の高さいっぱいまで伸ばす。
        // 末尾の行が途中で切れて見えることで、下にまだ入力欄が続くことを認知させる。
        // 実際の高さは AlertDialog 側で利用可能な領域に切り詰められるので、大きめの値でよい。
        Runnable stretchForHierarchy = () -> view.setMinimumHeight(
                hierarchy.getVisibility() == View.VISIBLE ? (int)
                        (context.getResources().getDisplayMetrics().heightPixels * HIERARCHY_HEIGHT_RATIO)
                        : 0);

        // county / suburb は他フィールドに依存するため、どの欄を編集しても全体を検証し直す
        Runnable revalidate = () -> {
            JpAddress form = snapshot(inputs);
            for (Field field : Field.values()) {
                EditText input = inputs.get(field);
                if (input == null) { continue; }
                JpAddress.Avail avail = field.validator.apply(form, input.getText().toString());
                input.setBackgroundTintList(avail == JpAddress.Avail.NO
                        ? ColorStateList.valueOf(Color.RED)
                        : defaultTints.get(field));
            }
            // full へ戻せるのは階層が全空欄のときだけ。入力済みの値を黙って捨てさせない
            collapseButton.setVisibility(
                    hierarchy.getVisibility() == View.VISIBLE && isHierarchyEmpty(form)
                            ? View.VISIBLE : View.GONE);
        };
        for (EditText input : inputs.values()) {
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    revalidate.run();
                }
            });
        }
        revalidate.run();
        stretchForHierarchy.run();

        expandButton.setOnClickListener(v -> {
            hierarchy.setVisibility(View.VISIBLE);
            expandButton.setVisibility(View.GONE);
            // 元の full 値は転記の参考に残しつつ、保存対象からは外れることを示す
            fullInput.setEnabled(false);
            fullRow.setAlpha(0.5f);
            revalidate.run();
            stretchForHierarchy.run();
        });

        collapseButton.setOnClickListener(v -> {
            hierarchy.setVisibility(View.GONE);
            // full が無かったPOIでも full 入力へ切り替えられるよう、行ごと出し直す
            fullRow.setVisibility(View.VISIBLE);
            fullInput.setEnabled(true);
            fullRow.setAlpha(1f);
            expandButton.setVisibility(View.VISIBLE);
            revalidate.run();
            stretchForHierarchy.run();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("住所編集ダイアログ")
                .setView(view)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.show();

        // 検証NGのとき確認を挟むため、既定の自動 dismiss を上書きする
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean useHierarchy = hierarchy.getVisibility() == View.VISIBLE;
            JpAddress edited = useHierarchy ? snapshot(inputs) : fullOnly(fullInput);

            // 起動時から値が変わっていなければ保存すべきものが無い。
            // リスナを呼ぶと詳細ダイアログ側が「編集画面で保存」を促してしまうため、黙って閉じる
            if (isUnchanged(current, edited)) {
                dialog.dismiss();
                return;
            }

            // addr:full は空判定のみでNGにならないため、full入力時は検証対象が無い
            List<String> invalid = useHierarchy ? collectInvalid(inputs, edited) : new ArrayList<>();

            if (invalid.isEmpty()) {
                listener.onSaved(edited);
                dialog.dismiss();
                return;
            }

            new MaterialAlertDialogBuilder(context)
                    .setMessage("入力値検証に失敗しています。続行しますか？\n\n"
                            + String.join("\n", invalid))
                    .setNegativeButton("やり直す", null)
                    .setPositiveButton("保存", (d, which) -> {
                        listener.onSaved(edited);
                        dialog.dismiss();
                    })
                    .show();
        });
    }

    /** ラベルと入力欄からなる1フィールド分の行を作る。 */
    private static View buildRow(Context context, JpAddress current, Field field,
                                 Map<Field, EditText> inputs,
                                 Map<Field, ColorStateList> defaultTints) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 4), 0, dp(context, 4));

        TextView labelView = new TextView(context);
        labelView.setText(field.label);
        labelView.setTextSize(13f);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        EditText input = new EditText(context);
        input.setHint(field.tagKey);
        applyPlaceholderStyle(context, input);
        input.setTextSize(14f);
        input.setSingleLine(true);
        input.setText(field.getter.apply(current));
        input.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));

        inputs.put(field, input);
        defaultTints.put(field, input.getBackgroundTintList());
        row.addView(labelView);
        row.addView(input);
        return row;
    }

    /**
     * placeholder を斜体＋淡色にして、入力済みの値と視覚的に区別する。
     *
     * <p>{@code android:textStyle="italic"} は入力済みの文字まで斜体にしてしまうため、
     * ヒント文字列に {@link StyleSpan} を張って placeholder だけを斜体にする。</p>
     */
    private static void applyPlaceholderStyle(Context context, EditText input) {
        CharSequence hint = input.getHint();
        if (hint == null) { return; }
        SpannableString styled = new SpannableString(hint.toString());
        styled.setSpan(new StyleSpan(Typeface.ITALIC), 0, styled.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        input.setHint(styled);
        input.setHintTextColor(ContextCompat.getColor(context, R.color.input_placeholder));
    }

    /** 階層の入力欄から JpAddress を組み立てる。検証にも保存にも使う。 */
    private static JpAddress snapshot(Map<Field, EditText> inputs) {
        // setFull() は階層フィールドを全クリアするため、階層入力時は full を捨てる
        JpAddress address = new JpAddress();
        for (Map.Entry<Field, EditText> entry : inputs.entrySet()) {
            entry.getKey().setter.accept(address, entry.getValue().getText().toString());
        }
        return address;
    }

    /** 階層フィールドが1つも埋まっていないか。full 入力へ戻せるかの判定に使う。 */
    private static boolean isHierarchyEmpty(JpAddress address) {
        for (Field field : Field.values()) {
            String value = field.getter.apply(address);
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** full のみを持つ JpAddress を組み立てる。 */
    private static JpAddress fullOnly(EditText fullInput) {
        JpAddress address = new JpAddress();
        address.setFull(fullInput.getText().toString());
        return address;
    }

    /**
     * ダイアログ起動時の値と編集結果を、full と各階層フィールドで突き合わせる。
     *
     * @return 全フィールドが一致していれば true
     */
    private static boolean isUnchanged(JpAddress before, JpAddress after) {
        if (!sameValue(before.getFull(), after.getFull())) {
            return false;
        }
        for (Field field : Field.values()) {
            if (!sameValue(field.getter.apply(before), field.getter.apply(after))) {
                return false;
            }
        }
        return true;
    }

    /**
     * null・空文字・前後空白のみの差は「同じ」とみなす。
     * {@link #putOrRemove} が trim して空ならタグを消す仕様と揃えている。
     */
    private static boolean sameValue(String a, String b) {
        return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
    }

    /** 検証NGのフィールドと、その入力ルールを集める。 */
    private static List<String> collectInvalid(Map<Field, EditText> inputs, JpAddress form) {
        List<String> invalid = new ArrayList<>();
        for (Field field : Field.values()) {
            EditText input = inputs.get(field);
            if (input == null) { continue; }
            if (field.validator.apply(form, input.getText().toString()) == JpAddress.Avail.NO) {
                invalid.add("・" + field.label + " (" + field.tagKey + ")\n    " + field.rule);
            }
        }
        return invalid;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
