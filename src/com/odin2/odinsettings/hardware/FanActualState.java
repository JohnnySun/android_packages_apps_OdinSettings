package com.odin2.odinsettings.hardware;

public enum FanActualState {
    OFF,
    QUIET,
    SPORT,
    UNRECOGNIZED;

    static FanActualState classify(FanMode mode, int state, int pwmHighTimeNs,
            int tachPulsesTimes300) {
        if (state == 0 && pwmHighTimeNs == 10000 && tachPulsesTimes300 == 0) {
            return mode == null || mode == FanMode.OFF ? OFF : UNRECOGNIZED;
        }
        if (mode == null) {
            return UNRECOGNIZED;
        }
        if (state != 1 || tachPulsesTimes300 <= 0) {
            return UNRECOGNIZED;
        }
        if (mode == FanMode.QUIET
                && pwmHighTimeNs >= 5000 && pwmHighTimeNs <= 25000) {
            return QUIET;
        }
        if (mode == FanMode.SPORT
                && pwmHighTimeNs >= 8000 && pwmHighTimeNs <= 25000) {
            return SPORT;
        }
        return UNRECOGNIZED;
    }
}
