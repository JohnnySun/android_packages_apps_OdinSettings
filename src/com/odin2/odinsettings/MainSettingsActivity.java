package com.odin2.odinsettings;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.odin2.odinsettings.hardware.FanActualState;
import com.odin2.odinsettings.hardware.FanApplyDispatcher;
import com.odin2.odinsettings.hardware.FanControlResult;
import com.odin2.odinsettings.hardware.FanController;
import com.odin2.odinsettings.hardware.FanMode;
import com.odin2.odinsettings.platform.AndroidDeviceIdentity;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.platform.AidlFanController;
import com.odin2.odinsettings.policy.AccessDecision;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;
import com.odin2.odinsettings.widget.ControllerListPreference;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainSettingsActivity extends PreferenceActivity {
    private static final String STATE_REQUESTED_FAN_MODE = "requested_fan_mode";
    private static final ExecutorService FAN_WORKER = Executors.newSingleThreadExecutor();

    private ListView preferenceList;
    private int lastFocusedPosition = ListView.INVALID_POSITION;
    private boolean controllerFocusActive;
    private final FanController fanController = AidlFanController.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FanApplyDispatcher fanApplyDispatcher = new FanApplyDispatcher(
            FAN_WORKER, new Executor() {
                @Override
                public void execute(Runnable command) {
                    if (!mainHandler.post(command)) {
                        throw new RejectedExecutionException("Main thread is shutting down");
                    }
                }
            });
    private ControllerListPreference fanModePreference;
    private FanMode lastRequestedMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);
        restoreRequestedFanMode(savedInstanceState);

        Preference controllerTest = findPreference("controller_input_test");
        controllerTest.setIntent(new Intent(this, ControllerTestActivity.class));
        fanModePreference = (ControllerListPreference) findPreference("fan_mode");
        fanModePreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        return applyFanMode(String.valueOf(newValue));
                    }
                });
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

    @Override
    protected void onStop() {
        fanApplyDispatcher.invalidateCallbacks();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (lastRequestedMode != null) {
            outState.putString(STATE_REQUESTED_FAN_MODE,
                    lastRequestedMode.preferenceValue);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        fanApplyDispatcher.close();
        super.onDestroy();
    }

    private void restoreRequestedFanMode(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String savedMode = savedInstanceState.getString(STATE_REQUESTED_FAN_MODE);
        if (savedMode == null) {
            return;
        }
        try {
            lastRequestedMode = FanMode.fromPreferenceValue(savedMode);
        } catch (IllegalArgumentException exception) {
            lastRequestedMode = null;
        }
    }

    private void updateFanStatus() {
        if (fanModePreference == null) {
            return;
        }
        fanModePreference.setEnabled(false);
        boolean accepted = fanApplyDispatcher.submitRead(
                fanController, new FanApplyDispatcher.Callback() {
            @Override
            public void onComplete(FanControlResult result) {
                if (!isUiStale()) {
                    fanModePreference.setEnabled(true);
                    renderFanStatus(result);
                }
            }
        });
        if (!accepted) {
            fanModePreference.setEnabled(true);
            renderFanStatus(FanControlResult.error(
                    FanControlResult.Code.UNAVAILABLE, lastRequestedMode));
        }
    }

    private boolean applyFanMode(String value) {
        final FanMode requestedMode;
        try {
            requestedMode = FanMode.fromPreferenceValue(value);
        } catch (IllegalArgumentException exception) {
            renderFanStatus(FanControlResult.error(
                    FanControlResult.Code.INVALID_MODE, lastRequestedMode));
            return false;
        }
        fanModePreference.setEnabled(false);
        boolean accepted = fanApplyDispatcher.submit(fanController, requestedMode,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        if (isUiStale()) {
                            return;
                        }
                        fanModePreference.setEnabled(true);
                        renderFanStatus(result);
                    }
                });
        if (accepted) {
            lastRequestedMode = requestedMode;
        } else {
            fanModePreference.setEnabled(true);
            renderFanStatus(FanControlResult.error(
                    FanControlResult.Code.UNAVAILABLE, lastRequestedMode));
        }
        return false;
    }

    private boolean isUiStale() {
        return isFinishing() || isDestroyed();
    }

    private void renderFanStatus(FanControlResult status) {
        FanMode requestedMode = status.requestedMode != null
                ? status.requestedMode
                : lastRequestedMode;
        String requested = requestedMode == null
                ? getString(R.string.fan_request_none)
                : getString(modeLabel(requestedMode));

        if (!status.hasSnapshot()) {
            fanModePreference.setSummary(getString(
                    R.string.fan_status_error_summary, requested, getString(errorReason(status))));
            return;
        }

        syncFanChoice(status.actualState);
        if (status.actualState == FanActualState.OFF) {
            fanModePreference.setSummary(getString(R.string.fan_status_off_summary,
                    requested, status.pwmHighTimeNs, status.tachPulsesTimes300));
            return;
        }
        fanModePreference.setSummary(getString(R.string.fan_status_on_summary,
                requested, getString(actualStateLabel(status.actualState)),
                status.pwmHighTimeNs, status.tachPulsesTimes300));
    }

    private void syncFanChoice(FanActualState actualState) {
        switch (actualState) {
            case OFF:
                fanModePreference.setValue(FanMode.OFF.preferenceValue);
                break;
            case QUIET:
                fanModePreference.setValue(FanMode.QUIET.preferenceValue);
                break;
            case SPORT:
                fanModePreference.setValue(FanMode.SPORT.preferenceValue);
                break;
            case UNRECOGNIZED:
                break;
        }
    }

    private static int modeLabel(FanMode mode) {
        switch (mode) {
            case OFF:
                return R.string.fan_mode_off;
            case QUIET:
                return R.string.fan_mode_quiet;
            case SPORT:
                return R.string.fan_mode_sport;
        }
        throw new IllegalArgumentException("Unknown fan mode");
    }

    private static int actualStateLabel(FanActualState state) {
        switch (state) {
            case OFF:
                return R.string.fan_actual_off;
            case QUIET:
                return R.string.fan_actual_quiet;
            case SPORT:
                return R.string.fan_actual_sport;
            case UNRECOGNIZED:
                return R.string.fan_actual_unrecognized;
        }
        throw new IllegalArgumentException("Unknown fan state");
    }

    private static int errorReason(FanControlResult status) {
        switch (status.code) {
            case UNSUPPORTED:
                return R.string.fan_error_unsupported;
            case UNEXPECTED_PATHS:
                return R.string.fan_error_unexpected_paths;
            case PERIOD_MISMATCH:
                return R.string.fan_error_period_mismatch;
            case WRITE_FAILED:
                return R.string.fan_error_write_failed;
            case READBACK_MISMATCH:
                return R.string.fan_error_readback_mismatch;
            case INVALID_MODE:
                return R.string.fan_error_invalid_mode;
            case MALFORMED:
                return R.string.fan_error_malformed;
            case UNAVAILABLE:
            case AVAILABLE:
                return R.string.fan_error_unavailable;
        }
        return R.string.fan_error_unavailable;
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
