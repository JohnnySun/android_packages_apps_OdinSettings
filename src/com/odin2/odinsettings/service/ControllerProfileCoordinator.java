package com.odin2.odinsettings.service;

import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.hardware.AdapterCapability;
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.HardwareAdapter;
import com.odin2.odinsettings.policy.AccessDecision;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;

public final class ControllerProfileCoordinator {
    private final HardwareAccessPolicy accessPolicy;
    private final HardwareAdapter adapter;

    public ControllerProfileCoordinator(HardwareAccessPolicy accessPolicy,
            HardwareAdapter adapter) {
        this.accessPolicy = accessPolicy;
        this.adapter = adapter;
    }

    public AdapterResult apply(DeviceIdentity identity, ControllerProfile profile) {
        AdapterResult gate = evaluateAccess(identity);
        if (gate != null) {
            return gate;
        }
        return adapter.applyControllerProfile(identity, profile);
    }

    public AdapterResult read(DeviceIdentity identity) {
        AdapterResult gate = evaluateAccess(identity);
        if (gate != null) {
            return gate;
        }
        return adapter.readControllerProfile(identity);
    }

    private AdapterResult evaluateAccess(DeviceIdentity identity) {
        AccessDecision decision = accessPolicy.evaluate(identity);
        if (!decision.allowed) {
            return AdapterResult.of(AdapterResult.Code.UNKNOWN_DEVICE, decision.reason);
        }

        AdapterStatus status = adapter.status();
        if (!status.available) {
            return AdapterResult.of(AdapterResult.Code.ADAPTER_UNAVAILABLE, status.detail);
        }
        if (!status.supports(AdapterCapability.CONTROLLER_PROFILE)) {
            return AdapterResult.of(
                    AdapterResult.Code.UNSUPPORTED_CAPABILITY,
                    "Adapter does not support controller profiles.");
        }
        return null;
    }
}
