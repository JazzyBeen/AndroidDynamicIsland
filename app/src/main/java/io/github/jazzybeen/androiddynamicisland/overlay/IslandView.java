package io.github.jazzybeen.androiddynamicisland.overlay;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.FrameLayout;
import io.github.jazzybeen.androiddynamicisland.core.IslandMotion;
import io.github.jazzybeen.androiddynamicisland.core.IslandShape;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.glass.LiquidIsland;

/** Native compositor retains the desktop timing and temporal blur around a glass Compose surface. */
public final class IslandView extends FrameLayout {
    private final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IslandSettings settings;
    private final LiquidIsland glass;
    private long appeared, dismissAt;
    private boolean running, complete, pendingStart;
    private Rect camera;
    private float top, centerX, smallWidth, smallHeight, faceWidth, faceHeight;
    private float screenRadius;
    private String lastTitle="VPN",lastText="";
    private boolean lastConnected;
    private final float density;
    private Runnable onHidden;
    public void setOnNoticeHidden(Runnable callback){onHidden=callback;}
    public void setBackdrop(Bitmap bitmap){glass.setBackdrop(bitmap,top,top);invalidate();}
    public IslandView(Context context) {
        super(context); settings = new IslandSettings(context); density = getResources().getDisplayMetrics().density;
        setClipChildren(false); setClipToPadding(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        add.setBlendMode(BlendMode.PLUS);
        glass = new LiquidIsland(context); glass.attachToRoot(this);addView(glass.getView(),new LayoutParams(Math.round(dp(320)),Math.round(dp(96))));
        setOnApplyWindowInsetsListener((v,insets) -> {
            RoundedCorner left=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT),right=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
            screenRadius=IslandShape.radius(left==null?0:left.getRadius(),right==null?0:right.getRadius(),true,0);
            camera = null; DisplayCutout cutout = insets.getDisplayCutout();
            if(cutout!=null) for(Rect r:cutout.getBoundingRects()) if(r.top<dp(80)&&r.width()>0&&(camera==null||Math.abs(r.centerX()-getWidth()/2f)<Math.abs(camera.centerX()-getWidth()/2f)))camera=new Rect(r);
            configureGeometry();return insets;
        });
    }
    private float dp(float n){return density*n;}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);configureGeometry();}
    @Override protected void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);if(configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)hideNotice();}
    private void configureGeometry(){
        centerX=getWidth()/2f;
        top=dp(settings.margin());
        smallHeight=Math.max(dp(24),camera==null?dp(24):camera.height()+dp(8));
        smallWidth=Math.max(dp(34),camera==null?dp(34):camera.width()+dp(12));
        float radius=IslandShape.radius(screenRadius,0,settings.autoCorners(),dp(settings.corners()));
        faceWidth=Math.max(dp(120),getWidth()-2*top);faceHeight=Math.max(Math.max(dp(112),smallHeight+dp(64)),radius*2);
        LayoutParams params=(LayoutParams)glass.getView().getLayoutParams();int w=(int)faceWidth,h=(int)faceHeight;
        if(params.width!=w||params.height!=h){params.width=w;params.height=h;glass.getView().setLayoutParams(params);}
        glass.update(lastTitle,lastText,complete,lastConnected,smallHeight/density,radius/density);
        invalidate();
    }
    public void showNotice(String title,String text,boolean complete,boolean connected){
        long now=SystemClock.elapsedRealtime();if(!running){appeared=now;pendingStart=true;}running=true;this.complete=complete;dismissAt=complete?now+3500:Long.MAX_VALUE;
        lastTitle=title;lastText=text;lastConnected=connected;configureGeometry();
        setVisibility(VISIBLE);requestApplyInsets();postInvalidateOnAnimation();
    }
    public void hideNotice(){boolean wasRunning=running;running=false;setVisibility(INVISIBLE);if(wasRunning&&onHidden!=null)onHidden.run();}
    private double openMs(){return IslandMotion.duration(smallHeight,top);}
    private IslandMotion.Frame frame(long now){boolean closing=now>=dismissAt;double phase=closing?openMs()-(now-dismissAt):now-appeared;return IslandMotion.frame(phase,smallWidth,smallHeight,faceWidth,faceHeight,top,closing);}
    @Override protected void dispatchDraw(android.graphics.Canvas canvas){
        if(!running)return;long now=SystemClock.elapsedRealtime();
        if(pendingStart){pendingStart=false;appeared=now;dismissAt=complete?now+3500:Long.MAX_VALUE;}
        if(now>=dismissAt&&now-dismissAt>openMs()+settings.blur()){hideNotice();return;}
        IslandMotion.Frame current=frame(now),trail=frame(now-settings.blur());
        boolean moving=Math.abs(current.width()-trail.width())+Math.abs(current.height()-trail.height())+Math.abs(current.top()-trail.top())>.1f;
        if(moving&&settings.blur()>0){
            int accumulation=canvas.saveLayer(0,0,getWidth(),getHeight(),null);int remainder=255;
            for(int i=4;i>=0;i--){int alpha=i==0?remainder:255/5;remainder-=alpha;add.setAlpha(alpha);int sample=canvas.saveLayer(0,0,getWidth(),getHeight(),add);drawGlass(canvas,frame(now-settings.blur()*i/4));canvas.restoreToCount(sample);}
            canvas.restoreToCount(accumulation);
        }else drawGlass(canvas,current);
        if(moving||now-appeared<openMs()+IslandMotion.SETTLE||now>=dismissAt)postInvalidateOnAnimation();
        else if(complete)postInvalidateDelayed(Math.max(1,dismissAt-now));
    }
    private void drawGlass(android.graphics.Canvas canvas,IslandMotion.Frame frame){int save=canvas.save();canvas.translate(centerX-frame.width()/2,frame.top());canvas.scale(frame.width()/faceWidth,frame.height()/faceHeight);super.dispatchDraw(canvas);canvas.restoreToCount(save);}
    @Override protected void onDetachedFromWindow(){hideNotice();glass.close();super.onDetachedFromWindow();}
}
