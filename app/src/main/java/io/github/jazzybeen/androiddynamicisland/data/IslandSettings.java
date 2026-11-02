package io.github.jazzybeen.androiddynamicisland.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class IslandSettings {
    private final SharedPreferences prefs;
    public IslandSettings(Context context) { prefs = context.getSharedPreferences("island", Context.MODE_PRIVATE); }
    public String vpnPackage() { return prefs.getString("vpnPackage", ""); }
    public void vpnPackage(String value) { prefs.edit().putString("vpnPackage", value == null ? "" : value).apply(); }
    public int margin() { return prefs.getInt("margin", 10); }
    public void margin(int value) { prefs.edit().putInt("margin", Math.max(4, Math.min(24,value))).apply(); }
    public boolean autoCorners() { return prefs.getBoolean("autoCorners",true); }
    public void autoCorners(boolean value) { prefs.edit().putBoolean("autoCorners",value).apply(); }
    public boolean liveBackdrop() { return prefs.getBoolean("liveBackdrop",false); }
    public void liveBackdrop(boolean value) { prefs.edit().putBoolean("liveBackdrop",value).apply(); }
    public int corners() { return prefs.getInt("corners",32); }
    public void corners(int value) { prefs.edit().putInt("corners",Math.max(12,Math.min(56,value))).apply(); }
    public boolean enabled() { return prefs.getBoolean("enabled", false); }
    public void enabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    public boolean introduced() { return prefs.getBoolean("introduced", false); }
    public void introduced(boolean value) { prefs.edit().putBoolean("introduced", value).apply(); }
    public boolean powerShortcut() { return prefs.getBoolean("powerShortcut", true); }
    public void powerShortcut(boolean value) { prefs.edit().putBoolean("powerShortcut", value).apply(); }
    public int verticalOffset() { return prefs.getInt("verticalOffset", 0); }
    public void verticalOffset(int value) { prefs.edit().putInt("verticalOffset", Math.max(-12, Math.min(64, value))).apply(); }
    public int blur() { return prefs.getInt("blur", 32); }
    public void blur(int value) { prefs.edit().putInt("blur", Math.max(0, Math.min(48, value))).apply(); }
}
