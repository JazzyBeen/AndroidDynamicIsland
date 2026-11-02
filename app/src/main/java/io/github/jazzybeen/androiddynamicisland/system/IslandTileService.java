package io.github.jazzybeen.androiddynamicisland.system;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.*;
import io.github.jazzybeen.androiddynamicisland.ui.ToggleActivity;
public final class IslandTileService extends TileService {
    @Override public void onStartListening() { Tile tile = getQsTile(); if (tile != null) { tile.setState(Tile.STATE_INACTIVE); tile.setSubtitle("Переключить VPN"); tile.updateTile(); } }
    // Android 12/13 have no PendingIntent overload; the deprecated call is confined to those OS versions.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") @SuppressWarnings("deprecation") @Override public void onClick() {
        unlockAndRun(() -> {
            Intent intent = new Intent(this, ToggleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            else startActivityAndCollapse(intent);
        });
    }
}
