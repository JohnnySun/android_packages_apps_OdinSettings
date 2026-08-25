package com.odin2.odinsettings.hardware;

/**
 * The stock performance modes, as encoded by the performance service.
 *
 * <p>SYSTEM_MANAGED means the daemon holds no opinion and leaves the ten nodes
 * to whatever else owns them, which is the state the device boots into.
 */
public enum PerformanceMode {
    SYSTEM_MANAGED("system", 0),
    STOCK_NORMAL("normal", 1),
    PERFORMANCE("performance", 2),
    HIGH("high", 3);

    public final String preferenceValue;
    public final int serviceValue;

    PerformanceMode(String preferenceValue, int serviceValue) {
        this.preferenceValue = preferenceValue;
        this.serviceValue = serviceValue;
    }

    public static PerformanceMode fromPreferenceValue(String value) {
        for (PerformanceMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown performance mode");
    }

    public static PerformanceMode fromServiceValue(int value) {
        for (PerformanceMode mode : values()) {
            if (mode.serviceValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown performance service mode");
    }
}
