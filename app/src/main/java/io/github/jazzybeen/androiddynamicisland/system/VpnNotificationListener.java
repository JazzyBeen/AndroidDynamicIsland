package io.github.jazzybeen.androiddynamicisland.system;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator;
import java.util.*;

/** Routes selected VPN actions, chosen notices and media sessions. Unrelated senders are ignored. */
public final class VpnNotificationListener extends NotificationListenerService {
    private io.github.jazzybeen.androiddynamicisland.activities.NoticeSource notices;
    private io.github.jazzybeen.androiddynamicisland.activities.MediaSource media;
    private static VpnNotificationListener instance;
    private static volatile boolean clientOngoing;
    private static volatile PendingIntent disconnect, connect;
    public static boolean clientOngoing() { return clientOngoing; }
    public static PendingIntent action(boolean connecting) { return connecting ? connect : disconnect; }
    public static void refreshSelection(){clientOngoing=false;disconnect=connect=null;if(instance!=null)instance.refresh();}
    public static boolean connected(){return instance!=null;}
    /** Visible text of one app's current notifications, e.g. the server a VPN client says it is connected to. */
    public static String textOf(String packageName){
        VpnNotificationListener listener=instance;if(listener==null||packageName==null)return "";
        StringBuilder text=new StringBuilder();
        try{StatusBarNotification[] all=listener.getActiveNotifications();if(all!=null)for(StatusBarNotification sbn:all){
            if(!packageName.equals(sbn.getPackageName()))continue;android.os.Bundle extras=sbn.getNotification().extras;
            for(String key:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_SUB_TEXT,Notification.EXTRA_BIG_TEXT}){CharSequence value=extras.getCharSequence(key);if(value!=null)text.append(value).append('\n');}
        }}catch(RuntimeException ignored){}
        return text.length()>2000?text.substring(0,2000):text.toString();
    }
    public static void refreshActivities(){if(instance!=null){if(instance.media!=null)instance.media.publish();io.github.jazzybeen.androiddynamicisland.activities.BannerPolicy.INSTANCE.update(instance,true);}}
    @Override public void onListenerConnected() { instance = this;notices=new io.github.jazzybeen.androiddynamicisland.activities.NoticeSource(this);if(media!=null)media.close();media=new io.github.jazzybeen.androiddynamicisland.activities.MediaSource(this);media.start(); refresh(); refreshActivities(); }
    @Override public void onListenerDisconnected() { instance = null; clientOngoing = false; disconnect = connect = null;if(media!=null){media.close();media=null;}io.github.jazzybeen.androiddynamicisland.activities.ActivityHub.INSTANCE.clearNotices();io.github.jazzybeen.androiddynamicisland.activities.BannerPolicy.INSTANCE.update(this,false); }
    @Override public void onDestroy(){onListenerDisconnected();super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn) { if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); try{if(notices!=null)notices.posted(sbn);}catch(RuntimeException ignored){} }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { if(notices!=null)notices.removed(sbn.getKey());if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); }
    private void refresh() {
        boolean ongoing = false;
        PendingIntent stop = null, start = null;
        try {
            StatusBarNotification[] active=getActiveNotifications();if(active==null)active=new StatusBarNotification[0];
            for (StatusBarNotification sbn : active) {
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
