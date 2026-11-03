package io.github.jazzybeen.androiddynamicisland.core;

/** One capsule travels out of the physical cutout, rests, compacts, and returns to the cutout. */
public final class IslandMotion {
    public static final double OPEN=420,SETTLE=180,MORPH=300,EXPANDED_HOLD=1400,COMPACT_HOLD=2000,SQUEEZE=240,RETRACT=180,CLOSE=SQUEEZE+RETRACT;
    // Smoothstep reaches 70% of the width contraction at approximately 63.7% of its duration.
    public static final double RETRACT_START=SQUEEZE*.637;
    public record Geometry(float cameraX,float cameraY,float seedWidth,float seedHeight,float centerX,float width,float height,float top,float compactWidth,float compactHeight,float compactCenterX) {}
    public record Frame(float centerX,float top,float width,float height,float alpha,float contentAlpha,float compact) {}
    public static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static double settle(double elapsed){double t=elapsed/SETTLE;if(t<=0||t>=1)return 0;return t<.35?ease(t/.35):1-ease((t-.35)/.65);}
    public static Frame opening(double elapsed,Geometry g){
        double p=ease(elapsed/OPEN),pulse=settle(elapsed-OPEN);
        return new Frame(lerp(g.cameraX(),g.centerX(),p),lerp(g.cameraY()-g.seedHeight()/2,g.top(),p),lerp(g.seedWidth(),g.width(),p)*(float)(1-.018*pulse),lerp(g.seedHeight(),g.height(),p)*(float)(1+.045*pulse),(float)ease(elapsed/70),(float)ease((p-.15)/.7),0);
    }
    public static Frame compacting(double elapsed,Geometry g){
        double p=ease(elapsed/MORPH),pulse=settle(elapsed-MORPH);
        return new Frame(lerp(g.centerX(),g.compactCenterX(),p),g.top(),lerp(g.width(),g.compactWidth(),p)*(float)(1-.018*pulse),lerp(g.height(),g.compactHeight(),p)*(float)(1+.065*pulse),1,1,(float)p);
    }
    public static Frame openingCompact(double elapsed,Geometry g){
        Geometry small=new Geometry(g.cameraX(),g.cameraY(),g.seedWidth(),g.seedHeight(),g.compactCenterX(),g.compactWidth(),g.compactHeight(),g.top(),g.compactWidth(),g.compactHeight(),g.compactCenterX());
        Frame f=opening(elapsed,small);return new Frame(f.centerX(),f.top(),f.width(),f.height(),f.alpha(),f.contentAlpha(),1);
    }
    public static Frame closing(double elapsed,Geometry g,Frame from){
        if(from.compact()>.8f){
            double squeeze=ease(elapsed/SQUEEZE),retract=ease((elapsed-RETRACT_START)/RETRACT);
            float almostCutout=g.seedWidth()*1.12f;
            return new Frame(lerp(from.centerX(),g.cameraX(),squeeze),lerp(from.top(),g.cameraY()-g.seedHeight()/2,retract),lerp(lerp(from.width(),almostCutout,squeeze),g.seedWidth(),retract),lerp(from.height(),g.seedHeight(),retract),from.alpha()*(float)(1-ease((retract-.25)/.75)),from.contentAlpha()*(float)(1-ease(elapsed/180)),from.compact());
        }
        double p=ease(elapsed/CLOSE);
        return new Frame(lerp(from.centerX(),g.cameraX(),p),lerp(from.top(),g.cameraY()-g.seedHeight()/2,p),lerp(from.width(),g.seedWidth(),p),lerp(from.height(),g.seedHeight(),p),from.alpha()*(float)(1-ease((p-.65)/.35)),from.contentAlpha()*(float)(1-ease(p/.65)),from.compact());
    }
    public static Frame rebound(Frame frame,double elapsed){
        double pulse=settle(elapsed);
        return new Frame(frame.centerX(),frame.top(),frame.width()*(float)(1-.018*pulse),frame.height()*(float)(1+.045*pulse),frame.alpha(),frame.contentAlpha(),frame.compact());
    }
    public static Frame blend(Frame a,Frame b,double p){return new Frame(lerp(a.centerX(),b.centerX(),p),lerp(a.top(),b.top(),p),lerp(a.width(),b.width(),p),lerp(a.height(),b.height(),p),lerp(a.alpha(),b.alpha(),p),lerp(a.contentAlpha(),b.contentAlpha(),p),lerp(a.compact(),b.compact(),p));}
    private static float lerp(float a,float b,double p){return (float)(a+(b-a)*Math.max(0,Math.min(1,p)));}
}
