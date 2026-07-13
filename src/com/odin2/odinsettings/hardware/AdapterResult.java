package com.odin2.odinsettings.hardware;

public final class AdapterResult {
    public enum Code {
        APPLIED,
        UNKNOWN_DEVICE,
        ADAPTER_UNAVAILABLE,
        UNSUPPORTED_CAPABILITY,
        REJECTED
    }

    public final Code code;
    public final String detail;

    private AdapterResult(Code code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    public static AdapterResult of(Code code, String detail) {
        return new AdapterResult(code, detail);
    }

    public boolean wasApplied() {
        return code == Code.APPLIED;
    }
}
