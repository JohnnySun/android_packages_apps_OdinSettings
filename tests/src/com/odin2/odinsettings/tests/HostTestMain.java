package com.odin2.odinsettings.tests;

import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.domain.ControllerAxisNormalizer;
import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;
import com.odin2.odinsettings.hardware.AdapterCapability;
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.DisabledHardwareAdapter;
import com.odin2.odinsettings.hardware.FanActualState;
import com.odin2.odinsettings.hardware.FanApplyDispatcher;
import com.odin2.odinsettings.hardware.FanControlResult;
import com.odin2.odinsettings.hardware.FanController;
import com.odin2.odinsettings.hardware.FanMode;
import com.odin2.odinsettings.hardware.FanResponseMapper;
import com.odin2.odinsettings.hardware.FanServiceConnection;
import com.odin2.odinsettings.hardware.HardwareAdapter;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;
import com.odin2.odinsettings.service.ControllerProfileCoordinator;
import com.odin2.odinsettings.service.ControllerColdBootPolicy;
import com.odin2.odinsettings.service.ExternalDisplayCoordinator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class HostTestMain {
    private static int tests;

    public static void main(String[] args) {
        standardProfileIsIdentity();
        flippedProfileSwapsOnlyFaceButtons();
        unknownStoredProfileFallsBackToStandard();
        controllerAxesNormalizePublishedGamepadRanges();
        controllerAxesClampAndHonorFlatZones();
        identityPolicyAcceptsOnlyProvenDeviceProductPairs();
        unknownDeviceNeverReachesAdapter();
        recognizedDeviceStillFailsClosedWithoutAdapter();
        capabilityGatePreventsUnsupportedCalls();
        reviewedAdapterCanReceiveRecognizedProfile();
        externalDisplayUsesTheSameIdentityAndCapabilityGates();
        controllerColdBootCycleIsBoundedAndFailClosed();
        fanModesAreStrictlyAllowlisted();
        fanStatusClassifiesActualStateTruthfully();
        fanControlErrorsCannotCarryPartialData();
        fanResponseMappingRequiresACompleteConfirmedSnapshot();
        fanReadAndWriteDispatchSeriallyAndReturnOnMainExecutor();
        fanDispatcherSuppressesLifecycleStaleCallbacks();
        fanConnectionRetriesReadOnceAfterRemoteFailure();
        fanConnectionNeverRetriesSetMode();
        fanConnectionClearsTheDeadCachedService();
        ResourceContractTest.verify();
        pass();

        System.out.println("PASS: " + tests + " Odin Settings host tests");
    }

    private static void standardProfileIsIdentity() {
        for (ControllerButton button : ControllerButton.values()) {
            assertEquals(button, ControllerProfiles.STANDARD.map(button),
                    "standard mapping for " + button);
        }
        pass();
    }

    private static void flippedProfileSwapsOnlyFaceButtons() {
        assertEquals(ControllerButton.B,
                ControllerProfiles.FLIPPED_FACE.map(ControllerButton.A), "A maps to B");
        assertEquals(ControllerButton.A,
                ControllerProfiles.FLIPPED_FACE.map(ControllerButton.B), "B maps to A");
        assertEquals(ControllerButton.Y,
                ControllerProfiles.FLIPPED_FACE.map(ControllerButton.X), "X maps to Y");
        assertEquals(ControllerButton.X,
                ControllerProfiles.FLIPPED_FACE.map(ControllerButton.Y), "Y maps to X");
        assertEquals(ControllerButton.START,
                ControllerProfiles.FLIPPED_FACE.map(ControllerButton.START),
                "non-face buttons remain unchanged");
        pass();
    }

    private static void unknownStoredProfileFallsBackToStandard() {
        assertEquals(ControllerProfiles.STANDARD,
                ControllerProfiles.findOrDefault("corrupt-or-future-value"),
                "unknown preview profile fallback");
        pass();
    }

    private static void controllerAxesNormalizePublishedGamepadRanges() {
        assertFloatEquals(-1.0f,
                ControllerAxisNormalizer.centered(-32768.0f, -32768.0f, 32768.0f, 0.0f),
                "centered axis minimum");
        assertFloatEquals(0.0f,
                ControllerAxisNormalizer.centered(0.0f, -32768.0f, 32768.0f, 0.0f),
                "centered axis neutral");
        assertFloatEquals(1.0f,
                ControllerAxisNormalizer.centered(32768.0f, -32768.0f, 32768.0f, 0.0f),
                "centered axis maximum");
        assertFloatEquals(0.5f,
                ControllerAxisNormalizer.trigger(0x308, 0.0f, 0x610, 0.0f),
                "published trigger midpoint");
        pass();
    }

    private static void controllerAxesClampAndHonorFlatZones() {
        assertFloatEquals(0.0f,
                ControllerAxisNormalizer.centered(500.0f, -32768.0f, 32768.0f, 1000.0f),
                "centered axis flat zone");
        assertFloatEquals(0.0f,
                ControllerAxisNormalizer.trigger(25.0f, 0.0f, 1552.0f, 50.0f),
                "trigger flat zone");
        assertFloatEquals(1.0f,
                ControllerAxisNormalizer.trigger(2000.0f, 0.0f, 1552.0f, 0.0f),
                "trigger clamps high");
        assertFloatEquals(-1.0f,
                ControllerAxisNormalizer.centered(-40000.0f, -32768.0f, 32768.0f, 0.0f),
                "centered axis clamps low");
        pass();
    }

    private static void identityPolicyAcceptsOnlyProvenDeviceProductPairs() {
        HardwareAccessPolicy policy = new HardwareAccessPolicy();

        assertTrue(policy.evaluate(
                new DeviceIdentity("Odin2_Mini", "kalama", "kalama", "QCS8550"))
                .allowed, "stock identity is accepted");
        assertTrue(policy.evaluate(
                new DeviceIdentity(
                        "Odin2 Mini", "odin2_mini", "lineage_odin2_mini", "QCS8550"))
                .allowed, "Lineage identity is accepted");
        assertFalse(policy.evaluate(
                new DeviceIdentity(
                        "Odin2 Mini", "odin2_mini", "kalama", "QCS8550"))
                .allowed, "mixed Lineage device and stock product are rejected");
        assertFalse(policy.evaluate(
                new DeviceIdentity(
                        "Odin2 Mini", "kalama", "lineage_odin2_mini", "QCS8550"))
                .allowed, "mixed stock device and Lineage product are rejected");
        assertFalse(policy.evaluate(
                new DeviceIdentity("Odin2 Mini", "unknown", "unknown", "QCS8550"))
                .allowed, "unknown device and product are rejected");
        pass();
    }

    private static void unknownDeviceNeverReachesAdapter() {
        RecordingAdapter adapter = RecordingAdapter.forCapabilities(
                AdapterCapability.CONTROLLER_PROFILE);
        ControllerProfileCoordinator coordinator = new ControllerProfileCoordinator(
                new HardwareAccessPolicy(), adapter);

        AdapterResult result = coordinator.apply(
                new DeviceIdentity("Other", "kalama", "kalama", "QCS8550"),
                ControllerProfiles.STANDARD);

        assertEquals(AdapterResult.Code.UNKNOWN_DEVICE, result.code,
                "unknown device result");
        assertEquals(0, adapter.controllerCalls, "adapter call count");
        pass();
    }

    private static void recognizedDeviceStillFailsClosedWithoutAdapter() {
        ControllerProfileCoordinator coordinator = new ControllerProfileCoordinator(
                new HardwareAccessPolicy(), new DisabledHardwareAdapter());

        AdapterResult result = coordinator.apply(knownIdentity(), ControllerProfiles.STANDARD);

        assertEquals(AdapterResult.Code.ADAPTER_UNAVAILABLE, result.code,
                "disabled adapter result");
        assertFalse(result.wasApplied(), "disabled adapter cannot report applied");
        pass();
    }

    private static void capabilityGatePreventsUnsupportedCalls() {
        RecordingAdapter adapter = RecordingAdapter.forCapabilities(
                AdapterCapability.EXTERNAL_DISPLAY_POLICY);
        ControllerProfileCoordinator coordinator = new ControllerProfileCoordinator(
                new HardwareAccessPolicy(), adapter);

        AdapterResult result = coordinator.apply(knownIdentity(), ControllerProfiles.STANDARD);

        assertEquals(AdapterResult.Code.UNSUPPORTED_CAPABILITY, result.code,
                "unsupported controller capability");
        assertEquals(0, adapter.controllerCalls, "unsupported adapter call count");
        pass();
    }

    private static void reviewedAdapterCanReceiveRecognizedProfile() {
        RecordingAdapter adapter = RecordingAdapter.forCapabilities(
                AdapterCapability.CONTROLLER_PROFILE);
        ControllerProfileCoordinator coordinator = new ControllerProfileCoordinator(
                new HardwareAccessPolicy(), adapter);

        AdapterResult result = coordinator.apply(knownIdentity(),
                ControllerProfiles.FLIPPED_FACE);

        assertEquals(AdapterResult.Code.APPLIED, result.code, "recognized apply result");
        assertEquals(1, adapter.controllerCalls, "recognized adapter call count");
        assertEquals(ControllerProfiles.FLIPPED_FACE, adapter.lastProfile,
                "profile passed to adapter");
        pass();
    }

    private static void externalDisplayUsesTheSameIdentityAndCapabilityGates() {
        RecordingAdapter adapter = RecordingAdapter.forCapabilities(
                AdapterCapability.EXTERNAL_DISPLAY_POLICY);
        ExternalDisplayCoordinator coordinator = new ExternalDisplayCoordinator(
                new HardwareAccessPolicy(), adapter);

        AdapterResult denied = coordinator.apply(
                new DeviceIdentity("Odin2_Mini", "kalama", "kalama", "unknown"),
                ExternalDisplayPolicy.FORCE_LANDSCAPE);
        assertEquals(AdapterResult.Code.UNKNOWN_DEVICE, denied.code,
                "display unknown device result");
        assertEquals(0, adapter.displayCalls, "display denied call count");

        AdapterResult applied = coordinator.apply(
                knownIdentity(), ExternalDisplayPolicy.SYSTEM_MANAGED);
        assertEquals(AdapterResult.Code.APPLIED, applied.code,
                "display recognized result");
        assertEquals(1, adapter.displayCalls, "display recognized call count");
        pass();
    }

    private static DeviceIdentity knownIdentity() {
        return new DeviceIdentity("Odin2_Mini", "kalama", "kalama", "QCS8550");
    }

    private static void controllerColdBootCycleIsBoundedAndFailClosed() {
        ControllerColdBootPolicy policy = new ControllerColdBootPolicy();
        assertEquals(ControllerColdBootPolicy.Decision.SKIP_UNKNOWN_DEVICE,
                policy.decide(false, false, false),
                "unknown device must not cycle the display");
        assertEquals(ControllerColdBootPolicy.Decision.SKIP_CONTROLLER_PRESENT,
                policy.decide(true, true, false),
                "published gamepad must not cycle the display");
        assertEquals(ControllerColdBootPolicy.Decision.SKIP_ATTEMPT_CONSUMED,
                policy.decide(true, false, true),
                "a failed cycle must not retry during the same boot");
        assertEquals(ControllerColdBootPolicy.Decision.CYCLE_DISPLAY_ONCE,
                policy.decide(true, false, false),
                "recognized device with missing gamepad gets one display cycle");
        pass();
    }

    private static void fanModesAreStrictlyAllowlisted() {
        assertEquals(FanMode.OFF, FanMode.fromPreferenceValue("off"), "off mode");
        assertEquals(FanMode.QUIET, FanMode.fromPreferenceValue("quiet"), "quiet mode");
        assertEquals(FanMode.SPORT, FanMode.fromPreferenceValue("sport"), "sport mode");
        assertEquals(3, FanMode.values().length, "fan mode allowlist size");
        assertThrows(new Runnable() {
            @Override
            public void run() {
                FanMode.fromPreferenceValue("custom");
            }
        }, "custom fan mode must be rejected");
        pass();
    }

    private static void fanStatusClassifiesActualStateTruthfully() {
        FanControlResult offWithCachedHighTime =
                FanControlResult.available(FanMode.OFF, 0, 25000, 3300);
        assertEquals(FanActualState.UNRECOGNIZED, offWithCachedHighTime.actualState,
                "off requires exact baseline high-time and zero tach");
        assertEquals(25000, offWithCachedHighTime.pwmHighTimeNs,
                "off snapshot preserves stored high-time");
        assertEquals(3300, offWithCachedHighTime.tachPulsesTimes300,
                "tach remains pulses times 300");

        assertEquals(FanActualState.OFF,
                FanControlResult.available(FanMode.OFF, 0, 10000, 0).actualState,
                "exact off actual state");

        assertEquals(FanActualState.QUIET,
                FanControlResult.available(FanMode.QUIET, 1, 7100, 3600).actualState,
                "quiet curve actual state");
        assertEquals(FanActualState.UNRECOGNIZED,
                FanControlResult.available(FanMode.QUIET, 1, 5000, 0).actualState,
                "quiet state and high-time without tach must not claim quiet");
        assertEquals(FanActualState.SPORT,
                FanControlResult.available(FanMode.SPORT, 1, 13000, 5700).actualState,
                "sport curve actual state");
        assertEquals(FanActualState.UNRECOGNIZED,
                FanControlResult.available(FanMode.SPORT, 1, 25000, 0).actualState,
                "sport state and high-time without tach must not claim sport");
        assertEquals(FanActualState.UNRECOGNIZED,
                FanControlResult.available(null, 1, 10000, 1800).actualState,
                "unknown enabled high-time must not invent a mode");
        pass();
    }

    private static void fanControlErrorsCannotCarryPartialData() {
        for (FanControlResult.Code code : new FanControlResult.Code[] {
                FanControlResult.Code.UNSUPPORTED,
                FanControlResult.Code.UNEXPECTED_PATHS,
                FanControlResult.Code.UNAVAILABLE,
                FanControlResult.Code.MALFORMED,
                FanControlResult.Code.PERIOD_MISMATCH,
                FanControlResult.Code.WRITE_FAILED,
                FanControlResult.Code.READBACK_MISMATCH,
                FanControlResult.Code.INVALID_MODE,
        }) {
            FanControlResult error = FanControlResult.error(code, FanMode.SPORT);
            assertEquals(code, error.code, "fan error code");
            assertFalse(error.hasSnapshot(), "fan error must not carry a snapshot");
            assertEquals(-1, error.state, "fan error state sentinel");
            assertEquals(-1, error.pwmHighTimeNs, "fan error high-time sentinel");
            assertEquals(-1, error.tachPulsesTimes300, "fan error tach sentinel");
            assertEquals(FanMode.SPORT, error.requestedMode,
                    "fan error must preserve the requested mode");
        }

        assertThrows(new Runnable() {
            @Override
            public void run() {
                FanControlResult.available(FanMode.OFF, 2, 10000, 0);
            }
        }, "invalid fan state must be rejected");
        assertThrows(new Runnable() {
            @Override
            public void run() {
                FanControlResult.available(FanMode.QUIET, 1, -1, 0);
            }
        }, "negative fan high-time must be rejected");
        assertThrows(new Runnable() {
            @Override
            public void run() {
                FanControlResult.available(FanMode.QUIET, 1, 5000, -1);
            }
        }, "negative fan tach must be rejected");
        assertThrows(new Runnable() {
            @Override
            public void run() {
                FanControlResult.error(FanControlResult.Code.AVAILABLE, FanMode.OFF);
            }
        }, "available code cannot be constructed without a snapshot");
        pass();
    }

    private static void fanResponseMappingRequiresACompleteConfirmedSnapshot() {
        FanControlResult read = FanResponseMapper.map(
                FanResponseMapper.RESULT_OK, FanMode.QUIET.serviceValue,
                1, 7100, 3600, null);
        assertEquals(FanActualState.QUIET, read.actualState,
                "complete read snapshot maps to quiet");
        assertTrue(read.requestedMode == null,
                "status reads must not invent a requested mode");

        FanControlResult write = FanResponseMapper.map(
                FanResponseMapper.RESULT_OK, FanMode.SPORT.serviceValue,
                1, 13000, 5700, FanMode.SPORT);
        assertEquals(FanMode.SPORT, write.requestedMode,
                "confirmed write preserves requested mode");

        assertEquals(FanControlResult.Code.MALFORMED,
                FanResponseMapper.map(FanResponseMapper.RESULT_OK,
                        FanMode.QUIET.serviceValue, 1, 5000, -1, null).code,
                "partial success snapshot must be rejected");
        assertEquals(FanControlResult.Code.MALFORMED,
                FanResponseMapper.map(FanResponseMapper.RESULT_OK,
                        FanMode.QUIET.serviceValue, 1, 4999, 3300, null).code,
                "mode and snapshot mismatch must be rejected");
        assertEquals(FanControlResult.Code.MALFORMED,
                FanResponseMapper.map(FanResponseMapper.RESULT_OK,
                        FanMode.QUIET.serviceValue, 1, 5000, 1200, FanMode.SPORT).code,
                "write response for a different mode must be rejected");
        assertEquals(FanControlResult.Code.MALFORMED,
                FanResponseMapper.map(FanResponseMapper.RESULT_IO_ERROR,
                        FanMode.QUIET.serviceValue, 1, 5000, 1200, FanMode.QUIET).code,
                "error response carrying a snapshot must be rejected");
        assertEquals(FanControlResult.Code.MALFORMED,
                FanResponseMapper.map(FanResponseMapper.RESULT_IO_ERROR,
                        FanMode.QUIET.serviceValue, -1, -1, -1, FanMode.SPORT).code,
                "write error for a different mode must be rejected");
        assertEquals(FanControlResult.Code.UNAVAILABLE,
                FanResponseMapper.map(FanResponseMapper.RESULT_IO_ERROR,
                        FanMode.OFF.serviceValue, -1, -1, -1, null).code,
                "status I/O failure maps to unavailable");
        assertEquals(FanControlResult.Code.WRITE_FAILED,
                FanResponseMapper.map(FanResponseMapper.RESULT_IO_ERROR,
                        FanMode.QUIET.serviceValue, -1, -1, -1, FanMode.QUIET).code,
                "setMode I/O failure maps to write failed");
        assertEquals(FanControlResult.Code.READBACK_MISMATCH,
                FanResponseMapper.map(FanResponseMapper.RESULT_TACH_TIMEOUT,
                        FanMode.SPORT.serviceValue, -1, -1, -1, FanMode.SPORT).code,
                "tach timeout maps to a snapshot-free readback error");
        pass();
    }

    private static void fanReadAndWriteDispatchSeriallyAndReturnOnMainExecutor() {
        ManualExecutorService worker = new ManualExecutorService();
        ManualExecutor main = new ManualExecutor();
        RecordingFanController controller = new RecordingFanController();
        FanApplyDispatcher dispatcher = new FanApplyDispatcher(worker, main);
        List<String> callbacks = new ArrayList<>();

        assertTrue(dispatcher.submitRead(controller,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbacks.add("read:" + result.actualState.name());
                    }
                }), "fan read must be accepted");
        assertTrue(dispatcher.submit(controller, FanMode.QUIET,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbacks.add("write:" + result.actualState.name());
                    }
                }), "fan write must queue behind the read");
        assertEquals(List.of(), controller.operations,
                "Binder operations must not run in the submit callback");

        worker.runNext();
        assertEquals(List.of("read"), controller.operations,
                "read must execute first on the single worker");
        worker.runNext();
        assertEquals(List.of("read", "write:quiet"), controller.operations,
                "write must execute after read on the same worker");
        assertEquals(List.of(), callbacks,
                "fan results must wait for the main executor");

        main.runNext();
        main.runNext();
        assertEquals(List.of("read:OFF", "write:QUIET"), callbacks,
                "callbacks must preserve serialized operation order");
        assertFalse(dispatcher.isPending(),
                "main delivery must clear all pending requests");
        dispatcher.close();
        pass();
    }

    private static void fanDispatcherSuppressesLifecycleStaleCallbacks() {
        ManualExecutorService worker = new ManualExecutorService();
        ManualExecutor main = new ManualExecutor();
        RecordingFanController controller = new RecordingFanController();
        FanApplyDispatcher dispatcher = new FanApplyDispatcher(worker, main);
        final int[] callbackCalls = {0};

        assertTrue(dispatcher.submit(controller, FanMode.SPORT,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbackCalls[0] += 1;
                    }
                }), "fan request before close must be accepted");
        worker.runNext();
        assertEquals(1, main.size(), "completed work must queue its UI callback");
        dispatcher.close();
        assertFalse(worker.shutdown,
                "closing one lifecycle must preserve the process worker");
        assertFalse(worker.shutdownNow,
                "close must not interrupt an accepted fan transaction");
        main.runNext();
        assertEquals(0, callbackCalls[0],
                "closed lifecycle must suppress an already queued UI result");

        FanApplyDispatcher closingBeforeRun = new FanApplyDispatcher(worker, main);
        assertTrue(closingBeforeRun.submit(controller, FanMode.QUIET,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbackCalls[0] += 1;
                    }
                }), "write accepted before close must remain queued");
        closingBeforeRun.close();
        worker.runNext();
        assertEquals(List.of("write:sport", "write:quiet"), controller.operations,
                "accepted write must finish after its UI lifecycle closes");
        assertEquals(0, main.size(),
                "closed lifecycle must not enqueue a new stale callback");
        assertEquals(0, callbackCalls[0],
                "closed lifecycle callback must remain suppressed");

        FanApplyDispatcher resumed = new FanApplyDispatcher(worker, main);
        assertTrue(resumed.submit(controller, FanMode.SPORT,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbackCalls[0] += 1;
                    }
                }), "fan request before lifecycle invalidation must be accepted");
        resumed.invalidateCallbacks();
        assertTrue(resumed.submitRead(controller,
                new FanApplyDispatcher.Callback() {
                    @Override
                    public void onComplete(FanControlResult result) {
                        callbackCalls[0] += 10;
                    }
                }), "new lifecycle read must be accepted after invalidation");
        worker.runNext();
        worker.runNext();
        assertEquals(List.of("write:sport", "write:quiet", "write:sport", "read"),
                controller.operations,
                "invalidation must not interrupt accepted work or reorder the fresh read");
        assertEquals(1, main.size(),
                "only the fresh lifecycle callback may reach the main executor");
        main.runNext();
        assertEquals(10, callbackCalls[0],
                "fresh lifecycle callback must be delivered after stale suppression");
        assertFalse(resumed.isPending(),
                "stale and fresh operations must both clear pending state");
        resumed.close();
        pass();
    }

    private static void fanConnectionRetriesReadOnceAfterRemoteFailure() {
        FakeFanConnector connector = new FakeFanConnector();
        FakeFanService failed = FakeFanService.failingRead();
        FakeFanService recovered = FakeFanService.available(FanMode.QUIET);
        connector.add(failed);
        connector.add(recovered);
        FanServiceConnection<FakeFanService> connection =
                new FanServiceConnection<>(connector);

        FanControlResult result = connection.read(new FakeReadCall());

        assertEquals(FanActualState.QUIET, result.actualState,
                "one reacquire may recover a status read");
        assertEquals(2, connector.connectCalls,
                "status read must reacquire at most once");
        assertEquals(1, failed.readCalls, "failed status call count");
        assertEquals(1, recovered.readCalls, "reacquired status call count");

        FakeFanConnector cappedConnector = new FakeFanConnector();
        FakeFanService firstFailure = FakeFanService.failingRead();
        FakeFanService secondFailure = FakeFanService.failingRead();
        FakeFanService forbiddenThird = FakeFanService.available(FanMode.SPORT);
        cappedConnector.add(firstFailure);
        cappedConnector.add(secondFailure);
        cappedConnector.add(forbiddenThird);
        FanControlResult cappedResult = new FanServiceConnection<>(cappedConnector)
                .read(new FakeReadCall());
        assertEquals(FanControlResult.Code.UNAVAILABLE, cappedResult.code,
                "second status failure must exhaust the retry budget");
        assertEquals(2, cappedConnector.connectCalls,
                "status read must stop after one reacquire");
        assertEquals(0, forbiddenThird.readCalls,
                "status read must never attempt a second reacquire");
        pass();
    }

    private static void fanConnectionNeverRetriesSetMode() {
        FakeFanConnector connector = new FakeFanConnector();
        FakeFanService ambiguous = FakeFanService.failingWrite();
        FakeFanService mustNotRun = FakeFanService.available(FanMode.SPORT);
        connector.add(ambiguous);
        connector.add(mustNotRun);
        FanServiceConnection<FakeFanService> connection =
                new FanServiceConnection<>(connector);

        FanControlResult result = connection.write(
                FanMode.SPORT, new FakeWriteCall(FanMode.SPORT));

        assertEquals(FanControlResult.Code.UNAVAILABLE, result.code,
                "ambiguous write failure must be reported without retry");
        assertEquals(1, connector.connectCalls,
                "setMode must never reacquire and retry");
        assertEquals(1, ambiguous.writeCalls, "ambiguous setMode call count");
        assertEquals(0, mustNotRun.writeCalls, "replacement service must not receive setMode");
        pass();
    }

    private static void fanConnectionClearsTheDeadCachedService() {
        FakeFanConnector connector = new FakeFanConnector();
        FakeFanService first = FakeFanService.available(FanMode.OFF);
        FakeFanService second = FakeFanService.available(FanMode.SPORT);
        connector.add(first);
        connector.add(second);
        FanServiceConnection<FakeFanService> connection =
                new FanServiceConnection<>(connector);

        assertEquals(FanActualState.OFF,
                connection.read(new FakeReadCall()).actualState,
                "first cached service result");
        connector.die(first);
        assertEquals(FanActualState.SPORT,
                connection.read(new FakeReadCall()).actualState,
                "death recipient must force a fresh service lookup");
        assertEquals(2, connector.connectCalls,
                "dead cached service must not be reused");
        pass();
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFloatEquals(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.0001f) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void pass() {
        tests += 1;
    }

    private static final class RecordingFanController implements FanController {
        private final List<String> operations = new ArrayList<>();

        @Override
        public FanControlResult read() {
            operations.add("read");
            return FanControlResult.available(null, FanMode.OFF, 0, 10000, 0);
        }

        @Override
        public FanControlResult apply(FanMode mode) {
            operations.add("write:" + mode.preferenceValue);
            int highTime = mode == FanMode.QUIET ? 5000 : 25000;
            return FanControlResult.available(mode, 1, highTime, 1800);
        }
    }

    private static final class FakeFanService {
        private final FanMode mode;
        private final boolean failRead;
        private final boolean failWrite;
        private int readCalls;
        private int writeCalls;

        private FakeFanService(FanMode mode, boolean failRead, boolean failWrite) {
            this.mode = mode;
            this.failRead = failRead;
            this.failWrite = failWrite;
        }

        static FakeFanService available(FanMode mode) {
            return new FakeFanService(mode, false, false);
        }

        static FakeFanService failingRead() {
            return new FakeFanService(FanMode.OFF, true, false);
        }

        static FakeFanService failingWrite() {
            return new FakeFanService(FanMode.OFF, false, true);
        }

        FanControlResult read() throws FanServiceConnection.RemoteFailure {
            readCalls += 1;
            if (failRead) {
                throw new FanServiceConnection.RemoteFailure("read failed");
            }
            return confirmed(mode, null);
        }

        FanControlResult write(FanMode requestedMode)
                throws FanServiceConnection.RemoteFailure {
            writeCalls += 1;
            if (failWrite) {
                throw new FanServiceConnection.RemoteFailure("write outcome unknown");
            }
            return confirmed(requestedMode, requestedMode);
        }

        private static FanControlResult confirmed(FanMode actualMode, FanMode requestedMode) {
            switch (actualMode) {
                case OFF:
                    return FanControlResult.available(
                            requestedMode, actualMode, 0, 10000, 0);
                case QUIET:
                    return FanControlResult.available(
                            requestedMode, actualMode, 1, 7100, 3600);
                case SPORT:
                    return FanControlResult.available(
                            requestedMode, actualMode, 1, 13000, 5700);
            }
            throw new IllegalArgumentException("Unknown fake fan mode");
        }
    }

    private static final class FakeFanConnector
            implements FanServiceConnection.Connector<FakeFanService> {
        private final Queue<FakeFanService> services = new ArrayDeque<>();
        private FanServiceConnection.DeathListener<FakeFanService> deathListener;
        private int connectCalls;

        void add(FakeFanService service) {
            services.add(service);
        }

        void die(FakeFanService service) {
            if (deathListener == null) {
                throw new AssertionError("No registered death listener");
            }
            deathListener.onServiceDied(service);
        }

        @Override
        public FakeFanService connect(
                FanServiceConnection.DeathListener<FakeFanService> listener) {
            connectCalls += 1;
            deathListener = listener;
            return services.poll();
        }
    }

    private static final class FakeReadCall
            implements FanServiceConnection.Call<FakeFanService> {
        @Override
        public FanControlResult call(FakeFanService service)
                throws FanServiceConnection.RemoteFailure {
            return service.read();
        }
    }

    private static final class FakeWriteCall
            implements FanServiceConnection.Call<FakeFanService> {
        private final FanMode requestedMode;

        FakeWriteCall(FanMode requestedMode) {
            this.requestedMode = requestedMode;
        }

        @Override
        public FanControlResult call(FakeFanService service)
                throws FanServiceConnection.RemoteFailure {
            return service.write(requestedMode);
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new AssertionError("No queued executor task");
            }
            task.run();
        }

        int size() {
            return tasks.size();
        }
    }

    private static final class ManualExecutorService extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;
        private boolean shutdownNow;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            shutdownNow = true;
            List<Runnable> remaining = List.copyOf(tasks);
            tasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor is shut down");
            }
            tasks.add(command);
        }

        void runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                throw new AssertionError("No queued executor task");
            }
            task.run();
        }
    }

    private static final class RecordingAdapter implements HardwareAdapter {
        private final AdapterStatus status;
        private int controllerCalls;
        private int displayCalls;
        private ControllerProfile lastProfile;

        private RecordingAdapter(AdapterStatus status) {
            this.status = status;
        }

        static RecordingAdapter forCapabilities(AdapterCapability... capabilities) {
            EnumSet<AdapterCapability> supported = EnumSet.noneOf(AdapterCapability.class);
            for (AdapterCapability capability : capabilities) {
                supported.add(capability);
            }
            return new RecordingAdapter(AdapterStatus.available("test adapter", supported));
        }

        @Override
        public AdapterStatus status() {
            return status;
        }

        @Override
        public AdapterResult applyControllerProfile(DeviceIdentity identity,
                ControllerProfile profile) {
            controllerCalls += 1;
            lastProfile = profile;
            return AdapterResult.of(AdapterResult.Code.APPLIED, "test apply");
        }

        @Override
        public AdapterResult applyExternalDisplayPolicy(DeviceIdentity identity,
                ExternalDisplayPolicy policy) {
            displayCalls += 1;
            return AdapterResult.of(AdapterResult.Code.APPLIED, "test apply");
        }
    }

    private HostTestMain() {}
}
