package com.odin2.odinsettings.platform;

import android.view.KeyEvent;

import com.odin2.odinsettings.domain.ControllerButton;

public final class AndroidControllerInputMapper {
    public static ControllerButton fromKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                return ControllerButton.A;
            case KeyEvent.KEYCODE_BUTTON_B:
                return ControllerButton.B;
            case KeyEvent.KEYCODE_BUTTON_X:
                return ControllerButton.X;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return ControllerButton.Y;
            case KeyEvent.KEYCODE_BUTTON_L1:
                return ControllerButton.LEFT_BUMPER;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return ControllerButton.RIGHT_BUMPER;
            case KeyEvent.KEYCODE_BUTTON_L2:
                return ControllerButton.LEFT_TRIGGER;
            case KeyEvent.KEYCODE_BUTTON_R2:
                return ControllerButton.RIGHT_TRIGGER;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return ControllerButton.LEFT_STICK;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                return ControllerButton.RIGHT_STICK;
            case KeyEvent.KEYCODE_BUTTON_START:
                return ControllerButton.START;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return ControllerButton.SELECT;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                return ControllerButton.HOME;
            case KeyEvent.KEYCODE_DPAD_UP:
                return ControllerButton.DPAD_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return ControllerButton.DPAD_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return ControllerButton.DPAD_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return ControllerButton.DPAD_RIGHT;
            default:
                return null;
        }
    }

    private AndroidControllerInputMapper() {}
}
