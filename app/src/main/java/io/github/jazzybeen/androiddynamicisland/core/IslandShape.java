package io.github.jazzybeen.androiddynamicisland.core;

/** Resolves platform corner metadata, including devices that report only one corner. */
public final class IslandShape {
    private IslandShape() {}
    public static float radius(float left, float right, boolean automatic, float fallback) {
        if (!automatic) return Math.max(0, fallback);
        if (left > 0 && right > 0) return Math.min(left, right);
        if (left > 0) return left;
        if (right > 0) return right;
        return Math.max(0, fallback);
    }
    /** Honor the requested inset while retaining the camera inside the compact capsule. */
    public static float dockTop(float cameraY,float seedHeight,float compactHeight,float inset){
        float clearance=Math.min(seedHeight/2,compactHeight/2);
        float low=Math.max(0,cameraY-compactHeight+clearance),high=Math.max(0,cameraY-clearance);
        return Math.max(low,Math.min(high,Math.max(0,inset)));
    }
    public static float expandedWidth(float screenWidth,float inset){return Math.max(1,screenWidth-2*inset);}
}
