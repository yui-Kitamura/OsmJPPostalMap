package pro.eng.yui.android.osmjppostalmap.core;

import android.content.Context;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.eng.yui.android.osmjppostalmap.R;

public final class IndoorBuildingNameConfirmation {

    private IndoorBuildingNameConfirmation() {
    }

    public static void show(Context context, View addressEditButton, Runnable onSave) {
        boolean[] returnToInput = {false};
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setMessage(R.string.msg_building_name_required)
                .setNegativeButton(R.string.btn_return_to_input, (d, which) -> returnToInput[0] = true)
                .setPositiveButton(R.string.btn_save_without_building_name, (d, which) -> onSave.run())
                .create();
        dialog.setOnDismissListener(d -> {
            if (returnToInput[0]) {
                addressEditButton.post(() -> addressEditButton.requestFocus());
            }
        });
        dialog.show();
    }
}