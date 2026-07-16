package com.odin2.odinsettings.platform;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.ayn.controller.ControllerProfileResponse;
import com.ayn.controller.IOdinController;
import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.hardware.AdapterCapability;
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.ControllerProfileResponseMapper;
import com.odin2.odinsettings.hardware.ControllerProfileServiceContract;
import com.odin2.odinsettings.hardware.HardwareAdapter;
import com.odin2.odinsettings.policy.DeviceIdentity;

import java.util.EnumSet;

public final class AidlControllerHardwareAdapter implements HardwareAdapter {
    private static final String SERVICE_NAME =
            "com.ayn.controller.IOdinController/default";
    private static final AidlControllerHardwareAdapter INSTANCE =
            new AidlControllerHardwareAdapter();

    public static AidlControllerHardwareAdapter getInstance() {
        return INSTANCE;
    }

    @Override
    public AdapterStatus status() {
        return AdapterStatus.available("Controller Binder client installed.",
                EnumSet.of(AdapterCapability.CONTROLLER_PROFILE));
    }

    @Override
    public AdapterResult readControllerProfile(DeviceIdentity identity) {
        IOdinController service = connect();
        if (service == null) {
            return unavailable("Controller service is unavailable.");
        }
        try {
            ControllerProfileResponse getResponse = service.getProfile();
            if (getResponse == null) {
                return unavailable("Controller service returned no profile response.");
            }
            return ControllerProfileResponseMapper.mapReadResponse(
                    getResponse.result,
                    getResponse.requestedProfile,
                    getResponse.activeProfile);
        } catch (RemoteException | RuntimeException exception) {
            return unavailable("Controller profile read failed.");
        }
    }

    @Override
    public AdapterResult applyControllerProfile(DeviceIdentity identity,
            ControllerProfile profile) {
        final int serviceProfile;
        try {
            serviceProfile = ControllerProfileServiceContract.toServiceProfile(profile);
        } catch (IllegalArgumentException exception) {
            return AdapterResult.forRequest(AdapterResult.Code.INVALID_PROFILE, profile,
                    "Controller profile is invalid.");
        }

        IOdinController service = connect();
        if (service == null) {
            return AdapterResult.forRequest(AdapterResult.Code.UNAVAILABLE, profile,
                    "Controller service is unavailable.");
        }
        final AdapterResult setResult;
        try {
            ControllerProfileResponse setResponse = service.setProfile(serviceProfile);
            if (setResponse == null) {
                return AdapterResult.forRequest(AdapterResult.Code.UNAVAILABLE, profile,
                        "Controller service returned no set response.");
            }
            setResult = ControllerProfileResponseMapper.mapSetResponse(
                    setResponse.result,
                    setResponse.requestedProfile,
                    setResponse.activeProfile,
                    profile);
        } catch (RemoteException | RuntimeException exception) {
            return AdapterResult.forRequest(AdapterResult.Code.UNAVAILABLE, profile,
                    "Controller profile result is unavailable.");
        }
        try {
            ControllerProfileResponse getResponse = service.getProfile();
            if (getResponse == null) {
                return setResult.code == AdapterResult.Code.OK
                        ? AdapterResult.forRequest(AdapterResult.Code.UNAVAILABLE, profile,
                                "Controller profile confirmation is unavailable.")
                        : setResult;
            }
            AdapterResult readResult = ControllerProfileResponseMapper.mapReadResponse(
                    getResponse.result,
                    getResponse.requestedProfile,
                    getResponse.activeProfile);
            return ControllerProfileResponseMapper.confirmReadback(setResult, readResult);
        } catch (RemoteException | RuntimeException exception) {
            return setResult.code == AdapterResult.Code.OK
                    ? AdapterResult.forRequest(AdapterResult.Code.UNAVAILABLE, profile,
                            "Controller profile confirmation is unavailable.")
                    : setResult;
        }
    }

    @Override
    public AdapterResult applyExternalDisplayPolicy(DeviceIdentity identity,
            ExternalDisplayPolicy policy) {
        return AdapterResult.of(AdapterResult.Code.UNSUPPORTED_CAPABILITY,
                "Controller adapter does not manage external displays.");
    }

    private static IOdinController connect() {
        final IBinder binder;
        try {
            binder = ServiceManager.checkService(SERVICE_NAME);
        } catch (RuntimeException exception) {
            return null;
        }
        return binder == null ? null : IOdinController.Stub.asInterface(binder);
    }

    private static AdapterResult unavailable(String detail) {
        return AdapterResult.of(AdapterResult.Code.UNAVAILABLE, detail);
    }

    private AidlControllerHardwareAdapter() {}
}
