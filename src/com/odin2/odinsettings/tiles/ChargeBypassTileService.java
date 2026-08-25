package com.odin2.odinsettings.tiles;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.odin2.odinsettings.hardware.ChargeBypassToggle;
import com.odin2.odinsettings.hardware.ChargeControlResult;
import com.odin2.odinsettings.hardware.ChargeController;
import com.odin2.odinsettings.hardware.ChargeMode;
import com.odin2.odinsettings.R;
import com.odin2.odinsettings.platform.AidlChargeController;
import com.odin2.odinsettings.platform.ChargeDisplayNames;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Stops charging while the adapter stays attached, from the notification shade.
 *
 * <p>The first tile here that does anything: the other two are deliberate
 * placeholders that report themselves unavailable. This one keeps the same
 * fail-closed principle by a different route — a daemon it cannot reach leaves
 * the tile unavailable rather than guessing at a state.
 */
public final class ChargeBypassTileService extends TileService {
    private static final String PREFERENCES = "charge_tile";
    private static final String KEY_REMEMBERED_MODE = "mode_before_bypass";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final ChargeController controller = AidlChargeController.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        submit(new Work() {
            @Override
            public ChargeControlResult run() {
                return controller.read();
            }
        });
    }

    @Override
    public void onClick() {
        super.onClick();
        submit(new Work() {
            @Override
            public ChargeControlResult run() {
                ChargeControlResult current = controller.read();
                if (!current.isAvailable()) {
                    // Never toggle from a state the daemon did not confirm.
                    return current;
                }
                ChargeBypassToggle.Decision decision =
                        ChargeBypassToggle.toggle(current.mode, readRememberedMode());
                ChargeControlResult applied = controller.apply(decision.target);
                if (applied.isAvailable()) {
                    writeRememberedMode(decision.remember);
                }
                return applied;
            }
        });
    }

    private interface Work {
        ChargeControlResult run();
    }

    private void submit(final Work work) {
        try {
            WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    final ChargeControlResult result = work.run();
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            render(result);
                        }
                    });
                }
            });
        } catch (RejectedExecutionException rejected) {
            render(null);
        }
    }

    private void render(ChargeControlResult result) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        // The label and the state line are set on every update rather than left
        // to the manifest. Without them the tile renders as a bare icon with no
        // text at all, which is how it shipped first and how it was impossible
        // to find in a shade full of labelled tiles.
        tile.setLabel(getString(R.string.tile_stop_charging));
        if (result == null || !result.isAvailable()) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setSubtitle(getString(R.string.tile_stop_charging_unavailable));
        } else {
            tile.setState(result.mode == ChargeMode.BYPASS
                    ? Tile.STATE_ACTIVE
                    : Tile.STATE_INACTIVE);
            tile.setSubtitle(getString(ChargeDisplayNames.modeName(result.mode)));
        }
        tile.setContentDescription(tile.getLabel() + " " + tile.getSubtitle());
        tile.updateTile();
    }

    private ChargeMode readRememberedMode() {
        String stored = preferences().getString(KEY_REMEMBERED_MODE, null);
        if (stored == null) {
            return null;
        }
        try {
            return ChargeMode.fromPreferenceValue(stored);
        } catch (IllegalArgumentException forgotten) {
            return null;
        }
    }

    private void writeRememberedMode(ChargeMode mode) {
        SharedPreferences.Editor editor = preferences().edit();
        if (mode == null) {
            editor.remove(KEY_REMEMBERED_MODE);
        } else {
            editor.putString(KEY_REMEMBERED_MODE, mode.preferenceValue);
        }
        editor.apply();
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }
}
