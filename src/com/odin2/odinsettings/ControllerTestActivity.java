package com.odin2.odinsettings;

import android.app.Activity;
import android.app.ActionBar;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.platform.AndroidControllerInputMapper;
import com.odin2.odinsettings.platform.ControllerDisplayNames;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.platform.ControllerProfileStore;

public final class ControllerTestActivity extends Activity {
    private TextView profileValue;
    private TextView physicalValue;
    private TextView mappedValue;
    private LinearLayout resultRegion;
    private ControllerProfile profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);
        content.setAccessibilityPaneTitle(getString(R.string.controller_test_title));

        TextView title = text(getString(R.string.controller_test_title), 24);
        content.addView(title);

        TextView prompt = text(getString(R.string.controller_test_prompt), 16);
        prompt.setPadding(0, dp(12), 0, dp(24));
        content.addView(prompt);

        Button done = new Button(this);
        done.setText(R.string.done);
        done.setFocusable(true);
        done.setFocusableInTouchMode(true);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        doneParams.bottomMargin = dp(4);
        content.addView(done, doneParams);

        resultRegion = new LinearLayout(this);
        resultRegion.setOrientation(LinearLayout.VERTICAL);
        resultRegion.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);

        profileValue = value();
        addLabeledValue(resultRegion, R.string.preview_profile_label, profileValue);

        physicalValue = value();
        addLabeledValue(resultRegion, R.string.physical_button_label, physicalValue);

        mappedValue = value();
        addLabeledValue(resultRegion, R.string.preview_output_label, mappedValue);
        content.addView(resultRegion);

        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        done.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        profile = ControllerProfileStore.read(this);
        profileValue.setText(ControllerDisplayNames.profileName(profile));
        physicalValue.setText(R.string.no_button_detected);
        mappedValue.setText(R.string.no_button_detected);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (ControllerNavigation.isBack(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                finish();
            }
            return true;
        }
        if (ControllerNavigation.isConfirm(event)) {
            return super.dispatchKeyEvent(ControllerNavigation.translateConfirm(event));
        }
        if (ControllerNavigation.isDirectional(event)) {
            return super.dispatchKeyEvent(event);
        }
        boolean controllerSource = event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
        if (!controllerSource) {
            return super.dispatchKeyEvent(event);
        }
        ControllerButton physical = AndroidControllerInputMapper.fromKeyCode(event.getKeyCode());
        if (physical == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            String physicalName = ControllerDisplayNames.buttonName(physical);
            String mappedName = ControllerDisplayNames.buttonName(profile.map(physical));
            physicalValue.setText(physicalName);
            mappedValue.setText(mappedName);
            String announcement = getString(
                    R.string.controller_test_result_announcement, physicalName, mappedName);
            resultRegion.setContentDescription(announcement);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void addLabeledValue(LinearLayout parent, int labelResId, TextView target) {
        TextView label = text(getString(labelResId), 14);
        label.setPadding(0, dp(20), 0, dp(4));
        target.setId(View.generateViewId());
        label.setLabelFor(target.getId());
        parent.addView(label);
        parent.addView(target);
    }

    private TextView value() {
        return text(getString(R.string.no_button_detected), 18);
    }

    private TextView text(String value, float sizeSp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
