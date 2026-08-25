package com.odin2.odinsettings.hardware;

/**
 * Caches the service binder and reconnects once when it dies.
 *
 * <p>Deliberately mirrors {@link FanServiceConnection}: one retry on a failed
 * connect, the cache dropped when the remote dies, and a write attempted only
 * once so a mode change is never silently applied twice.
 */
public final class ChargeServiceConnection<T> {
    public interface DeathListener<T> {
        void onServiceDied(T service);
    }

    public interface Connector<T> {
        T connect(DeathListener<T> listener) throws RemoteFailure;
    }

    public interface Call<T> {
        ChargeControlResult call(T service) throws RemoteFailure;
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

    public ChargeServiceConnection(Connector<T> connector) {
        if (connector == null) {
            throw new IllegalArgumentException("Charge service connector is required");
        }
        this.connector = connector;
    }

    /** Reads may reconnect and retry, because a read has no side effect. */
    public ChargeControlResult read(Call<T> call) {
        if (call == null) {
            throw new IllegalArgumentException("Charge service read call is required");
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            T service = connectQuietly();
            if (service == null) {
                continue;
            }
            try {
                return call.call(service);
            } catch (RemoteFailure failure) {
                clearIfCurrent(service);
            }
        }
        return ChargeControlResult.failure(
                ChargeControlResult.Code.UNAVAILABLE, null);
    }

    /** Writes are attempted once, so a mode change cannot be applied twice. */
    public ChargeControlResult write(Call<T> call) {
        if (call == null) {
            throw new IllegalArgumentException("Charge service write call is required");
        }
        T service = connectQuietly();
        if (service == null) {
            return ChargeControlResult.failure(
                    ChargeControlResult.Code.UNAVAILABLE, null);
        }
        try {
            return call.call(service);
        } catch (RemoteFailure failure) {
            clearIfCurrent(service);
            return ChargeControlResult.failure(
                    ChargeControlResult.Code.UNAVAILABLE, null);
        }
    }

    private T connectQuietly() {
        if (cachedService != null) {
            return cachedService;
        }
        try {
            cachedService = connector.connect(deathListener);
        } catch (RemoteFailure failure) {
            cachedService = null;
        }
        return cachedService;
    }

    private synchronized void clearIfCurrent(T service) {
        if (cachedService == service) {
            cachedService = null;
        }
    }
}
