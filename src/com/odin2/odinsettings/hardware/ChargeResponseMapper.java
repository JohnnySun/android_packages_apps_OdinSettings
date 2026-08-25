package com.odin2.odinsettings.hardware;

/**
 * Turns the charge daemon's plain integers into something the UI can act on.
 *
 * <p>Free of AIDL types on purpose, like {@link FanResponseMapper}, so the host
 * tests compile it without an Android classpath. The translation from the
 * generated parcelable happens in the platform layer.
 */
public final class ChargeResponseMapper {
    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED_DEVICE = 1;
    public static final int RESULT_UNEXPECTED_PATHS = 2;
    public static final int RESULT_INVALID_MODE = 3;
    public static final int RESULT_IO_ERROR = 4;
    public static final int RESULT_CAPACITY_UNAVAILABLE = 5;

    public static final int MODE_UNKNOWN = -1;

    public static final int RESTRICTED_UNKNOWN = -1;
    public static final int RESTRICTED_NO = 0;
    public static final int RESTRICTED_YES = 1;

    private ChargeResponseMapper() {}

    /** serviceMode is the mode actually in effect, not the one requested. */
    public static ChargeControlResult map(int result, int serviceMode, int restricted,
            int capacityPercent, int stopPercent, int resumePercent, ChargeMode requested) {
        ChargeMode reported = null;
        try {
            reported = ChargeMode.fromServiceValue(serviceMode);
        } catch (IllegalArgumentException ignored) {
            // A mode this build does not know is reported as unavailable rather
            // than guessed at.
        }

        switch (result) {
            case RESULT_OK:
                return reported == null
                        ? ChargeControlResult.failure(
                                ChargeControlResult.Code.UNAVAILABLE, requested)
                        : ChargeControlResult.available(reported, restriction(restricted),
                                capacityPercent, stopPercent, resumePercent);
            case RESULT_UNSUPPORTED_DEVICE:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.UNSUPPORTED, reported);
            case RESULT_UNEXPECTED_PATHS:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.UNEXPECTED_PATHS, reported);
            case RESULT_INVALID_MODE:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.INVALID_MODE, reported);
            case RESULT_IO_ERROR:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.IO_ERROR, reported);
            case RESULT_CAPACITY_UNAVAILABLE:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.CAPACITY_UNAVAILABLE, reported);
            default:
                return ChargeControlResult.failure(
                        ChargeControlResult.Code.UNAVAILABLE, reported);
        }
    }

    private static ChargeControlResult.Restriction restriction(int restricted) {
        switch (restricted) {
            case RESTRICTED_YES:
                return ChargeControlResult.Restriction.HOLDING;
            case RESTRICTED_NO:
                return ChargeControlResult.Restriction.NOT_HOLDING;
            default:
                return ChargeControlResult.Restriction.UNKNOWN;
        }
    }
}
