package com.odin2.odinsettings.hardware;

public interface FanController {
    FanControlResult read();

    FanControlResult apply(FanMode mode);
}
