package io.github.jazzybeen.androiddynamicisland.core;

/** Expanded cards grow with their content while preserving room below the island. */
public final class NavigationLayout {
    private NavigationLayout(){}
    public static float height(boolean details,boolean transfer,int rows,boolean mapReady,float screenHeightDp){
        float requested=!details?128:transfer&&!mapReady?Math.max(220,Math.min(360,128+38*Math.max(0,rows))):mapReady?360:288;
        return Math.max(36,Math.min(requested,screenHeightDp/2));
    }
}
