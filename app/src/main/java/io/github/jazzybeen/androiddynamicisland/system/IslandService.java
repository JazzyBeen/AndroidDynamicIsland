package io.github.jazzybeen.androiddynamicisland.system;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import io.github.jazzybeen.androiddynamicisland.R;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.ui.MainActivity;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator;

public final class IslandService extends Service {
    public static final String STOP = "io.github.jazzybeen.androiddynamicisland.STOP";
    private ConnectivityManager connectivity;
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { changed(); }
        @Override public void onLost(Network network) { changed(); }
        @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) { changed(); }
        private void changed() { VpnCoordinator.get(IslandService.this).observationChanged(); }
    };
    public static void ensureStarted(Context context) {
        if (!new IslandSettings(context).enabled()) return;
        try { context.startForegroundService(new Intent(context, IslandService.class)); }
        catch (RuntimeException ignored) { /* Setup shows background-permission guidance. */ }
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("island_background", "Остров в фоне", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false); manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, IslandService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, channel.getId())
                .setSmallIcon(R.drawable.ic_island).setContentTitle("Dynamic Island активен")
                .setContentText("Долгое нажатие питания → переключение VPN")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Остановить остров", stop).build()).build();
        startForeground(11, notification);
        connectivity = getSystemService(ConnectivityManager.class);
        connectivity.registerNetworkCallback(new NetworkRequest.Builder().clearCapabilities().build(), callback);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            new IslandSettings(this).enabled(false);
            IslandAccessibilityService service = IslandAccessibilityService.current();
            if (service != null) service.hideIsland();
            stopSelf(); return START_NOT_STICKY;
        }
        if (!new IslandSettings(this).enabled()) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public void onDestroy() { if (connectivity != null) connectivity.unregisterNetworkCallback(callback); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
