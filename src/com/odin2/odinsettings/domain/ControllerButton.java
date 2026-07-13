package com.odin2.odinsettings.domain;

public enum ControllerButton {
    A("A"),
    B("B"),
    X("X"),
    Y("Y"),
    LEFT_BUMPER("Left bumper"),
    RIGHT_BUMPER("Right bumper"),
    LEFT_TRIGGER("Left trigger"),
    RIGHT_TRIGGER("Right trigger"),
    LEFT_STICK("Left stick"),
    RIGHT_STICK("Right stick"),
    START("Start"),
    SELECT("Select"),
    HOME("Home"),
    DPAD_UP("D-pad up"),
    DPAD_DOWN("D-pad down"),
    DPAD_LEFT("D-pad left"),
    DPAD_RIGHT("D-pad right");

    public final String displayName;

    ControllerButton(String displayName) {
        this.displayName = displayName;
    }
}
