package com.odin2.odinsettings.hardware;

public final class FanControlResult {
    public enum Code {
        AVAILABLE,
        UNSUPPORTED,
        UNEXPECTED_PATHS,
        UNAVAILABLE,
        MALFORMED,
        PERIOD_MISMATCH,
        WRITE_FAILED,
        READBACK_MISMATCH,
        INVALID_MODE,
    }

    public final Code code;
    public final FanMode requestedMode;
    public final FanActualState actualState;
    public final int state;
    public final int pwmHighTimeNs;
    public final int tachPulsesTimes300;

    private FanControlResult(Code code, FanMode requestedMode, FanActualState actualState,
            int state, int pwmHighTimeNs, int tachPulsesTimes300) {
        this.code = code;
        this.requestedMode = requestedMode;
        this.actualState = actualState;
        this.state = state;
        this.pwmHighTimeNs = pwmHighTimeNs;
        this.tachPulsesTimes300 = tachPulsesTimes300;
    }

    public static FanControlResult available(FanMode requestedMode, int state,
            int pwmHighTimeNs, int tachPulsesTimes300) {
        if ((state != 0 && state != 1) || pwmHighTimeNs < 0 || tachPulsesTimes300 < 0) {
            throw new IllegalArgumentException("Invalid fan status snapshot");
        }
        return new FanControlResult(Code.AVAILABLE, requestedMode,
                FanActualState.classify(state, pwmHighTimeNs, tachPulsesTimes300), state,
                pwmHighTimeNs, tachPulsesTimes300);
    }

    public static FanControlResult error(Code code, FanMode requestedMode) {
        if (code == null || code == Code.AVAILABLE) {
            throw new IllegalArgumentException("Fan control error code is required");
        }
        return new FanControlResult(code, requestedMode, FanActualState.UNRECOGNIZED,
                -1, -1, -1);
    }

    public boolean hasSnapshot() {
        return code == Code.AVAILABLE;
    }
}
