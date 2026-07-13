package com.odin2.odinsettings.service;

import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.hardware.AdapterCapability;
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.HardwareAdapter;
import com.odin2.odinsettings.policy.AccessDecision;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;

public final class ExternalDisplayCoordinator {
    private final HardwareAccessPolicy accessPolicy;
    private final HardwareAdapter adapter;

    public ExternalDisplayCoordinator(HardwareAccessPolicy accessPolicy,
            HardwareAdapter adapter) {
        this.accessPolicy = accessPolicy;
        this.adapter = adapter;
    }

    public AdapterResult apply(DeviceIdentity identity, ExternalDisplayPolicy policy) {
        AccessDecision decision = accessPolicy.evaluate(identity);
        if (!decision.allowed) {
            return AdapterResult.of(AdapterResult.Code.UNKNOWN_DEVICE, decision.reason);
        }

        AdapterStatus status = adapter.status();
        if (!status.available) {
            return AdapterResult.of(AdapterResult.Code.ADAPTER_UNAVAILABLE, status.detail);
        }
        if (!status.supports(AdapterCapability.EXTERNAL_DISPLAY_POLICY)) {
            return AdapterResult.of(
                    AdapterResult.Code.UNSUPPORTED_CAPABILITY,
                    "Adapter does not support external display policy.");
        }
        return adapter.applyExternalDisplayPolicy(identity, policy);
    }
}
