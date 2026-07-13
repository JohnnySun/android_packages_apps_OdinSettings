package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.policy.DeviceIdentity;

public final class DisabledHardwareAdapter implements HardwareAdapter {
    private static final String DETAIL =
            "No validated Android 16 privileged hardware adapter is installed.";

    @Override
    public AdapterStatus status() {
        return AdapterStatus.unavailable(DETAIL);
    }

    @Override
    public AdapterResult applyControllerProfile(DeviceIdentity identity,
            ControllerProfile profile) {
        return AdapterResult.of(AdapterResult.Code.ADAPTER_UNAVAILABLE, DETAIL);
    }

    @Override
    public AdapterResult applyExternalDisplayPolicy(DeviceIdentity identity,
            ExternalDisplayPolicy policy) {
        return AdapterResult.of(AdapterResult.Code.ADAPTER_UNAVAILABLE, DETAIL);
    }
}
