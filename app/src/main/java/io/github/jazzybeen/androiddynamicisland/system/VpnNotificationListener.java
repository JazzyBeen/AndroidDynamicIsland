package io.github.jazzybeen.androiddynamicisland.system;

import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationHub;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationParser;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationSnapshot;
import java.util.*;

/** Routes selected VPN actions and opt-in Yandex navigation text. Unrelated senders are ignored. */
public final class VpnNotificationListener extends NotificationListenerService {
    private io.github.jazzybeen.androiddynamicisland.activities.NoticeSource notices;
    private io.github.jazzybeen.androiddynamicisland.activities.MediaSource media;
    private static final Set<String> MAP_PACKAGES=Set.of("ru.yandex.yandexmaps","ru.yandex.yandexnavi");
    private static VpnNotificationListener instance;
    private static volatile boolean clientOngoing;
    private static volatile PendingIntent disconnect, connect;
    public static boolean clientOngoing() { return clientOngoing; }
    public static PendingIntent action(boolean connecting) { return connecting ? connect : disconnect; }
    public static void refreshSelection(){clientOngoing=false;disconnect=connect=null;if(instance!=null)instance.refresh();}
    public static void refreshNavigation(){if(instance!=null)instance.refreshMaps();}
    public static boolean connected(){return instance!=null;}
    public static void refreshActivities(){if(instance!=null){if(instance.media!=null)instance.media.publish();io.github.jazzybeen.androiddynamicisland.activities.BannerPolicy.INSTANCE.update(instance,true);}}
    @Override public void onListenerConnected() { instance = this;notices=new io.github.jazzybeen.androiddynamicisland.activities.NoticeSource(this);if(media!=null)media.close();media=new io.github.jazzybeen.androiddynamicisland.activities.MediaSource(this);media.start(); refresh(); refreshMaps();refreshActivities(); }
    @Override public void onListenerDisconnected() { instance = null; clientOngoing = false; disconnect = connect = null;if(media!=null){media.close();media=null;}io.github.jazzybeen.androiddynamicisland.activities.ActivityHub.INSTANCE.clearNotices();io.github.jazzybeen.androiddynamicisland.activities.BannerPolicy.INSTANCE.update(this,false); NavigationHub.INSTANCE.sourceLost(); }
    @Override public void onDestroy(){onListenerDisconnected();super.onDestroy();}
    @Override public void onNotificationPosted(StatusBarNotification sbn) { if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); readMap(sbn);try{if(notices!=null)notices.posted(sbn);}catch(RuntimeException ignored){} }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { if(notices!=null)notices.removed(sbn.getKey());if (VpnClientBridge.selectedPackage(this).equals(sbn.getPackageName())) refresh(); if(NavigationHub.INSTANCE.enabled(this)&&MAP_PACKAGES.contains(sbn.getPackageName()))NavigationHub.INSTANCE.removed(sbn.getKey()); }
    private void refreshMaps(){if(!NavigationHub.INSTANCE.enabled(this))return;try{StatusBarNotification[] all=getActiveNotifications();if(all!=null){Arrays.sort(all,Comparator.comparingLong(StatusBarNotification::getPostTime));for(StatusBarNotification n:all)readMap(n);}}catch(SecurityException ignored){}}
    private void readMap(StatusBarNotification sbn){
        if(!NavigationHub.INSTANCE.enabled(this)||!MAP_PACKAGES.contains(sbn.getPackageName()))return;
        try{
        Notification n=sbn.getNotification();android.os.Bundle b=n.extras;
        List<String> rows=new ArrayList<>();CharSequence[] lines=b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)for(CharSequence line:lines)if(line!=null)rows.add(line.toString());
        CharSequence sub=b.getCharSequence(Notification.EXTRA_SUB_TEXT);if(sub!=null)rows.add(sub.toString());
        CharSequence title=b.getCharSequence(Notification.EXTRA_TITLE),text=b.getCharSequence(Notification.EXTRA_BIG_TEXT);if(text==null)text=b.getCharSequence(Notification.EXTRA_TEXT);
        NavigationSnapshot result=NavigationParser.INSTANCE.parse(sbn.getKey(),title==null?"":title.toString(),text==null?"":text.toString(),rows,sbn.isOngoing(),n.category,android.os.SystemClock.elapsedRealtime());
        if(result!=null)NavigationHub.INSTANCE.update(result);
        }catch(RuntimeException|LinkageError ignored){NavigationHub.INSTANCE.sourceError();/* An optional source must not bring down the VPN process. */}
    }
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
