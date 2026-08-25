package com.odin2.odinsettings.hardware;

/**
 * The three modes the charge daemon accepts.
 *
 * <p>BYPASS is named for what it does rather than for the technique. The
 * daemon sets the charge current to zero while the adapter stays attached; it
 * does not route adapter power around the pack, so a load heavier than the
 * adapter can supply still draws from the battery.
 */
public enum ChargeMode {
    OFF("off", 0),
    LIMIT("limit", 1),
    BYPASS("bypass", 2);

    public final String preferenceValue;
    public final int serviceValue;

    ChargeMode(String preferenceValue, int serviceValue) {
        this.preferenceValue = preferenceValue;
        this.serviceValue = serviceValue;
    }

    public static ChargeMode fromPreferenceValue(String value) {
        for (ChargeMode mode : values()) {
            if (mode.preferenceValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown charge mode");
    }

    public static ChargeMode fromServiceValue(int value) {
        for (ChargeMode mode : values()) {
            if (mode.serviceValue == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown charge service mode");
    }
}
