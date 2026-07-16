package com.odin2.odinsettings.service;

public final class ControllerColdBootPolicy {
    public static final long BROADCAST_DEADLINE_MILLIS = 10_000;
    public static final long START_DELAY_MILLIS = 1_000;
    public static final long DISPLAY_OFF_MILLIS = 1_000;
    public static final long PUBLICATION_SETTLE_MILLIS = 3_000;
    public static final long WAKE_LOCK_TIMEOUT_MILLIS = 8_000;

    public enum Decision {
        CYCLE_DISPLAY_ONCE,
        SKIP_UNKNOWN_DEVICE,
        SKIP_ATTEMPT_CONSUMED,
    }

    public Decision decide(boolean recognizedDevice, boolean attemptConsumed) {
        if (!recognizedDevice) {
            return Decision.SKIP_UNKNOWN_DEVICE;
        }
        if (attemptConsumed) {
            return Decision.SKIP_ATTEMPT_CONSUMED;
        }
        return Decision.CYCLE_DISPLAY_ONCE;
    }

    public static long totalCycleMillis() {
        return START_DELAY_MILLIS + DISPLAY_OFF_MILLIS + PUBLICATION_SETTLE_MILLIS;
    }
}
