package io.github.jazzybeen.androiddynamicisland.core;

/**
 * Pacing for connections nobody tapped. Each attempt waits longer than the last until one succeeds;
 * after {@link #GIVE_UP_AFTER} misses in a row it stops until the network comes back, so an expired
 * key or a dead server does not keep waking the phone.
 */
public final class AutoConnectPolicy {
    public static final int GIVE_UP_AFTER=6;
    private static final long[] PAUSE_MS={15_000,30_000,60_000,120_000,300_000,600_000};
    private int misses;
    private long nextAttemptAt;

    public boolean due(long now){return misses<GIVE_UP_AFTER&&now>=nextAttemptAt;}
    /** Counted as a miss up front: a start that never reports back must not be retried at once. */
    public void attempted(long now){misses++;nextAttemptAt=now+pauseAfter(misses);}
    public void succeeded(){misses=0;nextAttemptAt=0;}
    /** A fresh network is a new chance: try now and allow a new series of attempts. */
    public void networkReturned(long now){misses=Math.min(misses,GIVE_UP_AFTER-1);nextAttemptAt=Math.min(nextAttemptAt,now);}
    public int misses(){return misses;}
    public static long pauseAfter(int misses){return misses<=0?0:PAUSE_MS[Math.min(misses,PAUSE_MS.length)-1];}
}
