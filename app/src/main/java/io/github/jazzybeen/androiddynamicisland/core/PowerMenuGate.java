package io.github.jazzybeen.androiddynamicisland.core;

import java.util.Locale;

/** Detects a visible system power menu, never raw power-key events or arbitrary app text. */
public final class PowerMenuGate {
    private boolean visible;
    private long lastTrigger = -10000;
    public void dismissed(){visible=false;}
    public boolean update(String owner, String windowClass, String labels, long now) {
        boolean match = isPowerMenu(owner, windowClass, labels);
        boolean trigger = match && !visible && now - lastTrigger > 1800;
        visible = match;
        if (trigger) lastTrigger = now;
        return trigger;
    }
    public static boolean isPowerMenu(String owner, String windowClass, String labels) {
        if (!isSystemOwner(owner)) return false;
        String name = windowClass == null ? "" : windowClass.toLowerCase(Locale.ROOT);
        if (name.contains("globalactions") || name.contains("global_actions") || name.contains("powerdialog") || name.contains("shutdownview")) return true;
        String text = labels == null ? "" : labels.toLowerCase(Locale.ROOT);
        boolean off = text.contains("power off") || text.contains("shut down") || text.contains("выключ") || text.contains("отключение питания");
        boolean restart = text.contains("restart") || text.contains("reboot") || text.contains("перезагруз");
        return (text.contains("[system-power-actions]") || "miui.systemui.plugin".equals(owner) && text.contains("[system-power-slider]")) && off && restart;
    }
    public static boolean isSystemOwner(String owner){return "com.android.systemui".equals(owner)||"com.miui.systemui".equals(owner)||"com.miui.globalpower".equals(owner)||"miui.systemui.plugin".equals(owner);}
}
