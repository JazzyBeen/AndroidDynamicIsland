package io.github.jazzybeen.androiddynamicisland.core;

/** Zero traffic is an informational event, once per idle spell, with a global cooldown. */
public final class TrafficIdleGate {
    public static final long IDLE_MS=20000,COOLDOWN_MS=120000;
    private long bytes=-1,idleSince=-1,lastNotice=-COOLDOWN_MS;
    private boolean notified;
    public boolean sample(long total,boolean active,long now){
        if(!active||total<0){bytes=-1;idleSince=-1;notified=false;return false;}
        if(bytes<0||total!=bytes){bytes=total;idleSince=now;notified=false;return false;}
        if(!notified&&now-idleSince>=IDLE_MS&&now-lastNotice>=COOLDOWN_MS){notified=true;lastNotice=now;return true;}
        return false;
    }
}
