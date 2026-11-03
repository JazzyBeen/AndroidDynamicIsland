package io.github.jazzybeen.androiddynamicisland.ui;
import android.Manifest;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import io.github.jazzybeen.androiddynamicisland.system.*;
public final class Permissions {
    private Permissions() {}
    public static boolean accessibility(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName target = new ComponentName(c, IslandAccessibilityService.class);
        if (enabled == null) return false;
        for (String entry : enabled.split(":")) if (target.equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }
    public static boolean listener(Context c) { return c.getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(c, VpnNotificationListener.class)); }
    public static boolean notifications(Context c) { return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED; }
    public static boolean battery(Context c) { return c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName()); }
    public static boolean ready(Context c) { return accessibility(c) && notifications(c); }
}
