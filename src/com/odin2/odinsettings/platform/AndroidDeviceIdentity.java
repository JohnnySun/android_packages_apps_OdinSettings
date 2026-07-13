package com.odin2.odinsettings.platform;

import android.os.Build;

import com.odin2.odinsettings.policy.DeviceIdentity;

public final class AndroidDeviceIdentity {
    public static DeviceIdentity current() {
        return new DeviceIdentity(Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.SOC_MODEL);
    }

    private AndroidDeviceIdentity() {}
}
