package com.odin2.odinsettings.service;

public final class ControllerColdBootTiming {
    public static final long BROADCAST_DEADLINE_MILLIS = 10_000;
    public static final long START_DELAY_MILLIS = 1_000;
    public static final long DISPLAY_OFF_MILLIS = 1_000;
    public static final long PUBLICATION_SETTLE_MILLIS = 3_000;
    public static final long WAKE_LOCK_TIMEOUT_MILLIS = 8_000;

    private ControllerColdBootTiming() {}

    public static long totalCycleMillis() {
        return START_DELAY_MILLIS + DISPLAY_OFF_MILLIS + PUBLICATION_SETTLE_MILLIS;
    }
}
