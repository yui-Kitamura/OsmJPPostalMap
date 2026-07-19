package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.eng.yui.android.osmjppostalmap.R;
import pro.eng.yui.android.osmjppostalmap.domain.model.OsmPoi;
import pro.eng.yui.android.osmjppostalmap.schedule.ScheduleResult;

public class PoiDetailsDialog {

    public static void show(Context context, OsmPoi poi, ScheduleResult schedule) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        String amenity = poi.getTag("amenity");
        boolean isPostBox = "post_box".equals(amenity);
        
        builder.setTitle(isPostBox ? "郵便ポスト" : poi.getTag("name"));
        
        // TODO: 詳細情報のレイアウト作成
        builder.setMessage(schedule != null ? schedule.getTodayStatus() : "情報なし");
        
        builder.setPositiveButton("閉じる", null);
        builder.setNeutralButton("編集", (dialog, which) -> {
            // TODO: 編集画面へ
        });
        
        builder.show();
    }
}
