package com.odin2.odinsettings.hardware;

/** The seam the settings screen talks to, so the UI never touches Binder. */
public interface PerformanceController {
    PerformanceControlResult read();

    PerformanceControlResult apply(PerformanceMode mode);
}
