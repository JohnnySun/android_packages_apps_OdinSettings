package com.odin2.odinsettings.hardware;

/**
 * A stop and resume pair, and the rules the daemon will hold it to.
 *
 * <p>The bounds mirror `charge_policy.h` deliberately. Duplicating them here
 * means a pair the daemon would refuse never leaves the UI, so the row never
 * shows a limit that was rejected. The daemon still validates: this is a
 * courtesy, not the authority.
 */
public final class ChargeThresholds {
    public static final int MINIMUM_STOP_PERCENT = 50;
    public static final int MAXIMUM_STOP_PERCENT = 99;
    public static final int MINIMUM_HYSTERESIS_PERCENT = 2;
    /** Below this the daemon always releases, so a resume under it is a lie. */
    public static final int NEVER_RESTRICT_BELOW_PERCENT = 40;

    public final int stopPercent;
    public final int resumePercent;

    private ChargeThresholds(int stopPercent, int resumePercent) {
        this.stopPercent = stopPercent;
        this.resumePercent = resumePercent;
    }

    public static boolean isValid(int stopPercent, int resumePercent) {
        if (stopPercent < MINIMUM_STOP_PERCENT || stopPercent > MAXIMUM_STOP_PERCENT) {
            return false;
        }
        if (resumePercent < NEVER_RESTRICT_BELOW_PERCENT) {
            return false;
        }
        return stopPercent - resumePercent >= MINIMUM_HYSTERESIS_PERCENT;
    }

    public static ChargeThresholds of(int stopPercent, int resumePercent) {
        if (!isValid(stopPercent, resumePercent)) {
            throw new IllegalArgumentException("Thresholds the daemon would refuse");
        }
        return new ChargeThresholds(stopPercent, resumePercent);
    }

    /** The preference stores the pair as "stop:resume". */
    public static ChargeThresholds fromPreferenceValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("A threshold value is required");
        }
        final int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Malformed threshold value");
        }
        try {
            return of(Integer.parseInt(value.substring(0, separator)),
                    Integer.parseInt(value.substring(separator + 1)));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Malformed threshold value", malformed);
        }
    }

    public String toPreferenceValue() {
        return stopPercent + ":" + resumePercent;
    }
}
