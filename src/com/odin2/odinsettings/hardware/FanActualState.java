package com.odin2.odinsettings.hardware;

public enum FanActualState {
    OFF,
    QUIET,
    SPORT,
    UNRECOGNIZED;

    static FanActualState classify(int state, int pwmHighTimeNs, int tachPulsesTimes300) {
        if (state == 0 && pwmHighTimeNs == 10000 && tachPulsesTimes300 == 0) {
            return OFF;
        }
        if (state != 1 || tachPulsesTimes300 <= 0) {
            return UNRECOGNIZED;
        }
        if (pwmHighTimeNs == 5000) {
            return QUIET;
        }
        if (pwmHighTimeNs == 25000) {
            return SPORT;
        }
        return UNRECOGNIZED;
    }
}
