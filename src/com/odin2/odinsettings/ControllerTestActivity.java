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

        setContentView(R.layout.controller_test_activity);

        LinearLayout content = findViewById(R.id.controller_test_content);
        content.setAccessibilityPaneTitle(getString(R.string.controller_test_title));
        Button done = findViewById(R.id.controller_test_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        resultRegion = findViewById(R.id.controller_test_results);
        profileValue = findViewById(R.id.controller_test_profile_value);
        physicalValue = findViewById(R.id.controller_test_physical_value);
        mappedValue = findViewById(R.id.controller_test_mapped_value);
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
            String physicalName = getString(ControllerDisplayNames.buttonName(physical));
            String mappedName = getString(
                    ControllerDisplayNames.buttonName(profile.map(physical)));
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

}
