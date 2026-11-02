package io.github.jazzybeen.androiddynamicisland.system;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator;

/** Nothing from other senders is retained, logged, or routed. No INTERNET permission. */
public final class VpnNotificationListener extends NotificationListenerService {
    private static VpnNotificationListener instance;
    private static volatile boolean clientOngoing;
    private static volatile PendingIntent disconnect, connect;
    public static boolean clientOngoing() { return clientOngoing; }
    public static PendingIntent action(boolean connecting) { return connecting ? connect : disconnect; }
    public static void refreshSelection(){clientOngoing=false;disconnect=connect=null;if(instance!=null)instance.refresh();}
    @Override public void onListenerConnected() { instance = this; refresh(); }
    @Override public void onListenerDisconnected() { instance = null; clientOngoing = false; disconnect = connect = null; }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); }
    private void refresh() {
        boolean ongoing = false;
        PendingIntent stop = null, start = null;
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) {
                if (!VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) continue;
                Notification notification = sbn.getNotification();
                if (!sbn.isOngoing() && !Notification.CATEGORY_SERVICE.equals(notification.category)) continue;
                ongoing = true;
                if (notification.actions == null) continue;
                for (Notification.Action action : notification.actions) {
                    if (action.actionIntent == null || !VpnClientBridge.selectedPackage(this).equals(action.actionIntent.getCreatorPackage())) continue;
                    String label = action.title == null ? "" : action.title.toString().toLowerCase(Locale.ROOT);
                    if (label.contains("disconnect") || label.contains("stop") || label.contains("отключ") || label.contains("останов")) stop = action.actionIntent;
                    else if(label.equals("connect") || label.startsWith("подключить") || label.equals("подключиться")) start=action.actionIntent;
                }
            }
        } catch (SecurityException ignored) { }
        clientOngoing = ongoing; disconnect = stop; connect = start;
        VpnCoordinator.get(this).observationChanged();
    }
}
