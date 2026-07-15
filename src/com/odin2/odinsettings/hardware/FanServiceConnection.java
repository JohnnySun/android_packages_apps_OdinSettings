package com.odin2.odinsettings.hardware;

public final class FanServiceConnection<T> {
    public interface DeathListener<T> {
        void onServiceDied(T service);
    }

    public interface Connector<T> {
        T connect(DeathListener<T> listener) throws RemoteFailure;
    }

    public interface Call<T> {
        FanControlResult call(T service) throws RemoteFailure;
    }

    public static final class RemoteFailure extends Exception {
        private static final long serialVersionUID = 1L;

        public RemoteFailure(String message) {
            super(message);
        }

        public RemoteFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Connector<T> connector;
    private final DeathListener<T> deathListener = new DeathListener<T>() {
        @Override
        public void onServiceDied(T service) {
            clearIfCurrent(service);
        }
    };
    private T cachedService;

    public FanServiceConnection(Connector<T> connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Fan service connector is required");
        }
        this.connector = connector;
    }

    public FanControlResult read(Call<T> call) {
        if (call == null) {
            throw new IllegalArgumentException("Fan service read call is required");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            T service;
            try {
                service = getOrConnect();
            } catch (RemoteFailure failure) {
                if (attempt == 0) {
                    continue;
                }
                return unavailable(null);
            }
            if (service == null) {
                return unavailable(null);
            }
            try {
                return call.call(service);
            } catch (RemoteFailure failure) {
                clearIfCurrent(service);
                if (attempt == 1) {
                    return unavailable(null);
                }
            }
        }
        return unavailable(null);
    }

    public FanControlResult write(FanMode requestedMode, Call<T> call) {
        if (requestedMode == null || call == null) {
            throw new IllegalArgumentException("Fan service write call and mode are required");
        }
        final T service;
        try {
            service = getOrConnect();
        } catch (RemoteFailure failure) {
            return unavailable(requestedMode);
        }
        if (service == null) {
            return unavailable(requestedMode);
        }
        try {
            return call.call(service);
        } catch (RemoteFailure failure) {
            clearIfCurrent(service);
            return unavailable(requestedMode);
        }
    }

    private synchronized T getOrConnect() throws RemoteFailure {
        if (cachedService == null) {
            cachedService = connector.connect(deathListener);
        }
        return cachedService;
    }

    private synchronized void clearIfCurrent(T service) {
        if (cachedService == service) {
            cachedService = null;
        }
    }

    private static FanControlResult unavailable(FanMode requestedMode) {
        return FanControlResult.error(FanControlResult.Code.UNAVAILABLE, requestedMode);
    }
}
