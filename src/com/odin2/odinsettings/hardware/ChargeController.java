package com.odin2.odinsettings.hardware;

/** The seam the settings screen and the tile talk to, so neither touches Binder. */
public interface ChargeController {
    ChargeControlResult read();

    ChargeControlResult apply(ChargeMode mode);

    ChargeControlResult applyThresholds(ChargeThresholds thresholds);
}
