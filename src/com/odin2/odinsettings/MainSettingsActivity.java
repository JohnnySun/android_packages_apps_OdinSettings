package com.odin2.odinsettings;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(48, 48, 48, 48);
        scrollView.addView(list);

        TextView title = new TextView(this);
        title.setText(R.string.control_list_title);
        title.setTextSize(24);
        title.setGravity(Gravity.START);
        list.addView(title);

        TextView summary = new TextView(this);
        summary.setText(OdinControlRegistry.LINEAGE_TARGET + "\n"
                + getString(R.string.bringup_summary) + "\n"
                + "Write policy: " + OdinControlRegistry.HARDWARE_WRITE_POLICY);
        summary.setPadding(0, 12, 0, 24);
        list.addView(summary);

        for (OdinControl control : OdinControlRegistry.CONTROLS) {
            TextView row = new TextView(this);
            row.setPadding(0, 18, 0, 18);
            row.setText(control.label + "\n" + getString(R.string.control_disabled_summary)
                    + "\nMapping: " + control.mappingStatus
                    + "\nCandidates: " + formatCandidateReadPaths(control.candidateReadPaths)
                    + "\nAction: " + control.lineageAction
                    + "\nEvidence: " + control.summary);
            list.addView(row);
        }

        setContentView(scrollView);
    }

    private static String formatCandidateReadPaths(String[] candidateReadPaths) {
        if (candidateReadPaths.length == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < candidateReadPaths.length; i += 1) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(candidateReadPaths[i]);
        }
        return builder.toString();
    }
}
