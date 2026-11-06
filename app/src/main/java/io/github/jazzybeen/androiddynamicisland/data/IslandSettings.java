package io.github.jazzybeen.androiddynamicisland.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class IslandSettings {
    private final SharedPreferences prefs;
    public IslandSettings(Context context) { prefs = context.getSharedPreferences("island", Context.MODE_PRIVATE); }
    public String vpnPackage() { return prefs.getString("vpnPackage", ""); }
    public void vpnPackage(String value) { prefs.edit().putString("vpnPackage", value == null ? "" : value).apply(); }
    public boolean embeddedVpn() { return prefs.getBoolean("embeddedVpn",false); }
    public void embeddedVpn(boolean value) { prefs.edit().putBoolean("embeddedVpn",value).apply(); }
    public String vpnName() { return prefs.getString("vpnName","Мой VPN"); }
    public void vpnName(String value) { prefs.edit().putString("vpnName",value).apply(); }
    public void location(String code,String country,String city) { prefs.edit().putString("vpnCountryCode",code).putString("vpnCountry",country).putString("vpnCity",city).apply(); }
    public String vpnCountryCode() { return prefs.getString("vpnCountryCode",""); }
    public String vpnCountry() { return prefs.getString("vpnCountry",vpnName()); }
    public String vpnCity() { return prefs.getString("vpnCity","Мой сервер"); }
    public int responseTime() { return prefs.getInt("vpnResponseTime",-1); }
    public void responseTime(int value) { prefs.edit().putInt("vpnResponseTime",value).apply(); }
    public boolean autoOrigin() { return prefs.getBoolean("autoOrigin",true); }
    public void autoOrigin(boolean value) { prefs.edit().putBoolean("autoOrigin",value).apply(); }
    public int originX() { return prefs.getInt("originX",50); }
    public void originX(int value) { prefs.edit().putInt("originX",Math.max(0,Math.min(100,value))).apply(); }
    public int originY() { return prefs.getInt("originY",18); }
    public void originY(int value) { prefs.edit().putInt("originY",Math.max(0,Math.min(100,value))).apply(); }
    public boolean expectedConnected() { return prefs.getBoolean("expectedConnected",false); }
    public void expectedConnected(boolean value) { prefs.edit().putBoolean("expectedConnected",value).apply(); }
    /** Bring the embedded VPN up by itself: after boot, after a drop, when the network returns. */
    public boolean autoConnect() { return prefs.getBoolean("autoConnect",true); }
    public void autoConnect(boolean value) { prefs.edit().putBoolean("autoConnect",value).apply(); }
    /** The user's last word on the tunnel: false after they switch it off or another VPN takes over. */
    public boolean vpnWanted() { return prefs.getBoolean("vpnWanted",false); }
    public void vpnWanted(boolean value) { prefs.edit().putBoolean("vpnWanted",value).apply(); }
    public boolean connectionAlerts() { return prefs.getBoolean("connectionAlerts",true); }
    public void connectionAlerts(boolean value) { prefs.edit().putBoolean("connectionAlerts",value).apply(); }
    public boolean idleAlerts() { return prefs.getBoolean("idleAlerts",true); }
    public void idleAlerts(boolean value) { prefs.edit().putBoolean("idleAlerts",value).apply(); }
    public String providerDeviceId() {
        String value=prefs.getString("providerDeviceId","");
        if(value.isEmpty()){value=java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-","");prefs.edit().putString("providerDeviceId",value).apply();}
        return value;
    }
    public int margin() { return prefs.getInt("margin", 10); }
    public void margin(int value) { prefs.edit().putInt("margin", Math.max(4, Math.min(24,value))).apply(); }
    public boolean autoCorners() { return prefs.getBoolean("autoCorners",true); }
    public void autoCorners(boolean value) { prefs.edit().putBoolean("autoCorners",value).apply(); }
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
    public boolean material() { return prefs.getBoolean("material", false); }
    public void material(boolean value) { prefs.edit().putBoolean("material", value).apply(); }
    public int accent() { return prefs.getInt("accent", 0); }
    public void accent(int value) { prefs.edit().putInt("accent", Math.max(0, Math.min(5, value))).apply(); }
    public String providerHost() { return prefs.getString("providerHost", ""); }
    public void providerHost(String value) { prefs.edit().putString("providerHost", value == null ? "" : value).apply(); }
    public int blur() { return prefs.getInt("blur", 32); }
    public void blur(int value) { prefs.edit().putInt("blur", Math.max(0, Math.min(48, value))).apply(); }
}
