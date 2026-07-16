package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.domain.ControllerProfile;

public final class ControllerProfileResponseMapper {
    public static AdapterResult mapReadResponse(int resultCode,
            int requestedProfileValue, int activeProfileValue) {
        AdapterResult.Code code = mapResultCode(resultCode);
        ControllerProfile requested = ControllerProfileServiceContract.fromServiceProfile(
                requestedProfileValue);
        ControllerProfile actual = ControllerProfileServiceContract.fromServiceProfile(
                activeProfileValue);
        if (code != AdapterResult.Code.OK) {
            return AdapterResult.forRequest(code, requested, detail(code));
        }
        if (actual == null) {
            return AdapterResult.of(AdapterResult.Code.INVALID_PROFILE,
                    "Controller service returned OK without a valid active profile.");
        }
        return AdapterResult.withProfiles(code, actual, requested, detail(code));
    }

    public static AdapterResult mapSetResponse(int resultCode,
            int requestedProfileValue, int activeProfileValue,
            ControllerProfile requested) {
        if (requested == null) {
            return AdapterResult.of(AdapterResult.Code.INVALID_PROFILE,
                    "Controller profile is required.");
        }

        AdapterResult.Code code = mapResultCode(resultCode);
        ControllerProfile actual = ControllerProfileServiceContract.fromServiceProfile(
                activeProfileValue);
        int expectedRequested;
        try {
            expectedRequested = ControllerProfileServiceContract.toServiceProfile(requested);
        } catch (IllegalArgumentException exception) {
            return AdapterResult.forRequest(AdapterResult.Code.INVALID_PROFILE, requested,
                    "Controller profile is invalid.");
        }
        if (code != AdapterResult.Code.OK) {
            return AdapterResult.forRequest(code, requested, detail(code));
        }
        if (requestedProfileValue != expectedRequested) {
            return AdapterResult.withProfiles(AdapterResult.Code.INVALID_PROFILE,
                    actual, requested,
                    "Controller service returned a mismatched requested profile.");
        }
        return AdapterResult.withProfiles(code, actual, requested, detail(code));
    }

    public static AdapterResult confirmReadback(AdapterResult setResult,
            AdapterResult readResult) {
        if (setResult == null || readResult == null) {
            return AdapterResult.of(AdapterResult.Code.UNAVAILABLE,
                    "Controller profile confirmation is unavailable.");
        }
        if (setResult.code != AdapterResult.Code.OK) {
            ControllerProfile actual = readResult.hasProfile()
                    ? readResult.actualProfile
                    : setResult.actualProfile;
            return AdapterResult.withProfiles(setResult.code, actual,
                    setResult.requestedProfile, setResult.detail);
        }
        if (readResult.code != AdapterResult.Code.OK || !readResult.hasProfile()) {
            return readResult;
        }
        if (setResult.requestedProfile == null) {
            return AdapterResult.of(AdapterResult.Code.INVALID_PROFILE,
                    "Requested controller profile is unavailable.");
        }
        if (readResult.actualProfile != setResult.requestedProfile) {
            return AdapterResult.withProfiles(AdapterResult.Code.READBACK_MISMATCH,
                    readResult.actualProfile, setResult.requestedProfile,
                    "Controller service did not confirm the requested profile.");
        }
        return AdapterResult.withProfiles(AdapterResult.Code.OK,
                readResult.actualProfile, setResult.requestedProfile,
                "Controller profile confirmed.");
    }

    private static AdapterResult.Code mapResultCode(int resultCode) {
        switch (resultCode) {
            case ControllerProfileServiceContract.RESULT_OK:
                return AdapterResult.Code.OK;
            case ControllerProfileServiceContract.RESULT_UNSUPPORTED_DEVICE:
                return AdapterResult.Code.UNSUPPORTED_DEVICE;
            case ControllerProfileServiceContract.RESULT_INVALID_PROFILE:
                return AdapterResult.Code.INVALID_PROFILE;
            case ControllerProfileServiceContract.RESULT_BUSY:
                return AdapterResult.Code.BUSY;
            case ControllerProfileServiceContract.RESULT_STORE_READ_FAILED:
                return AdapterResult.Code.STORE_READ_FAILED;
            case ControllerProfileServiceContract.RESULT_STORE_WRITE_FAILED:
                return AdapterResult.Code.STORE_WRITE_FAILED;
            case ControllerProfileServiceContract.RESULT_NOT_INITIALIZED:
                return AdapterResult.Code.NOT_INITIALIZED;
            default:
                return AdapterResult.Code.UNAVAILABLE;
        }
    }

    private static String detail(AdapterResult.Code code) {
        switch (code) {
            case OK:
                return "Controller profile response is OK.";
            case UNSUPPORTED_DEVICE:
                return "Controller service does not support this device.";
            case INVALID_PROFILE:
                return "Controller service rejected the profile.";
            case BUSY:
                return "Controller service is busy.";
            case STORE_READ_FAILED:
                return "Controller service could not read the profile store.";
            case STORE_WRITE_FAILED:
                return "Controller service could not write the profile store.";
            case NOT_INITIALIZED:
                return "Controller service is not initialized.";
            case UNAVAILABLE:
            case READBACK_MISMATCH:
            case APPLIED:
            case UNKNOWN_DEVICE:
            case ADAPTER_UNAVAILABLE:
            case UNSUPPORTED_CAPABILITY:
            case REJECTED:
                return "Controller profile response is unavailable.";
        }
        return "Controller profile response is unavailable.";
    }

    private ControllerProfileResponseMapper() {}
}
