package com.odin2.odinsettings.hardware;

import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.service.ControllerProfileCoordinator;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class ControllerProfileDispatcher implements AutoCloseable {
    public interface Callback {
        void onComplete(AdapterResult result);
    }

    private interface Operation {
        AdapterResult run();
    }

    private final Executor worker;
    private final Executor mainExecutor;
    private boolean closed;
    private long lifecycleGeneration;
    private int pendingRequests;

    public ControllerProfileDispatcher(Executor worker, Executor mainExecutor) {
        if (worker == null || mainExecutor == null) {
            throw new IllegalArgumentException("Controller profile executors are required");
        }
        this.worker = worker;
        this.mainExecutor = mainExecutor;
    }

    public boolean submitRead(final ControllerProfileCoordinator coordinator,
            final DeviceIdentity identity, Callback callback) {
        if (coordinator == null || identity == null) {
            throw new IllegalArgumentException("Controller coordinator and identity are required");
        }
        return submitOperation(new Operation() {
            @Override
            public AdapterResult run() {
                return coordinator.read(identity);
            }
        }, callback);
    }

    public boolean submit(final ControllerProfileCoordinator coordinator,
            final DeviceIdentity identity, final ControllerProfile profile,
            Callback callback) {
        if (coordinator == null || identity == null || profile == null) {
            throw new IllegalArgumentException(
                    "Controller coordinator, identity, and profile are required");
        }
        return submitOperation(new Operation() {
            @Override
            public AdapterResult run() {
                return coordinator.apply(identity, profile);
            }
        }, callback);
    }

    public synchronized boolean isPending() {
        return pendingRequests > 0;
    }

    public synchronized void invalidateCallbacks() {
        if (!closed) {
            lifecycleGeneration += 1;
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            lifecycleGeneration += 1;
        }
    }

    private synchronized boolean submitOperation(final Operation operation,
            final Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Controller profile callback is required");
        }
        if (closed) {
            return false;
        }
        final long requestGeneration = lifecycleGeneration;
        pendingRequests += 1;
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    AdapterResult result;
                    try {
                        result = operation.run();
                    } catch (RuntimeException exception) {
                        result = AdapterResult.of(AdapterResult.Code.UNAVAILABLE,
                                "Controller profile operation failed.");
                    }
                    deliver(requestGeneration, callback, result);
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            pendingRequests -= 1;
            return false;
        }
    }

    private void deliver(final long requestGeneration, final Callback callback,
            final AdapterResult result) {
        synchronized (this) {
            if (closed || lifecycleGeneration != requestGeneration) {
                pendingRequests -= 1;
                return;
            }
        }
        try {
            mainExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    boolean deliver;
                    synchronized (ControllerProfileDispatcher.this) {
                        deliver = !closed && lifecycleGeneration == requestGeneration;
                        pendingRequests -= 1;
                    }
                    if (deliver) {
                        callback.onComplete(result);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            synchronized (this) {
                pendingRequests -= 1;
            }
        }
    }
}
