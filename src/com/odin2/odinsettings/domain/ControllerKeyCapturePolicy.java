package com.odin2.odinsettings.domain;

public final class ControllerKeyCapturePolicy {
    public static ControllerButton select(ControllerButton scanCodeButton,
            ControllerButton keyCodeButton, boolean controllerEvent) {
        if (scanCodeButton != null) {
            return scanCodeButton;
        }
        if (controllerEvent || keyCodeButton == ControllerButton.BACK) {
            return keyCodeButton;
        }
        return null;
    }

    private ControllerKeyCapturePolicy() {}
}
