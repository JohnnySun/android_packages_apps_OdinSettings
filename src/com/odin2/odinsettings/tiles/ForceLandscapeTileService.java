package com.odin2.odinsettings.tiles;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class ForceLandscapeTileService extends TileService {
    @Override
    public void onStartListening() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }
}
