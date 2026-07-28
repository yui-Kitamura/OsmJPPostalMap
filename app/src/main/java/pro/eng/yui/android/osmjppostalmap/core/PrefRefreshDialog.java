package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.oss.osm.lib.jppostalcore.JpPostalUtil;
import pro.eng.yui.android.osmjppostalmap.data.remote.DataDateResponse;
import pro.eng.yui.android.osmjppostalmap.domain.model.PrefMeta;
import pro.eng.yui.android.osmjppostalmap.ui.MainViewModel;

/**
 * 再取得ボタンから開く更新ダイアログ。
 *
 * <ul>
 *   <li>「表示範囲を取得」… 現在の表示範囲にかかる都道府県をまとめて取得する。</li>
 *   <li>保存済みの都道府県ごとに最終更新日を表示し、個別に更新できる。</li>
 * </ul>
 */
public class PrefRefreshDialog {

    /**
     * @param context       表示コンテキスト
     * @param viewModel     取得/更新の窓口
     * @param onRefreshArea 「表示範囲を取得」押下時の処理（通常は MainActivity#updatePois）
     */
    public static void show(Context context, MainViewModel viewModel, String currentPref, Runnable onRefreshArea) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_pref_refresh, null);
        LinearLayout container = view.findViewById(R.id.pref_list_container);
        Button areaButton = view.findViewById(R.id.refresh_area_button);
        TextView emptyText = view.findViewById(R.id.empty_text);
        TextView label = view.findViewById(R.id.pref_list_label);
        label.setText("都道府県別データ");

        // Add a loading indicator
        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        int margin = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        lp.setMargins(0, margin, 0, margin);
        progressBar.setLayoutParams(lp);
        container.addView(progressBar);

        CompletableFuture.runAsync(() -> {
            try {
                List<PrefMeta> savedMetas = viewModel.getSavedPrefectures();
                Map<String, PrefMeta> savedMap = new HashMap<>();
                for (PrefMeta m : savedMetas) {
                    String key = m.getName() + (m.getSubName() == null ? "" : ":" + m.getSubName());
                    savedMap.put(key, m);
                }

                Map<String, Date> remoteDates = new HashMap<>();
                DataDateResponse remoteData = viewModel.getDataDate().getValue();
                SimpleDateFormat remoteSdf = new SimpleDateFormat("yyyy/MM/dd'T'HH:mm:ss", Locale.JAPAN);
                if (remoteData != null && remoteData.getPrefectures() != null) {
                    for (DataDateResponse.PrefectureDate pd : remoteData.getPrefectures()) {
                        try {
                            Date date = remoteSdf.parse(pd.getLastModified());
                            if (date != null) {
                                remoteDates.put(pd.getName(), date);
                            }
                        } catch (ParseException ignored) {
                        }
                    }
                }

                String json = JpPostalUtil.getRawPrefecturesJson().join();
                Map<String, Integer> allPrefCodes = JpPostalUtil.getPrefectures().join();
                List<PrefInfo> allItems = new ArrayList<>();

                if (json != null && !json.isEmpty()) {
                    JSONObject root = new JSONObject(json);
                    Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        String prefName = keys.next();
                        JSONObject prefObj = root.getJSONObject(prefName);
                        Integer code = allPrefCodes.get(prefName);
                        if (code == null) continue;

                        PrefInfo pi = new PrefInfo(code, prefName);
                        if (prefObj.has("sub")) {
                            JSONObject subObj = prefObj.getJSONObject("sub");
                            Iterator<String> subKeys = subObj.keys();
                            while (subKeys.hasNext()) {
                                String subName = subKeys.next();
                                pi.subNames.add(subName);
                            }
                            Collections.sort(pi.subNames);
                        }
                        allItems.add(pi);
                    }
                } else {
                    // Fallback
                    for (Map.Entry<String, Integer> entry : allPrefCodes.entrySet()) {
                        allItems.add(new PrefInfo(entry.getValue(), entry.getKey()));
                    }
                }

                // ソート: 現在地 > 取込済みがある県 > 全く未取得。各グループ内ではコード順
                Collections.sort(allItems, (o1, o2) -> {
                    boolean isCurr1 = o1.name.equals(currentPref);
                    boolean isCurr2 = o2.name.equals(currentPref);
                    if (isCurr1 && !isCurr2) return -1;
                    if (!isCurr1 && isCurr2) return 1;

                    boolean s1 = hasAnySaved(o1, savedMap);
                    boolean s2 = hasAnySaved(o2, savedMap);
                    if (s1 && !s2) return -1;
                    if (!s1 && s2) return 1;
                    return Integer.compare(o1.code, o2.code);
                });

                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        container.removeView(progressBar);
                        if (allItems.isEmpty()) {
                            emptyText.setVisibility(View.VISIBLE);
                        } else {
                            emptyText.setVisibility(View.GONE);
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN);
                            for (PrefInfo pi : allItems) {
                                boolean isCurrent = pi.name.equals(currentPref);
                                if (pi.subNames.isEmpty()) {
                                    // 通常の県
                                    PrefMeta meta = savedMap.get(pi.name);
                                    if (meta == null) meta = new PrefMeta(pi.code, pi.name, 0);
                                    Date sourceUpdatedAt = remoteDates.get(pi.name);
                                    boolean isSaved = savedMap.containsKey(pi.name);
                                    boolean hasUpdate = isSaved && sourceUpdatedAt != null && meta.getLastUpdated() < sourceUpdatedAt.getTime();
                                    container.addView(buildRow(context, viewModel, meta, sdf, sourceUpdatedAt, hasUpdate, isSaved, isCurrent));
                                } else {
                                    // サブ領域あり
                                    container.addView(buildHierarchyRow(context, viewModel, pi, savedMap, remoteDates, sdf, isCurrent));
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("データの更新");
        builder.setView(view);
        builder.setNeutralButton("更新状況", (d, which) -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://yui-kitamura.github.io/OsmJpPostalMapDataSource/"));
            context.startActivity(intent);
        });
        builder.setPositiveButton("閉じる", null);
        AlertDialog dialog = builder.create();

        areaButton.setOnClickListener(v -> {
            if (onRefreshArea != null) {
                onRefreshArea.run();
            }
            Toast.makeText(context, "表示範囲を取得しています...", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private static boolean hasAnySaved(PrefInfo pi, Map<String, PrefMeta> savedMap) {
        if (pi.subNames.isEmpty()) {
            return savedMap.containsKey(pi.name);
        }
        for (String sub : pi.subNames) {
            if (savedMap.containsKey(pi.name + ":" + sub)) return true;
        }
        return false;
    }

    private static class PrefInfo {
        int code;
        String name;
        List<String> subNames = new ArrayList<>();
        PrefInfo(int code, String name) { this.code = code; this.name = name; }
    }

    private static View buildHierarchyRow(Context context, MainViewModel viewModel, PrefInfo pi,
                                          Map<String, PrefMeta> savedMap, Map<String, Date> remoteDates, SimpleDateFormat sdf, boolean isCurrent) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView label = new TextView(context);
        label.setText("▶ " + pi.name);
        if (isCurrent) {
            label.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_mylocation, 0, 0, 0);
            label.setCompoundDrawablePadding(16);
        }
        label.setTextSize(16f);
        label.setPadding(0, 24, 0, 8);
        label.setClickable(true);
        label.setBackgroundResource(android.R.drawable.list_selector_background);

        LinearLayout subContainer = new LinearLayout(context);
        subContainer.setOrientation(LinearLayout.VERTICAL);
        subContainer.setPadding(32, 0, 0, 0);
        subContainer.setVisibility(View.GONE);

        label.setOnClickListener(v -> {
            if (subContainer.getVisibility() == View.VISIBLE) {
                subContainer.setVisibility(View.GONE);
                label.setText("▶ " + pi.name);
            } else {
                subContainer.setVisibility(View.VISIBLE);
                label.setText("▼ " + pi.name);
            }
        });

        for (String sub : pi.subNames) {
            String key = pi.name + ":" + sub;
            PrefMeta meta = savedMap.get(key);
            if (meta == null) meta = new PrefMeta(pi.code, pi.name, sub, 0);

            // サブ領域の更新日は現状親と同じか、個別にあればそれを使う（現状は親単位で管理されている想定）
            Date sourceUpdatedAt = remoteDates.get(pi.name); 
            boolean isSaved = savedMap.containsKey(key);
            boolean hasUpdate = isSaved && sourceUpdatedAt != null && meta.getLastUpdated() < sourceUpdatedAt.getTime();

            subContainer.addView(buildRow(context, viewModel, meta, sdf, sourceUpdatedAt, hasUpdate, isSaved, false));
        }

        root.addView(label);
        root.addView(subContainer);
        return root;
    }

    private static View buildRow(Context context, MainViewModel viewModel, PrefMeta meta,
                                 SimpleDateFormat sdf, Date sourceUpdatedAt, boolean hasUpdate, boolean isSaved, boolean isCurrent) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 12, 0, 12);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        String displayName = meta.getSubName() == null ? meta.getName() : meta.getSubName();
        nameView.setText(displayName);
        if (isCurrent && meta.getSubName() == null) {
            nameView.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_mylocation, 0, 0, 0);
            nameView.setCompoundDrawablePadding(16);
        }
        nameView.setTextSize(16f);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button actionButton = new Button(context);
        actionButton.setText(isSaved ? "更新" : "取得");
        if (hasUpdate || !isSaved) {
            actionButton.setBackgroundResource(R.drawable.bg_button_update_highlight);
        }
        actionButton.setOnClickListener(v -> {
            viewModel.refreshPrefecture(meta.getPrefCode(), meta.getName(), meta.getSubName());
            String action = isSaved ? "を更新しています..." : "を取得しています...";
            Toast.makeText(context, displayName + action, Toast.LENGTH_SHORT).show();
        });

        header.addView(nameView);
        header.addView(actionButton);

        if (isSaved) {
            ImageButton deleteButton = new ImageButton(context);
            deleteButton.setImageResource(R.drawable.ic_delete_24);
            deleteButton.setContentDescription(context.getString(R.string.pref_cache_delete));
            deleteButton.setBackgroundResource(android.R.drawable.list_selector_background);
            int buttonSize = (int) (48 * context.getResources().getDisplayMetrics().density + 0.5f);
            deleteButton.setLayoutParams(new LinearLayout.LayoutParams(buttonSize, buttonSize));
            deleteButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                    .setMessage(context.getString(R.string.pref_cache_delete_confirm, displayName))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        viewModel.deletePrefectureCache(meta.getPrefCode(), meta.getSubName());
                        ViewGroup parent = (ViewGroup) row.getParent();
                        if (parent != null) {
                            parent.removeView(row);
                        }
                    })
                    .show());
            header.addView(deleteButton);
        }
        row.addView(header);

        TextView dateView = new TextView(context);
        String sourceDate = sourceUpdatedAt == null ? "-" : sdf.format(sourceUpdatedAt);
        String lastAcquired = (meta.getLastUpdated() == 0) ? "-" : sdf.format(new Date(meta.getLastUpdated()));
        dateView.setText("最終取得日時: " + lastAcquired
                + "\nデータ源更新日: " + sourceDate);
        dateView.setTextSize(12f);
        row.addView(dateView);
        return row;
    }
}
