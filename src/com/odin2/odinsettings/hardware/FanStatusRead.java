package com.odin2.odinsettings.hardware;

public final class FanStatusRead {
    public enum Code {
        AVAILABLE,
        UNSUPPORTED,
        UNAVAILABLE,
        MALFORMED,
    }

    public final Code code;
    public final int state;
    public final int duty;

    private FanStatusRead(Code code, int state, int duty) {
        this.code = code;
        this.state = state;
        this.duty = duty;
    }

    public static FanStatusRead available(int state, int duty) {
        if ((state != 0 && state != 1) || duty < 0) {
            throw new IllegalArgumentException("Invalid fan status snapshot");
        }
        return new FanStatusRead(Code.AVAILABLE, state, duty);
    }

    public static FanStatusRead error(Code code) {
        if (code == null || code == Code.AVAILABLE) {
            throw new IllegalArgumentException("Fan status error code is required");
        }
        return new FanStatusRead(code, -1, -1);
    }

    public boolean hasSnapshot() {
        return code == Code.AVAILABLE;
    }
}
