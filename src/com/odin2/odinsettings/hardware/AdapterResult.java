package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.domain.ControllerProfile;

public final class AdapterResult {
    public enum Code {
        OK,
        UNSUPPORTED_DEVICE,
        INVALID_PROFILE,
        BUSY,
        STORE_READ_FAILED,
        STORE_WRITE_FAILED,
        NOT_INITIALIZED,
        UNAVAILABLE,
        READBACK_MISMATCH,
        APPLIED,
        UNKNOWN_DEVICE,
        ADAPTER_UNAVAILABLE,
        UNSUPPORTED_CAPABILITY,
        REJECTED
    }

    public final Code code;
    public final ControllerProfile actualProfile;
    public final ControllerProfile requestedProfile;
    public final String detail;

    private AdapterResult(Code code, ControllerProfile actualProfile,
            ControllerProfile requestedProfile, String detail) {
        this.code = code;
        this.actualProfile = actualProfile;
        this.requestedProfile = requestedProfile;
        this.detail = detail;
    }

    public static AdapterResult of(Code code, String detail) {
        return new AdapterResult(code, null, null, detail);
    }

    public static AdapterResult withProfile(Code code, ControllerProfile actualProfile,
            String detail) {
        return new AdapterResult(code, actualProfile, null, detail);
    }

    public static AdapterResult forRequest(Code code, ControllerProfile requestedProfile,
            String detail) {
        return new AdapterResult(code, null, requestedProfile, detail);
    }

    public static AdapterResult withProfiles(Code code, ControllerProfile actualProfile,
            ControllerProfile requestedProfile, String detail) {
        return new AdapterResult(code, actualProfile, requestedProfile, detail);
    }

    public boolean wasApplied() {
        return code == Code.OK || code == Code.APPLIED;
    }

    public boolean hasProfile() {
        return actualProfile != null;
    }
}
