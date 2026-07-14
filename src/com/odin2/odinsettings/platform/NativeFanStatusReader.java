package com.odin2.odinsettings.platform;

import com.odin2.odinsettings.hardware.FanStatusRead;
import com.odin2.odinsettings.hardware.FanStatusReader;

public final class NativeFanStatusReader implements FanStatusReader {
    private static final int RESULT_AVAILABLE = 0;
    private static final int RESULT_UNSUPPORTED = 1;
    private static final int RESULT_UNAVAILABLE = 3;
    private static final boolean NATIVE_LOADED = loadNativeLibrary();

    @Override
    public FanStatusRead read() {
        if (!NATIVE_LOADED) {
            return FanStatusRead.error(FanStatusRead.Code.UNAVAILABLE);
        }

        final int[] result;
        try {
            result = nativeRead();
        } catch (RuntimeException | UnsatisfiedLinkError exception) {
            return FanStatusRead.error(FanStatusRead.Code.UNAVAILABLE);
        }
        if (result == null || result.length != 3) {
            return FanStatusRead.error(FanStatusRead.Code.MALFORMED);
        }

        try {
            switch (result[0]) {
                case RESULT_AVAILABLE:
                    return FanStatusRead.available(result[1], result[2]);
                case RESULT_UNSUPPORTED:
                    return FanStatusRead.error(FanStatusRead.Code.UNSUPPORTED);
                case RESULT_UNAVAILABLE:
                    return FanStatusRead.error(FanStatusRead.Code.UNAVAILABLE);
                default:
                    return FanStatusRead.error(FanStatusRead.Code.MALFORMED);
            }
        } catch (IllegalArgumentException exception) {
            return FanStatusRead.error(FanStatusRead.Code.MALFORMED);
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
}
