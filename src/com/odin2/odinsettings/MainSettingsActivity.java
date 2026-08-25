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
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.ControllerProfileDispatcher;
import com.odin2.odinsettings.hardware.DisabledHardwareAdapter;
import com.odin2.odinsettings.hardware.FanActualState;
import com.odin2.odinsettings.hardware.FanApplyDispatcher;
import com.odin2.odinsettings.hardware.FanControlResult;
import com.odin2.odinsettings.hardware.FanController;
import com.odin2.odinsettings.hardware.FanMode;
import com.odin2.odinsettings.platform.AndroidDeviceIdentity;
import com.odin2.odinsettings.platform.AidlControllerHardwareAdapter;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.platform.ControllerDisplayNames;
import com.odin2.odinsettings.hardware.PerformanceControlResult;
import com.odin2.odinsettings.hardware.PerformanceController;
import com.odin2.odinsettings.hardware.PerformanceMode;
import com.odin2.odinsettings.platform.AidlFanController;
import com.odin2.odinsettings.platform.AidlPerformanceController;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;
import com.odin2.odinsettings.service.ControllerProfileCoordinator;
import com.odin2.odinsettings.widget.ControllerListPreference;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class MainSettingsActivity extends PreferenceActivity {
    private static final String STATE_REQUESTED_FAN_MODE = "requested_fan_mode";
    private static final ExecutorService PROFILE_WORKER = Executors.newSingleThreadExecutor();
    private static final ExecutorService FAN_WORKER = Executors.newSingleThreadExecutor();
    private static final ExecutorService PERFORMANCE_WORKER =
            Executors.newSingleThreadExecutor();

    private ListView preferenceList;
    private int lastFocusedPosition = ListView.INVALID_POSITION;
    private boolean controllerFocusActive;
    private final FanController fanController = AidlFanController.getInstance();
    private final PerformanceController performanceController =
            AidlPerformanceController.getInstance();
    private ControllerListPreference performanceModePreference;
    private PerformanceMode lastRequestedPerformanceMode;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DeviceIdentity controllerIdentity = AndroidDeviceIdentity.current();
    private final ControllerProfileCoordinator controllerProfileCoordinator =
            new ControllerProfileCoordinator(new HardwareAccessPolicy(),
                    AidlControllerHardwareAdapter.getInstance());
    private final ControllerProfileDispatcher controllerProfileDispatcher =
            new ControllerProfileDispatcher(PROFILE_WORKER, new Executor() {
                @Override
                public void execute(Runnable command) {
                    if (!mainHandler.post(command)) {
                        throw new RejectedExecutionException("Main thread is shutting down");
                    }
                }
            });
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
    private ControllerListPreference controllerProfilePreference;
    private FanMode lastRequestedMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.main_preferences);
        restoreRequestedFanMode(savedInstanceState);

        Preference controllerTest = findPreference("controller_input_test");
        controllerTest.setIntent(new Intent(this, ControllerTestActivity.class));
        controllerProfilePreference = (ControllerListPreference) findPreference(
                "system_controller_profile");
        controllerProfilePreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        return applyControllerProfile(String.valueOf(newValue));
                    }
                });
        fanModePreference = (ControllerListPreference) findPreference("fan_mode");
        fanModePreference.setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        return applyFanMode(String.valueOf(newValue));
                    }
                });
        performanceModePreference =
                (ControllerListPreference) findPreference("performance_mode");
        if (performanceModePreference != null) {
            performanceModePreference.setOnPreferenceChangeListener(
                    new Preference.OnPreferenceChangeListener() {
                        @Override
                        public boolean onPreferenceChange(Preference preference,
                                Object newValue) {
                            return applyPerformanceMode(String.valueOf(newValue));
                        }
                    });
        }
        AdapterStatus adapter = new DisabledHardwareAdapter().status();

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
        updateControllerProfile();
        updateFanStatus();
        updatePerformanceStatus();
    }

    @Override
    protected void onStop() {
        controllerProfileDispatcher.invalidateCallbacks();
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
        controllerProfileDispatcher.close();
        fanApplyDispatcher.close();
        super.onDestroy();
    }

    private void updateControllerProfile() {
        controllerProfilePreference.setEnabled(false);
        controllerProfilePreference.setSummary(R.string.controller_profile_reading_summary);
        boolean accepted = controllerProfileDispatcher.submitRead(
                controllerProfileCoordinator, controllerIdentity,
                new ControllerProfileDispatcher.Callback() {
                    @Override
                    public void onComplete(AdapterResult result) {
                        if (isUiStale()) {
                            return;
                        }
                        renderControllerProfile(result);
                    }
                });
        if (!accepted) {
            renderControllerProfile(AdapterResult.of(
                    AdapterResult.Code.UNAVAILABLE, "Profile worker unavailable."));
        }
    }

    private boolean applyControllerProfile(String value) {
        final ControllerProfile requested;
        if (ControllerProfiles.STANDARD_ID.equals(value)) {
            requested = ControllerProfiles.STANDARD;
        } else if (ControllerProfiles.FLIPPED_FACE_ID.equals(value)) {
            requested = ControllerProfiles.FLIPPED_FACE;
        } else {
            renderControllerProfile(AdapterResult.of(
                    AdapterResult.Code.INVALID_PROFILE, "Unknown profile preference."));
            return false;
        }

        controllerProfilePreference.setEnabled(false);
        controllerProfilePreference.setSummary(R.string.controller_profile_applying_summary);
        boolean accepted = controllerProfileDispatcher.submit(
                controllerProfileCoordinator, controllerIdentity, requested,
                new ControllerProfileDispatcher.Callback() {
                    @Override
                    public void onComplete(AdapterResult result) {
                        if (isUiStale()) {
                            return;
                        }
                        renderControllerProfile(result);
                    }
                });
        if (!accepted) {
            renderControllerProfile(AdapterResult.forRequest(
                    AdapterResult.Code.UNAVAILABLE, requested,
                    "Profile worker unavailable."));
        }
        return false;
    }

    private void renderControllerProfile(AdapterResult result) {
        if (result.hasProfile()) {
            controllerProfilePreference.setValue(result.actualProfile.id);
        }
        controllerProfilePreference.setEnabled(controllerProfileCanRetry(result.code));
        switch (result.code) {
            case OK:
                if (result.hasProfile()) {
                    controllerProfilePreference.setSummary(getString(
                            R.string.controller_profile_active_summary,
                            getString(ControllerDisplayNames.profileName(
                                    result.actualProfile))));
                } else {
                    controllerProfilePreference.setSummary(
                            R.string.controller_profile_unavailable_summary);
                    controllerProfilePreference.setEnabled(false);
                }
                break;
            case UNKNOWN_DEVICE:
                controllerProfilePreference.setSummary(R.string.unrecognized_device_summary);
                break;
            case UNSUPPORTED_DEVICE:
            case UNSUPPORTED_CAPABILITY:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_unsupported_summary);
                break;
            case INVALID_PROFILE:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_invalid_summary);
                break;
            case BUSY:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_busy_summary);
                break;
            case STORE_READ_FAILED:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_store_read_failed_summary);
                break;
            case STORE_WRITE_FAILED:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_store_write_failed_summary);
                break;
            case NOT_INITIALIZED:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_not_initialized_summary);
                break;
            case READBACK_MISMATCH:
                if (result.hasProfile()) {
                    controllerProfilePreference.setSummary(getString(
                            R.string.controller_profile_mismatch_summary,
                            getString(ControllerDisplayNames.profileName(
                                    result.actualProfile))));
                } else {
                    controllerProfilePreference.setSummary(
                            R.string.controller_profile_unavailable_summary);
                }
                break;
            case UNAVAILABLE:
            case ADAPTER_UNAVAILABLE:
            case REJECTED:
            case APPLIED:
                controllerProfilePreference.setSummary(
                        R.string.controller_profile_unavailable_summary);
                break;
        }
    }

    private static boolean controllerProfileCanRetry(AdapterResult.Code code) {
        return code == AdapterResult.Code.OK
                || code == AdapterResult.Code.BUSY
                || code == AdapterResult.Code.STORE_READ_FAILED
                || code == AdapterResult.Code.STORE_WRITE_FAILED
                || code == AdapterResult.Code.READBACK_MISMATCH;
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

    private boolean applyPerformanceMode(String preferenceValue) {
        if (performanceModePreference == null) {
            return false;
        }
        final PerformanceMode requested;
        try {
            requested = PerformanceMode.fromPreferenceValue(preferenceValue);
        } catch (IllegalArgumentException rejected) {
            // An unknown value never reaches the daemon.
            return false;
        }
        lastRequestedPerformanceMode = requested;
        performanceModePreference.setEnabled(false);
        submitPerformance(new PerformanceWork() {
            @Override
            public PerformanceControlResult run() {
                return performanceController.apply(requested);
            }
        });
        // The row re-renders from what the daemon reports, not from the tap, so
        // a refused mode does not leave the UI claiming it was applied.
        return false;
    }

    private void updatePerformanceStatus() {
        if (performanceModePreference == null) {
            return;
        }
        performanceModePreference.setEnabled(false);
        submitPerformance(new PerformanceWork() {
            @Override
            public PerformanceControlResult run() {
                return performanceController.read();
            }
        });
    }

    private interface PerformanceWork {
        PerformanceControlResult run();
    }

    private void submitPerformance(final PerformanceWork work) {
        try {
            PERFORMANCE_WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    final PerformanceControlResult result = work.run();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!isUiStale() && performanceModePreference != null) {
                                performanceModePreference.setEnabled(true);
                                renderPerformanceStatus(result);
                            }
                        }
                    });
                }
            });
        } catch (RejectedExecutionException rejected) {
            performanceModePreference.setEnabled(true);
            renderPerformanceStatus(PerformanceControlResult.failure(
                    PerformanceControlResult.Code.UNAVAILABLE,
                    lastRequestedPerformanceMode));
        }
    }

    private void renderPerformanceStatus(PerformanceControlResult result) {
        if (performanceModePreference == null || result == null) {
            return;
        }
        if (result.isAvailable()) {
            performanceModePreference.setValue(result.mode.preferenceValue);
            performanceModePreference.setSummary(
                    getString(performanceModeLabel(result.mode)));
            return;
        }
        performanceModePreference.setSummary(
                getString(R.string.performance_status_initial_summary));
    }

    private static int performanceModeLabel(PerformanceMode mode) {
        switch (mode) {
            case STOCK_NORMAL:
                return R.string.performance_mode_normal;
            case PERFORMANCE:
                return R.string.performance_mode_performance;
            case HIGH:
                return R.string.performance_mode_high;
            case SYSTEM_MANAGED:
            default:
                return R.string.performance_mode_system;
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
