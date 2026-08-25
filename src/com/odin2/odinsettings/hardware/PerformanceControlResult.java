package com.odin2.odinsettings.hardware;

/** What the performance service reported, or why it could not be reached. */
public final class PerformanceControlResult {
    public enum Code {
        AVAILABLE,
        UNSUPPORTED,
        UNEXPECTED_PATHS,
        NOT_INITIALIZED,
        UNAVAILABLE,
        INVALID_MODE,
        READ_FAILED,
        WRITE_FAILED,
        READBACK_FAILED,
        ROLLBACK_FAILED,
        MODE_UNAVAILABLE,
    }

    public final Code code;
    /** Null when the service could not report one. */
    public final PerformanceMode mode;

    private PerformanceControlResult(Code code, PerformanceMode mode) {
        this.code = code;
        this.mode = mode;
    }

    public static PerformanceControlResult available(PerformanceMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("An available result needs a mode");
        }
        return new PerformanceControlResult(Code.AVAILABLE, mode);
    }

    public static PerformanceControlResult failure(Code code, PerformanceMode mode) {
        if (code == null || code == Code.AVAILABLE) {
            throw new IllegalArgumentException("A failure needs a failure code");
        }
        return new PerformanceControlResult(code, mode);
    }

    public boolean isAvailable() {
        return code == Code.AVAILABLE;
    }
}
