package com.odin2.odinsettings.hardware;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class AdapterStatus {
    public final boolean available;
    public final String detail;
    private final Set<AdapterCapability> capabilities;

    private AdapterStatus(boolean available, String detail,
            Set<AdapterCapability> capabilities) {
        this.available = available;
        this.detail = detail;
        this.capabilities = Collections.unmodifiableSet(
                capabilities.isEmpty()
                        ? EnumSet.noneOf(AdapterCapability.class)
                        : EnumSet.copyOf(capabilities));
    }

    public static AdapterStatus unavailable(String detail) {
        return new AdapterStatus(false, detail, EnumSet.noneOf(AdapterCapability.class));
    }

    public static AdapterStatus available(String detail, Set<AdapterCapability> capabilities) {
        return new AdapterStatus(true, detail, capabilities);
    }

    public boolean supports(AdapterCapability capability) {
        return capabilities.contains(capability);
    }
}
