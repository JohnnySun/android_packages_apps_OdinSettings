package com.odin2.odinsettings.hardware;

public final class FanResponseMapper {
    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED_DEVICE = 1;
    public static final int RESULT_UNEXPECTED_PATHS = 2;
    public static final int RESULT_INVALID_MODE = 3;
    public static final int RESULT_INVALID_OWNER = 4;
    public static final int RESULT_IO_ERROR = 5;
    public static final int RESULT_TACH_TIMEOUT = 6;
    public static final int RESULT_DISABLE_UNCONFIRMED = 7;
    public static final int RESULT_NOT_OWNER = 8;

    public static FanControlResult map(int resultCode, int responseMode, int state,
            int duty, int tach, FanMode requestedMode) {
        if (resultCode == RESULT_OK) {
            return mapSuccess(responseMode, state, duty, tach, requestedMode);
        }
        if (state != -1 || duty != -1 || tach != -1) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        if (!isValidErrorMode(responseMode, requestedMode)) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        return FanControlResult.error(mapError(resultCode, requestedMode), requestedMode);
    }

    private static FanControlResult mapSuccess(int responseMode, int state, int duty,
            int tach, FanMode requestedMode) {
        final FanMode actualMode;
        try {
            actualMode = FanMode.fromServiceValue(responseMode);
        } catch (IllegalArgumentException exception) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        if (requestedMode != null && actualMode != requestedMode) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        if (!isCompleteSnapshot(actualMode, state, duty, tach)) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        return FanControlResult.available(requestedMode, state, duty, tach);
    }

    private static boolean isCompleteSnapshot(FanMode mode, int state, int duty, int tach) {
        switch (mode) {
            case OFF:
                return state == 0 && duty == 10000 && tach == 0;
            case QUIET:
                return state == 1 && duty == 5000 && tach > 0;
            case SPORT:
                return state == 1 && duty == 25000 && tach > 0;
        }
        return false;
    }

    private static boolean isValidErrorMode(int responseMode, FanMode requestedMode) {
        if (requestedMode != null) {
            return responseMode == requestedMode.serviceValue;
        }
        if (responseMode == -1) {
            return true;
        }
        try {
            FanMode.fromServiceValue(responseMode);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static FanControlResult.Code mapError(int resultCode, FanMode requestedMode) {
        switch (resultCode) {
            case RESULT_UNSUPPORTED_DEVICE:
                return FanControlResult.Code.UNSUPPORTED;
            case RESULT_UNEXPECTED_PATHS:
                return FanControlResult.Code.UNEXPECTED_PATHS;
            case RESULT_INVALID_MODE:
                return FanControlResult.Code.INVALID_MODE;
            case RESULT_TACH_TIMEOUT:
            case RESULT_DISABLE_UNCONFIRMED:
                return FanControlResult.Code.READBACK_MISMATCH;
            case RESULT_IO_ERROR:
                return requestedMode == null
                        ? FanControlResult.Code.UNAVAILABLE
                        : FanControlResult.Code.WRITE_FAILED;
            case RESULT_INVALID_OWNER:
            case RESULT_NOT_OWNER:
                return FanControlResult.Code.UNAVAILABLE;
            default:
                return FanControlResult.Code.MALFORMED;
        }
    }

    private FanResponseMapper() {}
}
