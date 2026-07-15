package com.odin2.odinsettings.domain;

public final class ControllerScanCodeMapper {
    private static final int BTN_BACK = 278;
    private static final int BTN_EAST = 305;

    public static ControllerButton fromScanCode(int scanCode) {
        switch (scanCode) {
            case BTN_EAST:
                return ControllerButton.B;
            case BTN_BACK:
                return ControllerButton.BACK;
            default:
                return null;
        }
    }

    private ControllerScanCodeMapper() {}
}
