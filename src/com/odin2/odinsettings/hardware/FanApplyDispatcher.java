package com.odin2.odinsettings.hardware;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class FanApplyDispatcher implements AutoCloseable {
    public interface Callback {
        void onComplete(FanControlResult result);
    }

    private interface Operation {
        FanControlResult run();

        FanMode requestedMode();
    }

    private final Executor worker;
    private final Executor mainExecutor;
    private boolean closed;
    private long lifecycleGeneration;
    private int pendingRequests;

    public FanApplyDispatcher(Executor worker, Executor mainExecutor) {
        if (worker == null || mainExecutor == null) {
            throw new IllegalArgumentException("Fan executors are required");
        }
        this.worker = worker;
        this.mainExecutor = mainExecutor;
    }

    public boolean submitRead(final FanController controller, Callback callback) {
        if (controller == null) {
            throw new IllegalArgumentException("Fan controller is required");
        }
        return submitOperation(new Operation() {
            @Override
            public FanControlResult run() {
                return controller.read();
            }

            @Override
            public FanMode requestedMode() {
                return null;
            }
        }, callback);
    }

    public boolean submit(final FanController controller, final FanMode mode,
            Callback callback) {
        if (controller == null || mode == null) {
            throw new IllegalArgumentException("Fan controller and mode are required");
        }
        return submitOperation(new Operation() {
            @Override
            public FanControlResult run() {
                return controller.apply(mode);
            }

            @Override
            public FanMode requestedMode() {
                return mode;
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
        if (closed) {
            return;
        }
        closed = true;
        lifecycleGeneration += 1;
    }

    private synchronized boolean submitOperation(final Operation operation,
            final Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Fan callback is required");
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
                    FanControlResult result;
                    try {
                        result = operation.run();
                    } catch (RuntimeException exception) {
                        result = FanControlResult.error(
                                FanControlResult.Code.UNAVAILABLE,
                                operation.requestedMode());
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
            final FanControlResult result) {
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
                    synchronized (FanApplyDispatcher.this) {
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
