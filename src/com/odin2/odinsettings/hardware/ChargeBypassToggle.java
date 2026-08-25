package com.odin2.odinsettings.hardware;

/**
 * What a quick settings toggle should do to the charge mode.
 *
 * <p>The decision that needs stating: turning the tile off has to land
 * somewhere, and neither obvious answer is right on its own. Always landing on
 * OFF silently discards a limit the owner had configured. Always landing on
 * LIMIT imposes a restriction they may never have asked for. So the mode that
 * was in effect when the tile was switched on is remembered and restored, and
 * OFF is only the fallback for when there is nothing remembered.
 */
public final class ChargeBypassToggle {
    /** The mode to apply, and what the caller should remember afterwards. */
    public static final class Decision {
        public final ChargeMode target;
        /** Null means forget whatever was remembered. */
        public final ChargeMode remember;

        private Decision(ChargeMode target, ChargeMode remember) {
            this.target = target;
            this.remember = remember;
        }
    }

    private ChargeBypassToggle() {}

    public static Decision toggle(ChargeMode current, ChargeMode remembered) {
        if (current == null) {
            throw new IllegalArgumentException("A current charge mode is required");
        }
        if (current == ChargeMode.BYPASS) {
            // Restoring BYPASS would make the tile do nothing, so a stale
            // memory of it is treated as no memory at all.
            ChargeMode restored = remembered == null || remembered == ChargeMode.BYPASS
                    ? ChargeMode.OFF
                    : remembered;
            return new Decision(restored, null);
        }
        return new Decision(ChargeMode.BYPASS, current);
    }
}
