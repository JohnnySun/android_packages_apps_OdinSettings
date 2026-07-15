package com.odin2.odinsettings.platform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;

import com.odin2.odinsettings.policy.HardwareAccessPolicy;
import com.odin2.odinsettings.service.ControllerColdBootPolicy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ControllerColdBootReceiver extends BroadcastReceiver {
    private static final String TAG = "OdinControllerPrime";
    private static final int ODIN_GAMEPAD_VENDOR_ID = 0x2020;
    private static final int ODIN_GAMEPAD_PRODUCT_ID = 0x3001;
    private static final long START_DELAY_MILLIS = 10_000;
    private static final long DISPLAY_OFF_MILLIS = 1_000;
    private static final long PUBLICATION_SETTLE_MILLIS = 3_000;
    private static final long WAKE_LOCK_TIMEOUT_MILLIS = 8_000;

    private static boolean attemptConsumed;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        PendingResult pendingResult = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                runOnce(context.getApplicationContext());
            } finally {
                pendingResult.finish();
                executor.shutdown();
            }
        });
    }

    private static void runOnce(Context context) {
        if (!sleep(START_DELAY_MILLIS)) {
            return;
        }

        InputManager inputManager = context.getSystemService(InputManager.class);
        boolean controllerPresent = hasOdinGamepad(inputManager);
        boolean recognizedDevice = new HardwareAccessPolicy()
                .evaluate(AndroidDeviceIdentity.current()).allowed;
        ControllerColdBootPolicy.Decision decision = new ControllerColdBootPolicy()
                .decide(recognizedDevice, controllerPresent, attemptConsumed);
        Log.i(TAG, "cold-boot decision=" + decision);
        if (decision != ControllerColdBootPolicy.Decision.CYCLE_DISPLAY_ONCE) {
            return;
        }

        attemptConsumed = true;
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager == null) {
            Log.e(TAG, "PowerManager unavailable; display cycle not attempted");
            return;
        }

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, TAG + ":display-cycle");
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MILLIS);
        try {
            long sleepTime = SystemClock.uptimeMillis();
            powerManager.goToSleep(sleepTime,
                    PowerManager.GO_TO_SLEEP_REASON_APPLICATION,
                    PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE);
            if (!sleep(DISPLAY_OFF_MILLIS)) {
                return;
            }
            powerManager.wakeUp(SystemClock.uptimeMillis(),
                    PowerManager.WAKE_REASON_APPLICATION, TAG);
            if (!sleep(PUBLICATION_SETTLE_MILLIS)) {
                return;
            }
            Log.i(TAG, "one-shot display cycle complete; gamepad="
                    + hasOdinGamepad(inputManager));
        } catch (SecurityException exception) {
            Log.e(TAG, "display cycle denied", exception);
        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private static boolean hasOdinGamepad(InputManager inputManager) {
        if (inputManager == null) {
            return false;
        }
        for (int deviceId : inputManager.getInputDeviceIds()) {
            InputDevice device = inputManager.getInputDevice(deviceId);
            if (device != null
                    && device.getVendorId() == ODIN_GAMEPAD_VENDOR_ID
                    && device.getProductId() == ODIN_GAMEPAD_PRODUCT_ID) {
                return true;
            }
        }
        return false;
    }

    private static boolean sleep(long milliseconds) {
        try {
            TimeUnit.MILLISECONDS.sleep(milliseconds);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "cold-boot display cycle interrupted");
            return false;
        }
    }

    public ControllerColdBootReceiver() {}
}
