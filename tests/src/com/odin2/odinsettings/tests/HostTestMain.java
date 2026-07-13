package com.odin2.odinsettings.tests;

import com.odin2.odinsettings.display.ExternalDisplayPolicy;
import com.odin2.odinsettings.domain.ControllerButton;
import com.odin2.odinsettings.domain.ControllerProfile;
import com.odin2.odinsettings.domain.ControllerProfiles;
import com.odin2.odinsettings.hardware.AdapterCapability;
import com.odin2.odinsettings.hardware.AdapterResult;
import com.odin2.odinsettings.hardware.AdapterStatus;
import com.odin2.odinsettings.hardware.DisabledHardwareAdapter;
import com.odin2.odinsettings.hardware.HardwareAdapter;
import com.odin2.odinsettings.policy.DeviceIdentity;
import com.odin2.odinsettings.policy.HardwareAccessPolicy;
import com.odin2.odinsettings.service.ControllerProfileCoordinator;
import com.odin2.odinsettings.service.ExternalDisplayCoordinator;

import java.util.EnumSet;

public final class HostTestMain {
    private static int tests;

    public static void main(String[] args) {
        standardProfileIsIdentity();
        flippedProfileSwapsOnlyFaceButtons();
        unknownStoredProfileFallsBackToStandard();
        identityPolicyAcceptsOnlyProvenDeviceProductPairs();
        unknownDeviceNeverReachesAdapter();
        recognizedDeviceStillFailsClosedWithoutAdapter();
        capabilityGatePreventsUnsupportedCalls();
        reviewedAdapterCanReceiveRecognizedProfile();
        externalDisplayUsesTheSameIdentityAndCapabilityGates();
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

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void pass() {
        tests += 1;
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
