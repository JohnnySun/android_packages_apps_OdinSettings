package com.odin2.odinsettings.hardware;

public enum FanMode {
    OFF("off", 0),
    QUIET("quiet", 1),
    SPORT("sport", 2);

    public final String preferenceValue;
    public final int nativeValue;

    FanMode(String preferenceValue, int nativeValue) {
        this.preferenceValue = preferenceValue;
        this.nativeValue = nativeValue;
    }

    public static FanMode fromPreferenceValue(String value) {
        for (FanMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown fan mode");
    }

    public static FanMode fromNativeValue(int value) {
        for (FanMode mode : values()) {
            if (mode.nativeValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown native fan mode");
    }
}
