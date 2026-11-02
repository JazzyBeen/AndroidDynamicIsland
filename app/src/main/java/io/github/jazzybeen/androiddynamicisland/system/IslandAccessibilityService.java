package io.github.jazzybeen.androiddynamicisland.system;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.*;
import java.util.*;
import io.github.jazzybeen.androiddynamicisland.core.PowerMenuGate;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.overlay.IslandOverlay;
import io.github.jazzybeen.androiddynamicisland.vpn.*;

public final class IslandAccessibilityService extends AccessibilityService {
    private static IslandAccessibilityService instance;
    private final PowerMenuGate power = new PowerMenuGate();
    private final Handler main = new Handler(Looper.getMainLooper());
    private IslandOverlay overlay;
    private long disconnectDeadline;
    private boolean clickedDisconnect;
    private boolean connectingClient;
    public void configureClient(){disconnectDeadline=0;clickedDisconnect=false;main.removeCallbacks(disconnectPoll);android.accessibilityservice.AccessibilityServiceInfo info=getServiceInfo();info.packageNames=new String[]{"com.android.systemui","com.miui.systemui","com.miui.globalpower",VpnClientBridge.selectedPackage(this)};setServiceInfo(info);}
    private final BroadcastReceiver screen = new BroadcastReceiver() { @Override public void onReceive(Context context, Intent intent) { if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) hideIsland(); } };
    public static IslandAccessibilityService current() { return instance; }
    @Override protected void onServiceConnected() {
        instance = this; configureClient(); overlay = new IslandOverlay(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        IslandService.ensureStarted(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        IslandSettings settings = new IslandSettings(this);
        if (!settings.enabled()) return;
        String owner = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (VpnClientBridge.selectedPackage(this).equals(owner)) { if (disconnectDeadline > 0) tryDisconnect(); return; }
        if (!settings.powerShortcut()) return;
        boolean system = owner.equals("com.android.systemui") || owner.equals("com.miui.systemui") || owner.equals("com.miui.globalpower");
        if (!system && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null) owner = root.getPackageName().toString();
        String windowClass = event.getClassName() == null ? "" : event.getClassName().toString();
        StringBuilder labels = new StringBuilder();
        if (system && root != null && !PowerMenuGate.isPowerMenu(owner, windowClass, "")) collectLabels(root, labels, new int[]{0}, false);
        boolean trigger = power.update(owner, windowClass, labels.toString(), SystemClock.elapsedRealtime());
        if (!trigger) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) { showIsland("VPN", "Сначала разблокируйте телефон", true, false); return; }
        performGlobalAction(GLOBAL_ACTION_BACK); // Only dismiss the recognized power menu. Never press its power-off/restart actions.
        main.postDelayed(() -> VpnCoordinator.get(this).toggle(), 240);
    }
    private static void collectLabels(AccessibilityNodeInfo node, StringBuilder text, int[] count, boolean inPowerMenu) {
        if (++count[0] > 220 || text.length() > 5000) return;
        String id = node.getViewIdResourceName();
        if (id != null && (id.contains("global_action") || id.contains("power_menu") || id.contains("power_off") || id.contains("shutdown") || id.contains("reboot"))) inPowerMenu = true;
        if (inPowerMenu) {
            text.append(" [system-power-actions] ");
            if (node.getText() != null) text.append(' ').append(node.getText());
            if (node.getContentDescription() != null) text.append(' ').append(node.getContentDescription());
        }
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) collectLabels(child, text, count, inPowerMenu); }
    }
    public void requestClientAction(boolean connecting) {
        connectingClient=connecting;
        clickedDisconnect = false; disconnectDeadline = SystemClock.elapsedRealtime() + 7000;
        VpnClientBridge.open(this, false); main.postDelayed(this::tryDisconnect, 450);
    }
    private void tryDisconnect() {
        if (clickedDisconnect || disconnectDeadline <= 0) return;
        if (SystemClock.elapsedRealtime() >= disconnectDeadline) { disconnectDeadline = 0; showIsland("VPN", "Нажмите кнопку VPN в выбранном приложении", true, false); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && VpnClientBridge.selectedPackage(this).contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            List<AccessibilityNodeInfo> candidates = new ArrayList<>();
            findDisconnect(root, candidates, new int[]{0});
            if (candidates.size() == 1 && candidates.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)) { clickedDisconnect = true; disconnectDeadline = 0; return; }
        }
        main.removeCallbacks(disconnectPoll); main.postDelayed(disconnectPoll, 400);
    }
    private final Runnable disconnectPoll = this::tryDisconnect;
    private void findDisconnect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> output, int[] count) {
        if (++count[0] > 220) return;
        if (node.isPassword() || node.isEditable()) return;
        String label = (node.getContentDescription() != null ? node.getContentDescription().toString() : node.getText() == null ? "" : node.getText().toString()).trim().toLowerCase(Locale.ROOT);
        Set<String> labels=connectingClient?Set.of("connect","connect vpn","подключить","подключиться","включить vpn","подключить vpn","включить впн"):Set.of("disconnect","disconnect vpn","отключить","отключиться","отключить vpn","отключить впн");
        if (labels.contains(label)) {
            AccessibilityNodeInfo button = node;
            for (int i = 0; i < 3 && button != null && !button.isClickable(); i++) button = button.getParent();
            if (button != null && button.isEnabled() && button.isVisibleToUser() && button.isClickable() && !button.isEditable()) {
                Rect bounds = new Rect(); button.getBoundsInScreen(bounds);
                if (bounds.width() < getResources().getDisplayMetrics().widthPixels * .9f && bounds.height() < getResources().getDisplayMetrics().heightPixels * .45f && !output.contains(button)) output.add(button);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) findDisconnect(child, output, count); }
    }
    public void showIsland(String title, String text, boolean complete, boolean connected) { main.post(() -> { if (overlay != null) overlay.show(title, text, complete, connected); }); }
    public void hideIsland() { if (overlay != null) overlay.hide(); }
    @Override public void onInterrupt() { hideIsland(); }
    @Override public void onDestroy() { instance = null; main.removeCallbacksAndMessages(null); try { unregisterReceiver(screen); } catch (IllegalArgumentException ignored) {} if (overlay != null) overlay.close(); super.onDestroy(); }
}
