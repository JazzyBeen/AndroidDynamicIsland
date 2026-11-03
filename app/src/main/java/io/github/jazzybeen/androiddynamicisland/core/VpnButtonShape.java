package io.github.jazzybeen.androiddynamicisland.core;

/** Structural fallback for the known client's unlabelled central power control. */
public final class VpnButtonShape {
    private VpnButtonShape(){}
    public static boolean matches(float left,float top,float right,float bottom,float screenWidth,float screenHeight){
        float width=right-left,height=bottom-top,centerX=(left+right)/2,centerY=(top+bottom)/2;
        return width>=screenWidth*.22f&&width<=screenWidth*.65f&&height>=width*.85f&&height<=width*1.15f&&Math.abs(centerX-screenWidth/2)<screenWidth*.1f&&centerY>screenHeight*.2f&&centerY<screenHeight*.7f;
    }
}
