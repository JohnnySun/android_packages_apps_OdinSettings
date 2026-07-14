package com.odin2.odinsettings.platform;

import android.view.InputDevice;
import android.view.KeyEvent;

public final class ControllerNavigation {
    public static boolean isControllerEvent(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD)
                || event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.isFromSource(InputDevice.SOURCE_DPAD);
    }

    public static boolean isBack(KeyEvent event) {
        return isControllerEvent(event) && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B;
    }

    public static boolean isConfirm(KeyEvent event) {
        return isControllerEvent(event) && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A;
    }

    public static boolean isDirectional(KeyEvent event) {
        if (!isControllerEvent(event)) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    public static KeyEvent translateConfirm(KeyEvent event) {
        if (!isConfirm(event)) {
            return event;
        }
        return new KeyEvent(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                KeyEvent.KEYCODE_DPAD_CENTER,
                event.getRepeatCount(),
                event.getMetaState(),
                event.getDeviceId(),
                event.getScanCode(),
                event.getFlags(),
                event.getSource());
    }

    private ControllerNavigation() {}
}
