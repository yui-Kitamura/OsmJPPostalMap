package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.eng.yui.android.osmjppostalmap.R;
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

        List<PrefMeta> metas = viewModel.getSavedPrefectures();
        if (metas.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.JAPAN);
        for (PrefMeta meta : metas) {
            container.addView(buildRow(context, viewModel, meta, sdf));
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle("データの更新");
        builder.setView(view);
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

    private static View buildRow(Context context, MainViewModel viewModel, PrefMeta meta, SimpleDateFormat sdf) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 12);

        TextView nameView = new TextView(context);
        nameView.setText(meta.getName());
        nameView.setTextSize(16f);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView dateView = new TextView(context);
        dateView.setText(sdf.format(new Date(meta.getLastUpdated())));
        dateView.setTextSize(12f);
        dateView.setPadding(0, 0, 16, 0);

        Button updateButton = new Button(context);
        updateButton.setText("更新");
        updateButton.setOnClickListener(v -> {
            viewModel.refreshPrefecture(meta.getPrefCode(), meta.getName());
            Toast.makeText(context, meta.getName() + "を更新しています...", Toast.LENGTH_SHORT).show();
        });

        row.addView(nameView);
        row.addView(dateView);
        row.addView(updateButton);
        return row;
    }
}
