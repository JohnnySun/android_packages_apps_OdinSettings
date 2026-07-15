package com.odin2.odinsettings.hardware;

public enum FanMode {
    OFF("off", 0),
    QUIET("quiet", 1),
    SPORT("sport", 2);

    public final String preferenceValue;
    public final int serviceValue;

    FanMode(String preferenceValue, int serviceValue) {
        this.preferenceValue = preferenceValue;
        this.serviceValue = serviceValue;
    }

    public static FanMode fromPreferenceValue(String value) {
        for (FanMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown fan mode");
    }

    public static FanMode fromServiceValue(int value) {
        for (FanMode mode : values()) {
            if (mode.serviceValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown fan service mode");
    }
}
