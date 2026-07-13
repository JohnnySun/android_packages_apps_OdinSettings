package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.policy.DeviceIdentity;

public interface HardwareAdapter {
    AdapterStatus status();

    AdapterResult applyControllerProfile(DeviceIdentity identity, ControllerProfile profile);

    AdapterResult applyExternalDisplayPolicy(DeviceIdentity identity,
            ExternalDisplayPolicy policy);
}
