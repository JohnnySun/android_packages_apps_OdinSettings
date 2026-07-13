package com.odin2.odinsettings.platform;

import com.odin2.odinsettings.R;
import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;

public final class ControllerDisplayNames {
    public static int profileName(ControllerProfile profile) {
        if (ControllerProfiles.FLIPPED_FACE_ID.equals(profile.id)) {
            return R.string.controller_profile_flipped_face;
        }
        return R.string.controller_profile_standard;
    }

    public static int buttonName(ControllerButton button) {
        switch (button) {
            case A:
                return R.string.controller_button_a;
            case B:
                return R.string.controller_button_b;
            case X:
                return R.string.controller_button_x;
            case Y:
                return R.string.controller_button_y;
            case LEFT_BUMPER:
                return R.string.controller_button_left_bumper;
            case RIGHT_BUMPER:
                return R.string.controller_button_right_bumper;
            case LEFT_TRIGGER:
                return R.string.controller_button_left_trigger;
            case RIGHT_TRIGGER:
                return R.string.controller_button_right_trigger;
            case LEFT_STICK:
                return R.string.controller_button_left_stick;
            case RIGHT_STICK:
                return R.string.controller_button_right_stick;
            case START:
                return R.string.controller_button_start;
            case SELECT:
                return R.string.controller_button_select;
            case HOME:
                return R.string.controller_button_home;
            case DPAD_UP:
                return R.string.controller_button_dpad_up;
            case DPAD_DOWN:
                return R.string.controller_button_dpad_down;
            case DPAD_LEFT:
                return R.string.controller_button_dpad_left;
            case DPAD_RIGHT:
                return R.string.controller_button_dpad_right;
        }
        throw new AssertionError("Unhandled controller button " + button);
    }

    private ControllerDisplayNames() {}
}
