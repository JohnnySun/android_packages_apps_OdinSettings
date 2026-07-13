package com.odin2.odinsettings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.DisabledHardwareAdapter;
import com.odin2.odinsettings.platform.AndroidDeviceIdentity;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.policy.AccessDecision;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;

public final class MainSettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);

        Preference controllerTest = findPreference("controller_input_test");
        controllerTest.setIntent(new Intent(this, ControllerTestActivity.class));

        AccessDecision identity = new HardwareAccessPolicy().evaluate(
                AndroidDeviceIdentity.current());
        AdapterStatus adapter = new DisabledHardwareAdapter().status();

        Preference systemMapping = findPreference("system_controller_mapping");
        systemMapping.setSummary(identity.allowed
                ? getString(R.string.system_mapping_unavailable_summary)
                : getString(R.string.unrecognized_device_summary));

        Preference externalDisplay = findPreference("external_display_policy");
        externalDisplay.setSummary(adapter.available
                ? getString(R.string.external_display_available_summary)
                : getString(R.string.external_display_unavailable_summary));

        focusFirstEnabledPreference();
    }

    @Override
    public boolean onIsMultiPane() {
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (ControllerNavigation.isBack(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                finish();
            }
            return true;
        }
        return super.dispatchKeyEvent(ControllerNavigation.translateConfirm(event));
    }

    private void focusFirstEnabledPreference() {
        ListView list = getListView();
        list.setFocusableInTouchMode(true);
        list.post(new Runnable() {
            @Override
            public void run() {
                ListAdapter adapter = list.getAdapter();
                for (int position = 0; position < adapter.getCount(); position++) {
                    if (adapter.isEnabled(position)) {
                        list.requestFocus();
                        list.setSelection(position);
                        return;
                    }
                }
            }
        });
    }
}
