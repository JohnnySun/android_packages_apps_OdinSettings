package com.odin2.odinsettings.platform;

import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.ayn.charge.ChargeResponse;
import com.ayn.charge.IOdinCharge;
import com.odin2.odinsettings.hardware.ChargeControlResult;
import com.odin2.odinsettings.hardware.ChargeController;
import com.odin2.odinsettings.hardware.ChargeMode;
import com.odin2.odinsettings.hardware.ChargeResponseMapper;
import com.odin2.odinsettings.hardware.ChargeServiceConnection;

/** Talks to the private charge daemon over its own Binder instance. */
public final class AidlChargeController implements ChargeController {
    private static final String SERVICE_NAME = "com.ayn.charge.IOdinCharge/default";
    private static final AidlChargeController INSTANCE = new AidlChargeController();

    private final ChargeServiceConnection<IOdinCharge> connection =
            new ChargeServiceConnection<>(new ServiceConnector());

    public static AidlChargeController getInstance() {
        return INSTANCE;
    }

    @Override
    public ChargeControlResult read() {
        return connection.read(new ChargeServiceConnection.Call<IOdinCharge>() {
            @Override
            public ChargeControlResult call(IOdinCharge service)
                    throws ChargeServiceConnection.RemoteFailure {
                try {
                    return translate(service.getStatus(), null, "getStatus");
                } catch (RemoteException exception) {
                    throw remoteFailure("getStatus failed", exception);
                }
            }
        });
    }

    @Override
    public ChargeControlResult apply(final ChargeMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("A charge mode is required");
        }
        return connection.write(new ChargeServiceConnection.Call<IOdinCharge>() {
            @Override
            public ChargeControlResult call(IOdinCharge service)
                    throws ChargeServiceConnection.RemoteFailure {
                try {
                    return translate(service.setMode(mode.serviceValue), mode, "setMode");
                } catch (RemoteException exception) {
                    throw remoteFailure("setMode outcome is unknown", exception);
                }
            }
        });
    }

    private static ChargeControlResult translate(ChargeResponse response, ChargeMode requested,
            String call) throws ChargeServiceConnection.RemoteFailure {
        if (response == null) {
            throw remoteFailure(call + " returned nothing", null);
        }
        return ChargeResponseMapper.map(response.result, response.mode, response.restricted,
                response.capacity, response.stopPercent, response.resumePercent, requested);
    }

    private static ChargeServiceConnection.RemoteFailure remoteFailure(String message,
            Throwable cause) {
        return new ChargeServiceConnection.RemoteFailure(message, cause);
    }

    private static final class ServiceConnector
            implements ChargeServiceConnection.Connector<IOdinCharge> {
        @Override
        public IOdinCharge connect(
                final ChargeServiceConnection.DeathListener<IOdinCharge> listener)
                throws ChargeServiceConnection.RemoteFailure {
            // Non-blocking on purpose: neither the settings screen nor a quick
            // settings tile may stall on a daemon that is not up.
            IBinder binder = ServiceManager.checkService(SERVICE_NAME);
            if (binder == null) {
                throw new ChargeServiceConnection.RemoteFailure(
                        "charge service is not published");
            }
            final IOdinCharge service = IOdinCharge.Stub.asInterface(binder);
            if (service == null) {
                throw new ChargeServiceConnection.RemoteFailure(
                        "charge service binder did not resolve");
            }
            try {
                binder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        listener.onServiceDied(service);
                    }
                }, 0);
            } catch (RemoteException exception) {
                throw new ChargeServiceConnection.RemoteFailure(
                        "charge service died before it could be used", exception);
            }
            return service;
        }
    }
}
