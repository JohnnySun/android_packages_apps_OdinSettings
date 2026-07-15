package com.odin2.odinsettings.platform;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.ayn.fan.FanResponse;
import com.ayn.fan.IOdinFan;
import com.odin2.odinsettings.hardware.FanControlResult;
import com.odin2.odinsettings.hardware.FanController;
import com.odin2.odinsettings.hardware.FanMode;
import com.odin2.odinsettings.hardware.FanResponseMapper;
import com.odin2.odinsettings.hardware.FanServiceConnection;

public final class AidlFanController implements FanController {
    private static final String SERVICE_NAME = "com.ayn.fan.IOdinFan/default";
    private static final IBinder OWNER_TOKEN = new Binder();
    private static final AidlFanController INSTANCE = new AidlFanController();

    private final FanServiceConnection<IOdinFan> connection =
            new FanServiceConnection<>(new ServiceConnector());

    public static AidlFanController getInstance() {
        return INSTANCE;
    }

    @Override
    public FanControlResult read() {
        return connection.read(new FanServiceConnection.Call<IOdinFan>() {
            @Override
            public FanControlResult call(IOdinFan service)
                    throws FanServiceConnection.RemoteFailure {
                try {
                    return map(service.getStatus(), null);
                } catch (RemoteException exception) {
                    throw remoteFailure("getStatus failed", exception);
                }
            }
        });
    }

    @Override
    public FanControlResult apply(final FanMode mode) {
        if (mode == null) {
            return FanControlResult.error(FanControlResult.Code.INVALID_MODE, null);
        }
        return connection.write(mode, new FanServiceConnection.Call<IOdinFan>() {
            @Override
            public FanControlResult call(IOdinFan service)
                    throws FanServiceConnection.RemoteFailure {
                try {
                    return map(service.setMode(mode.serviceValue, OWNER_TOKEN), mode);
                } catch (RemoteException exception) {
                    throw remoteFailure("setMode outcome is unknown", exception);
                }
            }
        });
    }

    private static FanControlResult map(FanResponse response, FanMode requestedMode) {
        if (response == null) {
            return FanControlResult.error(FanControlResult.Code.MALFORMED, requestedMode);
        }
        return FanResponseMapper.map(response.result, response.mode, response.state,
                response.duty, response.tach, requestedMode);
    }

    private static FanServiceConnection.RemoteFailure remoteFailure(
            String message, Throwable cause) {
        return new FanServiceConnection.RemoteFailure(message, cause);
    }

    private static final class ServiceConnector
            implements FanServiceConnection.Connector<IOdinFan> {
        @Override
        public IOdinFan connect(
                final FanServiceConnection.DeathListener<IOdinFan> listener)
                throws FanServiceConnection.RemoteFailure {
            final IBinder binder;
            try {
                binder = ServiceManager.checkService(SERVICE_NAME);
            } catch (RuntimeException exception) {
                throw remoteFailure("Fan service lookup failed", exception);
            }
            if (binder == null) {
                return null;
            }
            final IOdinFan service = IOdinFan.Stub.asInterface(binder);
            if (service == null) {
                return null;
            }
            try {
                binder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        listener.onServiceDied(service);
                    }
                }, 0);
            } catch (RemoteException exception) {
                throw remoteFailure("Fan service died during lookup", exception);
            }
            return service;
        }
    }

    private AidlFanController() {}
}
