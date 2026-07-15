package com.odin2.odinsettings.domain;

public final class ControllerAxisNormalizer {
    public static float centered(float value, float min, float max, float flat) {
        if (max <= min) {
            return 0.0f;
        }
        float center = (min + max) / 2.0f;
        if (Math.abs(value - center) <= Math.max(0.0f, flat)) {
            return 0.0f;
        }
        return clamp((value - center) / ((max - min) / 2.0f), -1.0f, 1.0f);
    }

    public static float trigger(float value, float min, float max, float flat) {
        if (max <= min || value <= min + Math.max(0.0f, flat)) {
            return 0.0f;
        }
        return clamp((value - min) / (max - min), 0.0f, 1.0f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private ControllerAxisNormalizer() {}
}
