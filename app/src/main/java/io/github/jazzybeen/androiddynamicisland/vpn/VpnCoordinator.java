package io.github.jazzybeen.androiddynamicisland.vpn;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import io.github.jazzybeen.androiddynamicisland.core.VpnOperation;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService;
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener;

public final class VpnCoordinator {
    private static VpnCoordinator instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private VpnOperation operation;
    private long lastCommand = -5000;
    private String message = "Готов к работе";
    private final Runnable poll = this::observe;
    private VpnCoordinator(Context context) { this.context = context.getApplicationContext(); }
    public static synchronized VpnCoordinator get(Context context) { if (instance == null) instance = new VpnCoordinator(context); return instance; }
    public String message() { return message; }
    public boolean busy() { return operation != null; }
    public void clientChanged(){main.removeCallbacks(poll);operation=null;message="VPN-клиент выбран";}
    public boolean anyVpn() {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return false;
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
        }
        return false;
    }
    public void toggle() { main.post(this::begin); }
    private void begin() {
        if (operation != null || SystemClock.elapsedRealtime() - lastCommand < 1600) return;
        lastCommand = SystemClock.elapsedRealtime();
        if (!new IslandSettings(context).enabled()) { announce("Сначала включите остров в настройках", true, false); return; }
        if (!VpnClientBridge.installed(context)) { announce("Выберите и настройте VPN-приложение", true, false); return; }
        boolean connected = anyVpn();
        if (connected && !VpnNotificationListener.clientOngoing()) { announce("VPN найден, но выбранный клиент не подтверждён", true, false); return; }
        operation = new VpnOperation(!connected, SystemClock.elapsedRealtime());
        announce(connected ? "Отключение…" : "Подключение…", false, connected);
        try {
            if (!connected) VpnClientBridge.connect(context); else VpnClientBridge.disconnect(context);
            main.postDelayed(poll, 350);
        } catch (RuntimeException error) { operation = null; announce(error.getMessage(), true, connected); }
    }
    public void observationChanged() { main.post(() -> { if (operation != null) observe(); }); }
    private void observe() {
        main.removeCallbacks(poll);
        if (operation == null) return;
        VpnOperation.Result result = operation.observe(anyVpn(), VpnNotificationListener.clientOngoing(), SystemClock.elapsedRealtime());
        if (result == VpnOperation.Result.WAITING) { main.postDelayed(poll, 350); return; }
        operation = null;
        switch (result) {
            case CONNECTED -> announce("Подключено", true, true);
            case DISCONNECTED -> announce("Отключено", true, false);
            default -> announce("Не подтверждено · проверьте VPN-клиент", true, false);
        }
    }
    public void preview() { main.post(() -> announce("Так будет выглядеть уведомление", true, true)); }
    private void announce(String text, boolean complete, boolean connected) {
        message = text == null ? "Не удалось выполнить команду" : text;
        IslandAccessibilityService service = IslandAccessibilityService.current();
        if (service != null) service.showIsland("VPN", message, complete, connected);
    }
}
