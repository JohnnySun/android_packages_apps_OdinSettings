package com.odin2.odinsettings;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public final class MainSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView view = new TextView(this);
        view.setGravity(Gravity.CENTER);
        view.setPadding(48, 48, 48, 48);
        view.setText(R.string.bringup_summary);
        setContentView(view);
    }
}
