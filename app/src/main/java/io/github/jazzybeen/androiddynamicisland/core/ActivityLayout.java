package io.github.jazzybeen.androiddynamicisland.core;

/** Compact activities share a row; a satellite moves to the other side near a screen edge. */
public final class ActivityLayout {
    private ActivityLayout(){}
    public static float compactWidth(float available,float density,boolean split){return Math.min((split?134:210)*density,available*.62f);}
    public static float satelliteLeft(float screen,float center,float capsule,float gap,float size,float inset){
        float right=center+capsule/2+gap,left=center-capsule/2-gap-size;
        if(right+size<=screen-inset)return right;
        if(left>=inset)return left;
        return Math.max(inset,Math.min(screen-inset-size,right));
    }
}
