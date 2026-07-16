package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;

public final class ControllerProfileServiceContract {
    public static final int PROFILE_STANDARD = 0;
    public static final int PROFILE_FLIPPED_FACE = 1;

    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED_DEVICE = 1;
    public static final int RESULT_INVALID_PROFILE = 2;
    public static final int RESULT_BUSY = 3;
    public static final int RESULT_STORE_READ_FAILED = 4;
    public static final int RESULT_STORE_WRITE_FAILED = 5;
    public static final int RESULT_NOT_INITIALIZED = 6;

    public static int toServiceProfile(ControllerProfile profile) {
        if (profile == ControllerProfiles.STANDARD) {
            return PROFILE_STANDARD;
        }
        if (profile == ControllerProfiles.FLIPPED_FACE) {
            return PROFILE_FLIPPED_FACE;
        }
        throw new IllegalArgumentException("Unsupported controller profile");
    }

    public static ControllerProfile fromServiceProfile(int profile) {
        switch (profile) {
            case PROFILE_STANDARD:
                return ControllerProfiles.STANDARD;
            case PROFILE_FLIPPED_FACE:
                return ControllerProfiles.FLIPPED_FACE;
            default:
                return null;
        }
    }

    private ControllerProfileServiceContract() {}
}
