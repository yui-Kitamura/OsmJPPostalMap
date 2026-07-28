package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

import androidx.lifecycle.Observer;
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
    public static void show(Context context, MainViewModel viewModel, Runnable onRefreshArea) {
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

        // データの構築処理を関数化して、DataDateの取得後に呼び出せるようにする
        Runnable buildList = () -> {
            String currentPref = viewModel.getCurrentPrefecture().getValue();
            String currentSub = viewModel.getCurrentSubArea().getValue();
            // Fetch saved data and remote data date from background, then combine with JpPostal master data
            CompletableFuture.supplyAsync(() -> {
                List<PrefMeta> savedMetas = viewModel.getSavedPrefectures();
                Map<String, PrefMeta> savedMap = new HashMap<>();
                for (PrefMeta m : savedMetas) {
                    String key = m.getName() + (m.getSubName() == null ? "" : ":" + m.getSubName());
                    savedMap.put(key, m);
                }
                return new Object[]{savedMetas, savedMap};
            }).thenCombine(JpPostalUtil.getPrefectures(), (data, allPrefCodes) -> {
                List<PrefMeta> savedMetas = (List<PrefMeta>) data[0];
                Map<String, PrefMeta> savedMap = (Map<String, PrefMeta>) data[1];
                return new Object[]{savedMetas, savedMap, allPrefCodes};
            }).thenCompose(data -> {
                List<PrefMeta> savedMetas = (List<PrefMeta>) data[0];
                Map<String, PrefMeta> savedMap = (Map<String, PrefMeta>) data[1];
                Map<String, Integer> allPrefCodes = (Map<String, Integer>) data[2];

                List<CompletableFuture<Map<String, Integer>>> subAreaFutures = new ArrayList<>();
                List<String> prefNames = new ArrayList<>(allPrefCodes.keySet());
                for (String prefName : prefNames) {
                    subAreaFutures.add(JpPostalUtil.getSubAreas(prefName));
                }
                
                return CompletableFuture.allOf(subAreaFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        Map<String, Map<String, Integer>> allSubCodes = new HashMap<>();
                        for (int i = 0; i < prefNames.size(); i++) {
                            allSubCodes.put(prefNames.get(i), subAreaFutures.get(i).join());
                        }
                        return new Object[]{savedMetas, savedMap, allPrefCodes, allSubCodes};
                    });
            }).thenCombine(JpPostalUtil.getRawPrefecturesJson(), (data, json) -> {
                List<PrefMeta> savedMetas = (List<PrefMeta>) data[0];
                Map<String, PrefMeta> savedMap = (Map<String, PrefMeta>) data[1];
                Map<String, Integer> allPrefCodes = (Map<String, Integer>) data[2];
                Map<String, Map<String, Integer>> allSubCodes = (Map<String, Map<String, Integer>>) data[3];

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

                List<PrefInfo> allItems = new ArrayList<>();
                java.util.Set<String> processedPrefs = new java.util.HashSet<>();

                try {
                    if (json != null && !json.isEmpty()) {
                        JSONObject root = new JSONObject(json);
                        Iterator<String> keys = root.keys();
                        while (keys.hasNext()) {
                            String prefName = keys.next();
                            JSONObject prefObj = root.getJSONObject(prefName);
                            Integer code = allPrefCodes.get(prefName);

                            // Fallback: search in saved metas if not in master list
                            if (code == null) {
                                for (PrefMeta m : savedMetas) {
                                    if (m.getName().equals(prefName)) {
                                        code = m.getPrefCode();
                                        break;
                                    }
                                }
                            }

                            if (code != null) {
                                PrefInfo pi = new PrefInfo(code, prefName);
                                Map<String, Integer> subCodes = allSubCodes.get(prefName);
                                if (prefObj.has("sub")) {
                                    JSONObject subObj = prefObj.getJSONObject("sub");
                                    Iterator<String> subKeys = subObj.keys();
                                    while (subKeys.hasNext()) {
                                        String subName = subKeys.next();
                                        Integer subCode = (subCodes != null) ? subCodes.get(subName) : null;
                                        if (subCode == null) subCode = 0;
                                        pi.subs.add(new SubInfo(subCode, subName));
                                    }
                                }
                                // Also merge from savedMetas for this prefName
                                for (PrefMeta m : savedMetas) {
                                    if (m.getName().equals(prefName) && m.getSubName() != null) {
                                        boolean exists = false;
                                        for (SubInfo si : pi.subs) {
                                            if (si.name.equals(m.getSubName())) {
                                                exists = true;
                                                break;
                                            }
                                        }
                                        if (!exists) {
                                            Integer subCode = (subCodes != null) ? subCodes.get(m.getSubName()) : null;
                                            if (subCode == null) subCode = 0;
                                            pi.subs.add(new SubInfo(subCode, m.getSubName()));
                                        }
                                    }
                                }
                                if (!pi.subs.isEmpty()) {
                                    Collections.sort(pi.subs, (a, b) -> Integer.compare(a.code, b.code));
                                }
                                allItems.add(pi);
                                processedPrefs.add(prefName);
                            }
                        }
                    }

                    // Add remaining from allPrefCodes
                    for (Map.Entry<String, Integer> entry : allPrefCodes.entrySet()) {
                        if (!processedPrefs.contains(entry.getKey())) {
                            PrefInfo pi = new PrefInfo(entry.getValue(), entry.getKey());
                            Map<String, Integer> subCodes = allSubCodes.get(entry.getKey());
                            // Fallback: search in saved metas for subareas
                            for (PrefMeta m : savedMetas) {
                                if (m.getName().equals(entry.getKey()) && m.getSubName() != null) {
                                    boolean exists = false;
                                    for (SubInfo si : pi.subs) {
                                        if (si.name.equals(m.getSubName())) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        Integer subCode = (subCodes != null) ? subCodes.get(m.getSubName()) : null;
                                        if (subCode == null) subCode = 0;
                                        pi.subs.add(new SubInfo(subCode, m.getSubName()));
                                    }
                                }
                            }
                            if (!pi.subs.isEmpty()) {
                                Collections.sort(pi.subs, (a, b) -> Integer.compare(a.code, b.code));
                            }
                            allItems.add(pi);
                            processedPrefs.add(entry.getKey());
                        }
                    }

                    // Add remaining from savedMetas
                    for (PrefMeta m : savedMetas) {
                        if (!processedPrefs.contains(m.getName())) {
                            PrefInfo pi = new PrefInfo(m.getPrefCode(), m.getName());
                            Map<String, Integer> subCodes = allSubCodes.get(m.getName());
                            // Add all subnames for this pref from savedMetas
                            for (PrefMeta m2 : savedMetas) {
                                if (m2.getName().equals(m.getName()) && m2.getSubName() != null) {
                                    boolean exists = false;
                                    for (SubInfo si : pi.subs) {
                                        if (si.name.equals(m2.getSubName())) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        Integer subCode = (subCodes != null) ? subCodes.get(m2.getSubName()) : null;
                                        if (subCode == null) subCode = 0;
                                        pi.subs.add(new SubInfo(subCode, m2.getSubName()));
                                    }
                                }
                            }
                            if (!pi.subs.isEmpty()) {
                                Collections.sort(pi.subs, (a, b) -> Integer.compare(a.code, b.code));
                            }
                            allItems.add(pi);
                            processedPrefs.add(m.getName());
                        }
                    }

                    // Sort: Current > Saved > Others. Within groups, sort by code.
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
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return new Object[]{allItems, savedMap, remoteDates};
            }).thenAccept(result -> {
                List<PrefInfo> allItems = (List<PrefInfo>) result[0];
                Map<String, PrefMeta> savedMap = (Map<String, PrefMeta>) result[1];
                Map<String, Date> remoteDates = (Map<String, Date>) result[2];

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
                                if (pi.subs.isEmpty()) {
                                    PrefMeta meta = savedMap.get(pi.name);
                                    if (meta == null) meta = new PrefMeta(pi.code, pi.name, 0);
                                    Date sourceUpdatedAt = remoteDates.get(pi.name);
                                    boolean isSaved = savedMap.containsKey(pi.name);
                                    boolean hasUpdate = isSaved && sourceUpdatedAt != null && meta.getLastUpdated() < sourceUpdatedAt.getTime();
                                    container.addView(buildRow(context, viewModel, meta, sdf, sourceUpdatedAt, hasUpdate, isSaved, isCurrent));
                                } else {
                                    container.addView(buildHierarchyRow(context, viewModel, pi, savedMap, remoteDates, sdf, isCurrent, currentSub));
                                }
                            }
                        }
                    });
                }
            }).exceptionally(ex -> {
                ex.printStackTrace();
                if (context instanceof AppCompatActivity) {
                    ((AppCompatActivity) context).runOnUiThread(() -> container.removeView(progressBar));
                }
                return null;
            });
        };

        // Observe DataDate and current area updates.
        // We need to handle the lifecycle correctly.
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            
            Runnable refreshAction = () -> {
                // When data is fetched or location updated, refresh the list.
                // We clear the container (except progressBar if it's still there) and rebuild.
                container.removeAllViews();
                container.addView(progressBar);
                buildList.run();
            };

            Observer<DataDateResponse> dateObserver = d -> refreshAction.run();
            Observer<String> prefObserver = s -> refreshAction.run();
            Observer<String> subObserver = s -> refreshAction.run();
            Observer<Boolean> loadingObserver = loading -> {
                if (loading != null && !loading) {
                    refreshAction.run();
                }
            };

            viewModel.getDataDate().observe(activity, dateObserver);
            viewModel.getCurrentPrefecture().observe(activity, prefObserver);
            viewModel.getCurrentSubArea().observe(activity, subObserver);
            viewModel.getLoading().observe(activity, loadingObserver);

            dialog.setOnDismissListener(d -> {
                viewModel.getDataDate().removeObserver(dateObserver);
                viewModel.getCurrentPrefecture().removeObserver(prefObserver);
                viewModel.getCurrentSubArea().removeObserver(subObserver);
                viewModel.getLoading().removeObserver(loadingObserver);
            });
            // Initial trigger
            viewModel.fetchDataDate();
        } else {
            // Fallback for non-lifecycle contexts
            buildList.run();
        }

        dialog.show();
    }

    private static boolean hasAnySaved(PrefInfo pi, Map<String, PrefMeta> savedMap) {
        if (pi.subs.isEmpty()) {
            return savedMap.containsKey(pi.name);
        }
        for (SubInfo sub : pi.subs) {
            if (savedMap.containsKey(pi.name + ":" + sub.name)) return true;
        }
        return false;
    }

    private static class SubInfo {
        int code;
        String name;
        SubInfo(int code, String name) { this.code = code; this.name = name; }
    }

    private static class PrefInfo {
        int code;
        String name;
        List<SubInfo> subs = new ArrayList<>();
        PrefInfo(int code, String name) { this.code = code; this.name = name; }
    }

    private static View buildHierarchyRow(Context context, MainViewModel viewModel, PrefInfo pi,
                                          Map<String, PrefMeta> savedMap, Map<String, Date> remoteDates,
                                          SimpleDateFormat sdf, boolean isCurrent, String currentSub) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        // 親（都道府県）のメタ情報を集約（最終更新日はサブの中で最新のもの、または都道府県全体）
        long lastUpdated = 0;
        boolean allSaved = true;
        boolean anySaved = false;
        for (SubInfo sub : pi.subs) {
            PrefMeta m = savedMap.get(pi.name + ":" + sub.name);
            if (m != null) {
                lastUpdated = Math.max(lastUpdated, m.getLastUpdated());
                anySaved = true;
            } else {
                allSaved = false;
            }
        }
        PrefMeta parentMeta = new PrefMeta(pi.code, pi.name, lastUpdated);
        Date sourceUpdatedAt = remoteDates.get(pi.name);
        boolean hasUpdate = anySaved && sourceUpdatedAt != null && parentMeta.getLastUpdated() < sourceUpdatedAt.getTime();

        // 親行の作成（基本は buildRow と同様だが、タップで展開する）
        View parentRow = buildRow(context, viewModel, parentMeta, sdf, sourceUpdatedAt, hasUpdate, anySaved, isCurrent);
        // buildRow が返すのは LinearLayout(VERTICAL) なので、その中の TextView(nameView) を見つけてアイコンを変える
        LinearLayout header = (LinearLayout) ((ViewGroup) parentRow).getChildAt(0);
        TextView nameView = (TextView) header.getChildAt(0);
        nameView.setText("▶ " + nameView.getText());

        LinearLayout subContainer = new LinearLayout(context);
        subContainer.setOrientation(LinearLayout.VERTICAL);
        subContainer.setPadding(48, 0, 0, 0);
        subContainer.setVisibility(View.GONE);

        nameView.setClickable(true);
        nameView.setBackgroundResource(android.R.drawable.list_selector_background);
        View.OnClickListener toggleListener = v -> {
            if (subContainer.getVisibility() == View.VISIBLE) {
                subContainer.setVisibility(View.GONE);
                nameView.setText(nameView.getText().toString().replace("▼ ", "▶ "));
            } else {
                subContainer.setVisibility(View.VISIBLE);
                nameView.setText(nameView.getText().toString().replace("▶ ", "▼ "));
            }
        };
        nameView.setOnClickListener(toggleListener);
        // Also allow clicking the area around the name
        header.setOnClickListener(toggleListener);

        // 現在地がこの都道府県（かつサブエリア一致、またはサブエリア未特定）なら展開
        if (isCurrent) {
            boolean subMatches = false;
            if (currentSub != null) {
                for (SubInfo sub : pi.subs) {
                    if (sub.name.equals(currentSub)) {
                        subMatches = true;
                        break;
                    }
                }
            }
            if (currentSub == null || subMatches) {
                subContainer.setVisibility(View.VISIBLE);
                nameView.setText(nameView.getText().toString().replace("▶ ", "▼ "));
            }
        }

        for (SubInfo sub : pi.subs) {
            String key = pi.name + ":" + sub.name;
            PrefMeta meta = savedMap.get(key);
            if (meta == null) meta = new PrefMeta(pi.code, pi.name, sub.name, 0);

            boolean isSaved = savedMap.containsKey(key);
            boolean subHasUpdate = isSaved && sourceUpdatedAt != null && meta.getLastUpdated() < sourceUpdatedAt.getTime();
            boolean isSubCurrent = isCurrent && sub.name.equals(currentSub);

            subContainer.addView(buildRow(context, viewModel, meta, sdf, sourceUpdatedAt, subHasUpdate, isSaved, isSubCurrent));
        }

        root.addView(parentRow);
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
        if (isCurrent) {
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
        // Prevent click from bubbling up to header's toggleListener
        actionButton.setFocusable(true);
        actionButton.setClickable(true);

        header.addView(nameView);
        header.addView(actionButton);

        // Make the whole header (including name and update button area) expandable if it's a hierarchy row
        // Actually, nameView already has the listener if it's hierarchy, but let's make it more robust.
        
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
        // Prevent click from bubbling up to header's toggleListener
        deleteButton.setFocusable(true);
        deleteButton.setClickable(true);
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
