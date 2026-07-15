package com.odin2.odinsettings.service;

public final class ControllerColdBootPolicy {
    public enum Decision {
        CYCLE_DISPLAY_ONCE,
        SKIP_UNKNOWN_DEVICE,
        SKIP_CONTROLLER_PRESENT,
        SKIP_ATTEMPT_CONSUMED,
    }

    public Decision decide(boolean recognizedDevice, boolean controllerPresent,
            boolean attemptConsumed) {
        if (!recognizedDevice) {
            return Decision.SKIP_UNKNOWN_DEVICE;
        }
        if (controllerPresent) {
            return Decision.SKIP_CONTROLLER_PRESENT;
        }
        if (attemptConsumed) {
            return Decision.SKIP_ATTEMPT_CONSUMED;
        }
        return Decision.CYCLE_DISPLAY_ONCE;
    }
}
