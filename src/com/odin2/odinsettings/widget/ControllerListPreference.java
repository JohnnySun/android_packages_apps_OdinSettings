package com.odin2.odinsettings.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;

import com.odin2.odinsettings.platform.ControllerNavigation;

public final class ControllerListPreference extends ListPreference {
    public ControllerListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ControllerListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (ControllerNavigation.isBack(event)) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        dialog.cancel();
                    }
                    return true;
                }
                if (!ControllerNavigation.isConfirm(event)) {
                    return false;
                }
                if (event.getAction() == KeyEvent.ACTION_UP && dialog instanceof AlertDialog) {
                    clickSelectedRow((AlertDialog) dialog);
                }
                return true;
            }
        });
    }

    private static void clickSelectedRow(AlertDialog dialog) {
        ListView list = dialog.getListView();
        int position = list.getSelectedItemPosition();
        if (position == ListView.INVALID_POSITION) {
            position = list.getCheckedItemPosition();
        }
        if (position == ListView.INVALID_POSITION) {
            return;
        }
        View row = list.getSelectedView();
        if (row == null) {
            row = list.getChildAt(position - list.getFirstVisiblePosition());
        }
        list.performItemClick(row, position, list.getItemIdAtPosition(position));
    }
}
