package com.odin2.odinsettings;

import android.app.Activity;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.platform.AndroidControllerInputMapper;
import com.odin2.odinsettings.platform.ControllerDisplayNames;
import com.odin2.odinsettings.platform.ControllerProfileStore;

public final class ControllerTestActivity extends Activity {
    private TextView profileValue;
    private TextView physicalValue;
    private TextView mappedValue;
    private ControllerProfile profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        content.setPadding(padding, padding, padding, padding);

        TextView title = text(getString(R.string.controller_test_title), 24);
        content.addView(title);

        TextView prompt = text(getString(R.string.controller_test_prompt), 16);
        prompt.setPadding(0, dp(12), 0, dp(24));
        content.addView(prompt);

        content.addView(label(getString(R.string.preview_profile_label)));
        profileValue = value();
        content.addView(profileValue);

        content.addView(label(getString(R.string.physical_button_label)));
        physicalValue = value();
        content.addView(physicalValue);

        content.addView(label(getString(R.string.preview_output_label)));
        mappedValue = value();
        content.addView(mappedValue);

        setContentView(content);
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
            physicalValue.setText(ControllerDisplayNames.buttonName(physical));
            mappedValue.setText(ControllerDisplayNames.buttonName(profile.map(physical)));
        }
        return true;
    }

    private TextView label(String value) {
        TextView view = text(value, 14);
        view.setPadding(0, dp(20), 0, dp(4));
        return view;
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
