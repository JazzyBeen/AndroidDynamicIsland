package io.github.jazzybeen.androiddynamicisland.system;
import android.content.*;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnAutoConnect;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) VpnAutoConnect.onBoot(context);
        // After an app update the island restarts and reconnects only if the VPN was on before it.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) IslandService.ensureStarted(context);
    }
}
