package com.odin2.odinsettings;

import android.app.Activity;
import android.app.ActionBar;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.odin2.odinsettings.domain.ControllerAxisNormalizer;
import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerKeyCapturePolicy;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerScanCodeMapper;
import com.odin2.odinsettings.platform.AndroidControllerInputMapper;
import com.odin2.odinsettings.platform.ControllerDisplayNames;
import com.odin2.odinsettings.platform.ControllerNavigation;
import com.odin2.odinsettings.platform.ControllerProfileStore;

import java.util.Locale;

public final class ControllerTestActivity extends Activity
        implements InputManager.InputDeviceListener {
    private static final int ODIN_GAMEPAD_VENDOR_ID = 0x2020;
    private static final int ODIN_GAMEPAD_PRODUCT_ID = 0x3001;

    private InputManager inputManager;
    private Button done;
    private TextView deviceValue;
    private TextView profileValue;
    private TextView physicalValue;
    private TextView mappedValue;
    private TextView leftStickValue;
    private TextView rightStickValue;
    private TextView triggerValue;
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
        done = findViewById(R.id.controller_test_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        resultRegion = findViewById(R.id.controller_test_results);
        deviceValue = findViewById(R.id.controller_test_device_value);
        profileValue = findViewById(R.id.controller_test_profile_value);
        physicalValue = findViewById(R.id.controller_test_physical_value);
        mappedValue = findViewById(R.id.controller_test_mapped_value);
        leftStickValue = findViewById(R.id.controller_test_left_stick_value);
        rightStickValue = findViewById(R.id.controller_test_right_stick_value);
        triggerValue = findViewById(R.id.controller_test_trigger_value);
        inputManager = getSystemService(InputManager.class);

        content.setFocusableInTouchMode(true);
        content.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        profile = ControllerProfileStore.read(this);
        profileValue.setText(ControllerDisplayNames.profileName(profile));
        physicalValue.setText(R.string.no_button_detected);
        mappedValue.setText(R.string.no_button_detected);
        setAxisValues(0, 0, 0, 0, 0, 0);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(this, null);
        }
        showDevice(findController());
    }

    @Override
    protected void onPause() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(this);
        }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        InputDevice device = event.getDevice();
        boolean controllerEvent = isControllerEvent(event, device);
        ControllerButton physical = ControllerKeyCapturePolicy.select(
                ControllerScanCodeMapper.fromScanCode(event.getScanCode()),
                AndroidControllerInputMapper.fromKeyCode(event.getKeyCode()),
                controllerEvent);
        if (physical == null) {
            return super.dispatchKeyEvent(event);
        }
        showDevice(device);
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            showButton(physical);
        }
        if (ControllerNavigation.isDirectional(event)) {
            return super.dispatchKeyEvent(event);
        }
        if (physical == ControllerButton.A && done.hasFocus()) {
            return super.dispatchKeyEvent(ControllerNavigation.translateConfirm(event));
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (event.getAction() != MotionEvent.ACTION_MOVE || !isControllerMotion(event, device)) {
            return super.dispatchGenericMotionEvent(event);
        }

        showDevice(device);
        float leftX = centeredAxis(event, MotionEvent.AXIS_X);
        float leftY = centeredAxis(event, MotionEvent.AXIS_Y);
        float rightX;
        float rightY;
        float leftTrigger;
        float rightTrigger;
        if (isOdinGamepad(device)) {
            rightX = centeredAxis(event, MotionEvent.AXIS_Z);
            rightY = centeredAxis(event, MotionEvent.AXIS_RZ);
            leftTrigger = triggerAxis(event, MotionEvent.AXIS_LTRIGGER);
            rightTrigger = triggerAxis(event, MotionEvent.AXIS_RTRIGGER);
        } else {
            int[] rightAxes = hasAxes(device, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY)
                    ? new int[] {MotionEvent.AXIS_RX, MotionEvent.AXIS_RY}
                    : new int[] {MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ};
            rightX = centeredAxis(event, rightAxes[0]);
            rightY = centeredAxis(event, rightAxes[1]);
            leftTrigger = firstTriggerAxis(event,
                    MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE,
                    rightAxes[0] == MotionEvent.AXIS_RX ? MotionEvent.AXIS_Z : -1);
            rightTrigger = firstTriggerAxis(event,
                    MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS,
                    rightAxes[1] == MotionEvent.AXIS_RY ? MotionEvent.AXIS_RZ : -1);
        }
        setAxisValues(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger);
        return true;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        showDevice(findController());
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        showDevice(findController());
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        showDevice(findController());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showButton(ControllerButton physical) {
        String physicalName = getString(ControllerDisplayNames.buttonName(physical));
        String mappedName = getString(ControllerDisplayNames.buttonName(profile.map(physical)));
        physicalValue.setText(physicalName);
        mappedValue.setText(mappedName);
        resultRegion.setContentDescription(getString(
                R.string.controller_test_result_announcement, physicalName, mappedName));
    }

    private void setAxisValues(float leftX, float leftY, float rightX, float rightY,
            float leftTrigger, float rightTrigger) {
        leftStickValue.setText(getString(R.string.controller_axis_pair_value,
                percent(leftX), percent(leftY)));
        rightStickValue.setText(getString(R.string.controller_axis_pair_value,
                percent(rightX), percent(rightY)));
        triggerValue.setText(getString(R.string.controller_trigger_pair_value,
                percent(leftTrigger), percent(rightTrigger)));
    }

    private void showDevice(InputDevice device) {
        if (device == null) {
            deviceValue.setText(R.string.no_controller_detected);
            return;
        }
        String identity = String.format(Locale.ROOT, "%04x:%04x",
                device.getVendorId(), device.getProductId());
        deviceValue.setText(getString(
                R.string.controller_device_value, device.getName(), identity));
    }

    private InputDevice findController() {
        InputDevice fallback = null;
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (isOdinGamepad(device)) {
                return device;
            }
            if (fallback == null && isControllerDevice(device)) {
                fallback = device;
            }
        }
        return fallback;
    }

    private static boolean isControllerEvent(KeyEvent event, InputDevice device) {
        return isOdinGamepad(device)
                || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
    }

    private static boolean isControllerMotion(MotionEvent event, InputDevice device) {
        return isOdinGamepad(device) || event.isFromSource(InputDevice.SOURCE_JOYSTICK);
    }

    private static boolean isControllerDevice(InputDevice device) {
        return device != null && (isOdinGamepad(device)
                || device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                || device.supportsSource(InputDevice.SOURCE_JOYSTICK));
    }

    private static boolean isOdinGamepad(InputDevice device) {
        return device != null
                && device.getVendorId() == ODIN_GAMEPAD_VENDOR_ID
                && device.getProductId() == ODIN_GAMEPAD_PRODUCT_ID;
    }

    private static boolean hasAxes(InputDevice device, int first, int second) {
        return device != null && device.getMotionRange(first) != null
                && device.getMotionRange(second) != null;
    }

    private static float centeredAxis(MotionEvent event, int axis) {
        InputDevice.MotionRange range = motionRange(event, axis);
        if (range == null) {
            return 0.0f;
        }
        return ControllerAxisNormalizer.centered(event.getAxisValue(axis),
                range.getMin(), range.getMax(), range.getFlat());
    }

    private static float triggerAxis(MotionEvent event, int axis) {
        InputDevice.MotionRange range = motionRange(event, axis);
        if (range == null) {
            return 0.0f;
        }
        return ControllerAxisNormalizer.trigger(event.getAxisValue(axis),
                range.getMin(), range.getMax(), range.getFlat());
    }

    private static float firstTriggerAxis(MotionEvent event, int... axes) {
        for (int axis : axes) {
            if (axis >= 0 && motionRange(event, axis) != null) {
                return triggerAxis(event, axis);
            }
        }
        return 0.0f;
    }

    private static InputDevice.MotionRange motionRange(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device == null) {
            return null;
        }
        InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
        return range != null ? range : device.getMotionRange(axis);
    }

    private static int percent(float value) {
        return Math.round(value * 100.0f);
    }

}
