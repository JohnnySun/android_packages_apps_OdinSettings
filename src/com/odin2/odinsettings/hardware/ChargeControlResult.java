package com.odin2.odinsettings.hardware;

/**
 * What the charge daemon reported, or why it could not be reached.
 *
 * <p>Unlike the fan and performance results this one carries a payload, because
 * the whole point of the row is to say what the battery is actually doing: the
 * capacity, whether charging is being held off right now, and the two
 * thresholds the daemon is working to.
 */
public final class ChargeControlResult {
    public enum Code {
        AVAILABLE,
        UNSUPPORTED,
        UNEXPECTED_PATHS,
        INVALID_MODE,
        IO_ERROR,
        CAPACITY_UNAVAILABLE,
        UNAVAILABLE,
    }

    /** Tri-state, because "the daemon could not tell" is not "not holding". */
    public enum Restriction {
        UNKNOWN,
        HOLDING,
        NOT_HOLDING,
    }

    public static final int UNKNOWN_PERCENT = -1;

    public final Code code;
    /** Null when the service could not report one. */
    public final ChargeMode mode;
    public final Restriction restriction;
    /** {@link #UNKNOWN_PERCENT} when the daemon could not read it. */
    public final int capacityPercent;
    public final int stopPercent;
    public final int resumePercent;

    private ChargeControlResult(Code code, ChargeMode mode, Restriction restriction,
            int capacityPercent, int stopPercent, int resumePercent) {
        this.code = code;
        this.mode = mode;
        this.restriction = restriction == null ? Restriction.UNKNOWN : restriction;
        this.capacityPercent = normalize(capacityPercent);
        this.stopPercent = normalize(stopPercent);
        this.resumePercent = normalize(resumePercent);
    }

    public static ChargeControlResult available(ChargeMode mode, Restriction restriction,
            int capacityPercent, int stopPercent, int resumePercent) {
        if (mode == null) {
            throw new IllegalArgumentException("An available result needs a mode");
        }
        return new ChargeControlResult(Code.AVAILABLE, mode, restriction, capacityPercent,
                stopPercent, resumePercent);
    }

    /**
     * A failure carries no payload. A partially-read snapshot presented as a
     * reading is worse than no reading, so the fields are cleared rather than
     * passed through.
     */
    public static ChargeControlResult failure(Code code, ChargeMode mode) {
        if (code == null || code == Code.AVAILABLE) {
            throw new IllegalArgumentException("A failure needs a failure code");
        }
        return new ChargeControlResult(code, mode, Restriction.UNKNOWN, UNKNOWN_PERCENT,
                UNKNOWN_PERCENT, UNKNOWN_PERCENT);
    }

    public boolean isAvailable() {
        return code == Code.AVAILABLE;
    }

    public boolean hasCapacity() {
        return capacityPercent != UNKNOWN_PERCENT;
    }

    public boolean hasThresholds() {
        return stopPercent != UNKNOWN_PERCENT && resumePercent != UNKNOWN_PERCENT;
    }

    private static int normalize(int percent) {
        return percent < 0 || percent > 100 ? UNKNOWN_PERCENT : percent;
    }
}
