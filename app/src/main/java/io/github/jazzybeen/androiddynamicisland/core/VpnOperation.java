package io.github.jazzybeen.androiddynamicisland.core;

/** No successful result is inferred from an intent being accepted. */
public final class VpnOperation {
    public enum Result { WAITING, CONNECTED, DISCONNECTED, TIMEOUT }
    private final boolean connecting;
    private final long deadline;
    public VpnOperation(boolean connecting, long now) { this.connecting = connecting; deadline = now + 30000; }
    public boolean connecting() { return connecting; }
    public Result observe(boolean anyVpn, boolean clientNotification, long now) {
        if (connecting && anyVpn && clientNotification) return Result.CONNECTED;
        if (!connecting && !anyVpn) return Result.DISCONNECTED;
        return now >= deadline ? Result.TIMEOUT : Result.WAITING;
    }
}
