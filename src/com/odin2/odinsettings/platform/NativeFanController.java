package com.odin2.odinsettings.platform;

import com.odin2.odinsettings.hardware.FanControlResult;
import com.odin2.odinsettings.hardware.FanController;
import com.odin2.odinsettings.hardware.FanMode;

public final class NativeFanController implements FanController {
    private static final boolean NATIVE_LOADED = loadNativeLibrary();

    @Override
    public FanControlResult read() {
        return callNative(null);
    }

    @Override
    public FanControlResult apply(FanMode mode) {
        if (mode == null) {
            return FanControlResult.error(FanControlResult.Code.INVALID_MODE, null);
        }
        return callNative(mode);
    }

    private static FanControlResult callNative(FanMode requestedMode) {
        if (!NATIVE_LOADED) {
            return FanControlResult.error(FanControlResult.Code.UNAVAILABLE, requestedMode);
        }

        final int[] result;
        try {
            result = requestedMode == null
                    ? nativeRead()
                    : nativeApply(requestedMode.nativeValue);
        } catch (RuntimeException | UnsatisfiedLinkError exception) {
            return FanControlResult.error(FanControlResult.Code.UNAVAILABLE, requestedMode);
        }
        if (result == null || result.length != 5) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }

        try {
            FanMode nativeRequestedMode = result[1] < 0
                    ? null
                    : FanMode.fromNativeValue(result[1]);
            FanControlResult.Code code = codeFromNative(result[0]);
            if (code == FanControlResult.Code.AVAILABLE) {
                return FanControlResult.available(nativeRequestedMode, result[2], result[3],
                        result[4]);
            }
            return FanControlResult.error(code, nativeRequestedMode);
        } catch (IllegalArgumentException exception) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
    }

    private static FanControlResult.Code codeFromNative(int code) {
        switch (code) {
            case 0:
                return FanControlResult.Code.AVAILABLE;
            case 1:
                return FanControlResult.Code.UNSUPPORTED;
            case 2:
                return FanControlResult.Code.UNEXPECTED_PATHS;
            case 3:
                return FanControlResult.Code.UNAVAILABLE;
            case 4:
                return FanControlResult.Code.MALFORMED;
            case 5:
                return FanControlResult.Code.PERIOD_MISMATCH;
            case 6:
                return FanControlResult.Code.WRITE_FAILED;
            case 7:
                return FanControlResult.Code.READBACK_MISMATCH;
            case 8:
                return FanControlResult.Code.INVALID_MODE;
            default:
                throw new IllegalArgumentException("Unknown native fan result");
        }
    }

    private static boolean loadNativeLibrary() {
        try {
            System.loadLibrary("odinsettings_fan_status_jni");
            return true;
        } catch (SecurityException | UnsatisfiedLinkError exception) {
            return false;
        }
    }

    private static native int[] nativeRead();

    private static native int[] nativeApply(int requestedMode);
}
