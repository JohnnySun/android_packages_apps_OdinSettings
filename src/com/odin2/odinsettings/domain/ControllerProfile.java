package com.odin2.odinsettings.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ControllerProfile {
    public final String id;
    private final Map<ControllerButton, ControllerButton> mappings;

    ControllerProfile(String id, Map<ControllerButton, ControllerButton> mappings) {
        this.id = Objects.requireNonNull(id);

        EnumMap<ControllerButton, ControllerButton> copy =
                new EnumMap<>(ControllerButton.class);
        copy.putAll(mappings);
        for (ControllerButton button : ControllerButton.values()) {
            if (!copy.containsKey(button)) {
                throw new IllegalArgumentException("Missing mapping for " + button);
            }
        }
        this.mappings = Collections.unmodifiableMap(copy);
    }

    public ControllerButton map(ControllerButton physicalButton) {
        return mappings.get(Objects.requireNonNull(physicalButton));
    }

    public Map<ControllerButton, ControllerButton> mappings() {
        return mappings;
    }
}
