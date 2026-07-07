package com.odin2.odinsettings.tiles;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public abstract class FailClosedTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        markUnavailable();
    }

    @Override
    public void onClick() {
        super.onClick();
        markUnavailable();
    }

    private void markUnavailable() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }
}
