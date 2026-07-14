package com.odin2.odinsettings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.DisabledHardwareAdapter;
import com.odin2.odinsettings.hardware.FanStatusRead;
import com.odin2.odinsettings.hardware.FanStatusReader;
import com.odin2.odinsettings.platform.AndroidDeviceIdentity;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.platform.NativeFanStatusReader;
import com.odin2.odinsettings.policy.AccessDecision;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;

public final class MainSettingsActivity extends PreferenceActivity {
    private ListView preferenceList;
    private int lastFocusedPosition = ListView.INVALID_POSITION;
    private boolean controllerFocusActive;
    private final FanStatusReader fanStatusReader = new NativeFanStatusReader();
    private Preference fanStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);

        Preference controllerTest = findPreference("controller_input_test");
        controllerTest.setIntent(new Intent(this, ControllerTestActivity.class));
        fanStatus = findPreference("fan_status");
        updateFanStatus();

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

        configureControllerFocus();
        focusFirstEnabledPreference();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFanStatus();
    }

    private void updateFanStatus() {
        if (fanStatus == null) {
            return;
        }
        FanStatusRead status = fanStatusReader.read();
        switch (status.code) {
            case AVAILABLE:
                fanStatus.setSummary(status.state == 1
                        ? getString(R.string.fan_status_active_summary, status.duty)
                        : getString(R.string.fan_status_inactive_summary, status.duty));
                break;
            case UNSUPPORTED:
                fanStatus.setSummary(R.string.fan_status_unsupported_summary);
                break;
            case UNAVAILABLE:
                fanStatus.setSummary(R.string.fan_status_unavailable_summary);
                break;
            case MALFORMED:
                fanStatus.setSummary(R.string.fan_status_malformed_summary);
                break;
        }
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
        if (ControllerNavigation.isControllerEvent(event)
                && event.getAction() == KeyEvent.ACTION_DOWN) {
            activateControllerFocus();
        }
        return super.dispatchKeyEvent(ControllerNavigation.translateConfirm(event));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && controllerFocusActive && preferenceList != null) {
            preferenceList.post(new Runnable() {
                @Override
                public void run() {
                    restoreControllerFocus();
                }
            });
        }
    }

    private void configureControllerFocus() {
        preferenceList = getListView();
        preferenceList.setFocusableInTouchMode(true);
        preferenceList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isEnabledPosition(position)) {
                    lastFocusedPosition = position;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        preferenceList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    int position = preferenceList.pointToPosition(
                            Math.round(event.getX()), Math.round(event.getY()));
                    if (isEnabledPosition(position)) {
                        lastFocusedPosition = position;
                    }
                    controllerFocusActive = false;
                }
                return false;
            }
        });
    }

    private void focusFirstEnabledPreference() {
        controllerFocusActive = true;
        preferenceList.post(new Runnable() {
            @Override
            public void run() {
                restoreControllerFocus();
            }
        });
    }

    private void activateControllerFocus() {
        controllerFocusActive = true;
        restoreControllerFocus();
    }

    private void restoreControllerFocus() {
        int selectedPosition = preferenceList.getSelectedItemPosition();
        if (isEnabledPosition(selectedPosition)) {
            lastFocusedPosition = selectedPosition;
        } else if (!isEnabledPosition(lastFocusedPosition)) {
            lastFocusedPosition = firstEnabledPosition();
        }
        if (lastFocusedPosition == ListView.INVALID_POSITION) {
            return;
        }
        preferenceList.requestFocus();
        preferenceList.setSelection(lastFocusedPosition);
    }

    private int firstEnabledPosition() {
        ListAdapter adapter = preferenceList.getAdapter();
        if (adapter == null) {
            return ListView.INVALID_POSITION;
        }
        for (int position = 0; position < adapter.getCount(); position++) {
            if (adapter.isEnabled(position)) {
                return position;
            }
        }
        return ListView.INVALID_POSITION;
    }

    private boolean isEnabledPosition(int position) {
        ListAdapter adapter = preferenceList == null ? null : preferenceList.getAdapter();
        return adapter != null && position >= 0 && position < adapter.getCount()
                && adapter.isEnabled(position);
    }
}
