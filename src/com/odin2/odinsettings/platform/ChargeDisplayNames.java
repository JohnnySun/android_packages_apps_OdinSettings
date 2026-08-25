package com.odin2.odinsettings.platform;

import com.odin2.odinsettings.R;
import com.odin2.odinsettings.hardware.ChargeMode;

/** One place for the charge mode labels, shared by the settings row and the tile. */
public final class ChargeDisplayNames {
    public static int modeName(ChargeMode mode) {
        switch (mode) {
            case OFF:
                return R.string.charge_mode_off;
            case LIMIT:
                return R.string.charge_mode_limit;
            case BYPASS:
                return R.string.charge_mode_bypass;
        }
        throw new AssertionError("Unhandled charge mode " + mode);
    }

    private ChargeDisplayNames() {}
}
