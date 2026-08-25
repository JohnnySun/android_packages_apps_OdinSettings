package com.odin2.odinsettings.widget;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ListView;

import com.odin2.odinsettings.platform.ControllerNavigation;

public final class ControllerListPreference extends ListPreference {
    // AOSP's ListPreference.getSummary runs the summary back through
    // String.format with the selected entry as the argument. Every summary here
    // is already formatted in Java before it is set and none of them wants the
    // entry substituted in, so that second pass can only do harm: a summary
    // carrying a literal percent sign — a battery level, say — throws
    // UnknownFormatConversionException while the list is laying out and takes
    // the activity down. The summary is kept verbatim instead.
    private CharSequence literalSummary;

    public ControllerListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        literalSummary = super.getSummary();
    }

    public ControllerListPreference(Context context) {
        super(context);
        literalSummary = super.getSummary();
    }

    @Override
    public void setSummary(CharSequence summary) {
        literalSummary = summary;
        super.setSummary(summary);
    }

    @Override
    public CharSequence getSummary() {
        return literalSummary != null ? literalSummary : super.getSummary();
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

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        Dialog currentDialog = getDialog();
        if (!(currentDialog instanceof AlertDialog)) {
            return;
        }
        final ListView list = ((AlertDialog) currentDialog).getListView();
        list.setFocusableInTouchMode(true);
        list.post(new Runnable() {
            @Override
            public void run() {
                int position = list.getCheckedItemPosition();
                if (position == ListView.INVALID_POSITION) {
                    position = findIndexOfValue(getValue());
                }
                list.requestFocus();
                if (position != ListView.INVALID_POSITION) {
                    list.setSelection(position);
                }
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
