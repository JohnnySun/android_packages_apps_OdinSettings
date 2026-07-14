package com.odin2.odinsettings.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public final class ControllerProfiles {
    public static final String STANDARD_ID = "standard";
    public static final String FLIPPED_FACE_ID = "flipped_face";

    public static final ControllerProfile STANDARD = new ControllerProfile(
            STANDARD_ID,
            identityMappings());

    public static final ControllerProfile FLIPPED_FACE = new ControllerProfile(
            FLIPPED_FACE_ID,
            flippedFaceMappings());

    public static final List<ControllerProfile> ALL = Collections.unmodifiableList(
            Arrays.asList(STANDARD, FLIPPED_FACE));

    public static ControllerProfile findOrDefault(String id) {
        for (ControllerProfile profile : ALL) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return STANDARD;
    }

    private static EnumMap<ControllerButton, ControllerButton> identityMappings() {
        EnumMap<ControllerButton, ControllerButton> mappings =
                new EnumMap<>(ControllerButton.class);
        for (ControllerButton button : ControllerButton.values()) {
            mappings.put(button, button);
        }
        return mappings;
    }

    private static EnumMap<ControllerButton, ControllerButton> flippedFaceMappings() {
        EnumMap<ControllerButton, ControllerButton> mappings = identityMappings();
        mappings.put(ControllerButton.A, ControllerButton.B);
        mappings.put(ControllerButton.B, ControllerButton.A);
        mappings.put(ControllerButton.X, ControllerButton.Y);
        mappings.put(ControllerButton.Y, ControllerButton.X);
        return mappings;
    }

    private ControllerProfiles() {}
}
