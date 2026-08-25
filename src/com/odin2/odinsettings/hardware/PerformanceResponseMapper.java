package com.odin2.odinsettings.hardware;

/**
 * Turns the service's plain result and mode integers into something the UI can
 * act on.
 *
 * <p>Deliberately free of AIDL types, like {@link FanResponseMapper}, so the
 * host tests can compile it without an Android classpath. The translation from
 * the generated parcelable happens in the platform layer.
 */
public final class PerformanceResponseMapper {
    public static final int RESULT_OK = 0;
    public static final int RESULT_UNSUPPORTED_DEVICE = 1;
    public static final int RESULT_UNEXPECTED_PATHS = 2;
    public static final int RESULT_NOT_INITIALIZED = 3;
    public static final int RESULT_ALREADY_INITIALIZED = 4;
    public static final int RESULT_INVALID_MODE = 5;
    public static final int RESULT_READ_FAILED = 6;
    public static final int RESULT_WRITE_FAILED = 7;

    private PerformanceResponseMapper() {}

    public static PerformanceControlResult map(int result, int serviceMode,
            PerformanceMode requested) {
        PerformanceMode reported = null;
        try {
            reported = PerformanceMode.fromServiceValue(serviceMode);
        } catch (IllegalArgumentException ignored) {
            // A mode this build does not know is reported as unavailable rather
            // than guessed at.
        }

        switch (result) {
            case RESULT_OK:
                return reported == null
                        ? PerformanceControlResult.failure(
                                PerformanceControlResult.Code.UNAVAILABLE, requested)
                        : PerformanceControlResult.available(reported);
            case RESULT_UNSUPPORTED_DEVICE:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.UNSUPPORTED, reported);
            case RESULT_UNEXPECTED_PATHS:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.UNEXPECTED_PATHS, reported);
            case RESULT_NOT_INITIALIZED:
            case RESULT_ALREADY_INITIALIZED:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.NOT_INITIALIZED, reported);
            case RESULT_INVALID_MODE:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.INVALID_MODE, reported);
            case RESULT_READ_FAILED:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.READ_FAILED, reported);
            case RESULT_WRITE_FAILED:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.WRITE_FAILED, reported);
            default:
                return PerformanceControlResult.failure(
                        PerformanceControlResult.Code.UNAVAILABLE, reported);
        }
    }
}
