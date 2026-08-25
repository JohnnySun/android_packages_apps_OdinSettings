package com.odin2.odinsettings.platform;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.ayn.performance.IOdinPerformance;
import com.ayn.performance.PerformanceResponse;
import com.odin2.odinsettings.hardware.PerformanceControlResult;
import com.odin2.odinsettings.hardware.PerformanceController;
import com.odin2.odinsettings.hardware.PerformanceMode;
import com.odin2.odinsettings.hardware.PerformanceResponseMapper;
import com.odin2.odinsettings.hardware.PerformanceServiceConnection;

/** Talks to the private performance daemon over its own Binder instance. */
public final class AidlPerformanceController implements PerformanceController {
    private static final String SERVICE_NAME = "com.ayn.performance.IOdinPerformance/default";
    private static final AidlPerformanceController INSTANCE = new AidlPerformanceController();

    private final PerformanceServiceConnection<IOdinPerformance> connection =
            new PerformanceServiceConnection<>(new ServiceConnector());

    public static AidlPerformanceController getInstance() {
        return INSTANCE;
    }

    @Override
    public PerformanceControlResult read() {
        return connection.read(new PerformanceServiceConnection.Call<IOdinPerformance>() {
            @Override
            public PerformanceControlResult call(IOdinPerformance service)
                    throws PerformanceServiceConnection.RemoteFailure {
                try {
                    PerformanceResponse response = service.getStatus();
                    if (response == null) {
                        throw remoteFailure("getStatus returned nothing", null);
                    }
                    return PerformanceResponseMapper.map(
                            response.result, response.activeMode, null);
                } catch (RemoteException exception) {
                    throw remoteFailure("getStatus failed", exception);
                }
            }
        });
    }

    @Override
    public PerformanceControlResult apply(final PerformanceMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("A performance mode is required");
        }
        return connection.write(new PerformanceServiceConnection.Call<IOdinPerformance>() {
            @Override
            public PerformanceControlResult call(IOdinPerformance service)
                    throws PerformanceServiceConnection.RemoteFailure {
                try {
                    PerformanceResponse response = service.setMode(mode.serviceValue);
                    if (response == null) {
                        throw remoteFailure("setMode returned nothing", null);
                    }
                    return PerformanceResponseMapper.map(
                            response.result, response.activeMode, mode);
                } catch (RemoteException exception) {
                    throw remoteFailure("setMode outcome is unknown", exception);
                }
            }
        });
    }

    private static PerformanceServiceConnection.RemoteFailure remoteFailure(String message,
            Throwable cause) {
        return new PerformanceServiceConnection.RemoteFailure(message, cause);
    }

    private static final class ServiceConnector
            implements PerformanceServiceConnection.Connector<IOdinPerformance> {
        @Override
        public IOdinPerformance connect(
                final PerformanceServiceConnection.DeathListener<IOdinPerformance> listener)
                throws PerformanceServiceConnection.RemoteFailure {
            // Non-blocking on purpose: the settings screen must not stall on a
            // daemon that is not up.
            IBinder binder = ServiceManager.checkService(SERVICE_NAME);
            if (binder == null) {
                throw new PerformanceServiceConnection.RemoteFailure(
                        "performance service is not published");
            }
            final IOdinPerformance service = IOdinPerformance.Stub.asInterface(binder);
            if (service == null) {
                throw new PerformanceServiceConnection.RemoteFailure(
                        "performance service binder did not resolve");
            }
            try {
                binder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        listener.onServiceDied(service);
                    }
                }, 0);
            } catch (RemoteException exception) {
                throw new PerformanceServiceConnection.RemoteFailure(
                        "performance service died before it could be used", exception);
            }
            return service;
        }
    }
}
