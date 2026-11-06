package io.github.jazzybeen.androiddynamicisland.overlay;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.FrameLayout;
import io.github.jazzybeen.androiddynamicisland.core.IslandMotion;
import io.github.jazzybeen.androiddynamicisland.core.IslandShape;
import io.github.jazzybeen.androiddynamicisland.core.ActivityLayout;
import io.github.jazzybeen.androiddynamicisland.core.NavigationLayout;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationHub;
import io.github.jazzybeen.androiddynamicisland.navigation.MapKeyStore;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationContent;
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationSnapshot;
import io.github.jazzybeen.androiddynamicisland.activities.ActivityContent;
import io.github.jazzybeen.androiddynamicisland.activities.ActivityCard;
import io.github.jazzybeen.androiddynamicisland.activities.ActivityKind;

/** Single black capsule with temporal motion blur and a continuous expanded-to-compact morph. */
public final class IslandView extends FrameLayout {
    private final Paint add=new Paint(Paint.ANTI_ALIAS_FLAG),black=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final RectF blurBounds=new RectF();
    private final IslandMotion.Frame[] samples=new IslandMotion.Frame[5];
    private final IslandSettings settings;
    private final IslandContent expanded,compact;
    private final NavigationContent navExpanded,navCompact;
    private final ActivityContent activityExpanded,activityCompact;
    private ActivityCard activityCard;
    private boolean activity;
    private NavigationSnapshot navigationSnapshot;
    private boolean navigation,navigationDetails;
    private boolean splitActivities;
    private final float density;
    private long appeared,compactAt=-1,dismissAt=Long.MAX_VALUE,closingAt=-1,resumeAt=-1;
    private long guideUntil;
    private long reboundAt=-1;
    private IslandMotion.Frame closeFrom,resumeFrom;
    private boolean running,complete,pendingStart,touchCompact,compactOnly;
    private Rect camera;
    private float top,centerX,seedWidth,seedHeight,faceWidth,faceHeight,compactWidth,compactHeight,compactCenterX,cameraX,cameraY;
    private float screenRadius,faceRadius;
    private Runnable onHidden,onDismissStarted,onGeometryChanged;
    public void setOnNoticeHidden(Runnable callback){onHidden=callback;}
    public void setOnDismissStarted(Runnable callback){onDismissStarted=callback;}
    public void setOnGeometryChanged(Runnable callback){onGeometryChanged=callback;}
    public IslandView(Context context){
        super(context);settings=new IslandSettings(context);density=getResources().getDisplayMetrics().density;
        setClipChildren(false);setClipToPadding(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        add.setBlendMode(BlendMode.PLUS);black.setColor(Color.BLACK);
        expanded=new IslandContent(context,false);compact=new IslandContent(context,true);
        expanded.attachToRoot(this);addView(expanded.getView(),new LayoutParams(Math.round(dp(360)),Math.round(dp(82))));
        addView(compact.getView(),new LayoutParams(Math.round(dp(210)),Math.round(dp(36))));
        navExpanded=new NavigationContent(context,false);navCompact=new NavigationContent(context,true);
        addView(navExpanded.getView(),new LayoutParams(Math.round(dp(360)),Math.round(dp(128))));
        addView(navCompact.getView(),new LayoutParams(Math.round(dp(210)),Math.round(dp(36))));
        navExpanded.setOnCollapse(this::compactNavigation);
        activityExpanded=new ActivityContent(context,false);activityCompact=new ActivityContent(context,true);
        addView(activityExpanded.getView(),new LayoutParams(Math.round(dp(360)),Math.round(dp(224))));
        addView(activityCompact.getView(),new LayoutParams(Math.round(dp(210)),Math.round(dp(36))));
        activityExpanded.setOnCollapse(()->{if(activityCard!=null&&activityCard.getKind()==ActivityKind.NOTIFICATION)io.github.jazzybeen.androiddynamicisland.activities.ActivityHub.INSTANCE.dismiss();else compactActivity();});
        setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=31){
                RoundedCorner left=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT),right=insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                screenRadius=IslandShape.radius(left==null?0:left.getRadius(),right==null?0:right.getRadius(),true,0);
            }else screenRadius=IslandShape.radius(0,0,true,0);
            camera=null;DisplayCutout cutout=insets.getDisplayCutout();
            if(cutout!=null)for(Rect r:cutout.getBoundingRects())if(r.top<dp(80)&&r.width()>0&&(camera==null||Math.abs(r.centerX()-getWidth()/2f)<Math.abs(camera.centerX()-getWidth()/2f)))camera=new Rect(r);
            configureGeometry();return insets;
        });
    }
    private float dp(float n){return density*n;}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);configureGeometry();}
    @Override protected void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);if(c.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)hideNotice();}
    private void configureGeometry(){
        centerX=getWidth()/2f;
        faceHeight=navigation?dp(NavigationLayout.height(navigationDetails,navigationSnapshot!=null&&navigationSnapshot.getTransfer(),navigationSnapshot==null?0:navigationSnapshot.getLines().size(),new MapKeyStore(getContext()).exists(),getResources().getDisplayMetrics().heightPixels/density)):dp(82);compactHeight=dp(36);
        if(activity)faceHeight=dp(activityCard!=null&&activityCard.getKind()==ActivityKind.MEDIA?224:206);
        float diameter=camera==null?dp(18):Math.min(camera.width(),camera.height());
        seedWidth=seedHeight=diameter+dp(3);cameraX=camera==null?centerX:camera.exactCenterX();cameraY=camera==null?dp(18):camera.exactCenterY();
        if(!settings.autoOrigin()){cameraX=getWidth()*settings.originX()/100f;cameraY=dp(settings.originY());}
        // The same inset is used above, left and right; the camera remains inside the capsule.
        top=IslandShape.dockTop(cameraY,seedHeight,compactHeight,dp(settings.margin()));
        faceWidth=IslandShape.expandedWidth(getWidth(),top);compactWidth=ActivityLayout.compactWidth(faceWidth,density,(navigation||activity)&&splitActivities);
        faceRadius=IslandShape.radius(screenRadius,0,settings.autoCorners(),dp(settings.corners()));
        compactCenterX=Math.max(compactWidth/2,Math.min(getWidth()-compactWidth/2,cameraX));
        sizeContent(expanded,faceWidth,faceHeight);sizeContent(compact,compactWidth,compactHeight);
        sizeContentView(navExpanded.getView(),faceWidth,faceHeight);sizeContentView(navCompact.getView(),compactWidth,compactHeight);
        sizeContentView(activityExpanded.getView(),faceWidth,faceHeight);sizeContentView(activityCompact.getView(),compactWidth,compactHeight);
        if(onGeometryChanged!=null)onGeometryChanged.run();invalidate();
    }
    private void sizeContent(IslandContent content,float width,float height){sizeContentView(content.getView(),width,height);}
    private void sizeContentView(View content,float width,float height){LayoutParams p=(LayoutParams)content.getLayoutParams();if(p.width!=(int)width||p.height!=(int)height){p.width=(int)width;p.height=(int)height;content.setLayoutParams(p);}}
    private IslandMotion.Geometry geometry(){return new IslandMotion.Geometry(cameraX,cameraY,seedWidth,seedHeight,centerX,faceWidth,faceHeight,top,compactWidth,compactHeight,compactCenterX);}
    public void showNotice(String title,String text,boolean complete,boolean connected){
        long now=SystemClock.elapsedRealtime();
        if(activity){activity=false;activityCard=null;activityExpanded.pause();configureGeometry();}
        if(navigation){hideNotice();navigation=false;navigationSnapshot=null;navigationDetails=false;navExpanded.pauseMap();configureGeometry();}
        guideUntil=0;reboundAt=-1;
        if(!running){appeared=now;pendingStart=true;resumeAt=-1;}
        else if(closingAt>=0||compactOnly||(compactAt>=0&&now>=compactAt)){resumeFrom=frame(now);resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);}
        closingAt=-1;running=true;compactOnly=false;this.complete=complete;touchCompact=false;
        planCompact(now);expanded.update(text,complete,connected);compact.update(text,complete,connected);
        setVisibility(VISIBLE);requestApplyInsets();postInvalidateOnAnimation();
    }
    private void planCompact(long now){
        if(activity){compactAt=compactOnly?-1:Math.max(now,appeared+(long)(IslandMotion.OPEN+IslandMotion.SETTLE))+4500;dismissAt=Long.MAX_VALUE;return;}
        if(navigation){compactAt=compactOnly?-1:Math.max(now,appeared+(long)(IslandMotion.OPEN+IslandMotion.SETTLE))+(navigationDetails?6500:3000);dismissAt=Long.MAX_VALUE;return;}
        if(compactOnly){compactAt=-1;dismissAt=now+1500;return;}
        compactAt=complete?Math.max(now,appeared+(long)(IslandMotion.OPEN+IslandMotion.SETTLE))+(long)IslandMotion.EXPANDED_HOLD:-1;dismissAt=complete?compactAt+(long)(IslandMotion.MORPH+IslandMotion.COMPACT_HOLD):Long.MAX_VALUE;
    }
    public void showCompactNotice(String text,boolean connected){
        if(activity){activity=false;activityCard=null;activityExpanded.pause();configureGeometry();}
        if(navigation){hideNotice();navigation=false;navigationSnapshot=null;navigationDetails=false;navExpanded.pauseMap();configureGeometry();}
        long now=SystemClock.elapsedRealtime();guideUntil=0;reboundAt=-1;
        if(running){resumeFrom=frame(now);resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);pendingStart=false;}else{appeared=now;pendingStart=true;resumeAt=-1;}
        compactOnly=true;complete=true;running=true;closingAt=-1;touchCompact=true;planCompact(now);
        expanded.update(text,true,connected);compact.update(text,true,connected,text);
        if(onGeometryChanged!=null)onGeometryChanged.run();setVisibility(VISIBLE);requestApplyInsets();postInvalidateOnAnimation();
    }
    public void hideNotice(){boolean was=running;running=false;guideUntil=0;closingAt=-1;resumeAt=-1;reboundAt=-1;navExpanded.pauseMap();activityExpanded.pause();if(navigation&&navigationSnapshot==null)navigation=false;setVisibility(INVISIBLE);if(was&&onHidden!=null)onHidden.run();}
    public void showOriginGuide(){hideNotice();configureGeometry();guideUntil=SystemClock.elapsedRealtime()+2500;setVisibility(VISIBLE);requestApplyInsets();postInvalidateOnAnimation();}
    public void dismissAnimated(){if(!running||closingAt>=0)return;long now=SystemClock.elapsedRealtime();closeFrom=frame(now);closingAt=now;if(onDismissStarted!=null)onDismissStarted.run();postInvalidateOnAnimation();}
    public boolean isCompact(){return running&&closingAt<0&&touchCompact;}
    public void expandNotice(){
        if(!isCompact())return;
        if(navigation){expandNavigation();return;}
        if(activity){activityExpanded.update(activityCard);}
        long now=SystemClock.elapsedRealtime();resumeFrom=frame(now);resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);
        compactOnly=false;touchCompact=false;reboundAt=now+220;planCompact(now);if(onGeometryChanged!=null)onGeometryChanged.run();postInvalidateOnAnimation();
    }
    public Rect restBounds(){float w=touchCompact?compactWidth:faceWidth,h=touchCompact?compactHeight:faceHeight,cx=touchCompact?compactCenterX:centerX;return new Rect(Math.round(cx-w/2),Math.round(top),Math.round(cx+w/2),Math.round(top+h));}
    public Rect satelliteBounds(){float size=dp(36),left=ActivityLayout.satelliteLeft(getWidth(),compactCenterX,compactWidth,dp(8),size,dp(settings.margin()));return new Rect(Math.round(left),Math.round(top),Math.round(left+size),Math.round(top+size));}
    public void splitActivities(boolean split){
        if(splitActivities==split)return;long now=SystemClock.elapsedRealtime();IslandMotion.Frame from=running?frame(now):null;
        splitActivities=split;configureGeometry();if((navigation||activity)&&running&&from!=null){resumeFrom=from;resumeAt=now;postInvalidateOnAnimation();}
    }
    public boolean navigationActive(){return navigation&&running;}
    public boolean noticeVisible(){return running;}
    public void showNavigation(NavigationSnapshot snapshot,boolean announce){
        long now=SystemClock.elapsedRealtime();boolean same=navigation&&running;
        activity=false;activityCard=null;activityExpanded.pause();
        navigationSnapshot=snapshot;
        if(same&&(!announce||navigationDetails&&!touchCompact)){navExpanded.update(snapshot,navigationDetails);navCompact.update(snapshot,false);return;}
        IslandMotion.Frame previous=running?frame(now):null;
        navigation=true;navigationDetails=false;compactOnly=!announce;complete=true;navigationSnapshot=snapshot;guideUntil=0;closingAt=-1;reboundAt=-1;
        configureGeometry();navExpanded.update(snapshot,false);navCompact.update(snapshot,false);
        running=true;touchCompact=compactOnly;appeared=now;
        if(previous!=null){resumeFrom=previous;resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);pendingStart=false;}else{resumeAt=-1;pendingStart=true;}
        planCompact(now);setVisibility(VISIBLE);requestApplyInsets();if(onGeometryChanged!=null)onGeometryChanged.run();postInvalidateOnAnimation();
    }
    public void clearNavigation(){if(navigation){navigationSnapshot=null;navigationDetails=false;navExpanded.pauseMap();dismissAnimated();}}
    public boolean activityActive(){return activity&&running;}
    public void showActivity(ActivityCard card,boolean announce){
        long now=SystemClock.elapsedRealtime();boolean same=activity&&running&&activityCard!=null&&activityCard.getId().equals(card.getId());
        activityCard=card;activityExpanded.update(card);activityCompact.update(card);
        if(same&&!announce){if(touchCompact)activityExpanded.pause();invalidate();return;}
        IslandMotion.Frame previous=running?frame(now):null;
        navigation=false;navigationSnapshot=null;navigationDetails=false;navExpanded.pauseMap();activity=true;
        compactOnly=!announce;complete=true;closingAt=-1;guideUntil=0;reboundAt=-1;configureGeometry();running=true;touchCompact=compactOnly;appeared=now;
        if(previous!=null){resumeFrom=previous;resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);pendingStart=false;}else{resumeAt=-1;pendingStart=true;}
        planCompact(now);setVisibility(VISIBLE);requestApplyInsets();postInvalidateOnAnimation();
    }
    public void clearActivity(){if(activity){activityCard=null;activityExpanded.pause();dismissAnimated();}}
    public void compactActivity(){
        if(!activity||!running||isCompact())return;
        long now=SystemClock.elapsedRealtime();resumeFrom=frame(now);resumeAt=now;compactOnly=false;compactAt=now;dismissAt=Long.MAX_VALUE;reboundAt=-1;postInvalidateOnAnimation();
    }
    public boolean dispatchActivityTouch(MotionEvent event){
        if(!activity||isCompact())return false;IslandMotion.Frame f=frame(SystemClock.elapsedRealtime());
        MotionEvent copy=MotionEvent.obtain(event);copy.setLocation((event.getRawX()-(f.centerX()-f.width()/2))*faceWidth/f.width(),(event.getRawY()-f.top())*faceHeight/f.height());
        boolean handled=activityExpanded.getView().dispatchTouchEvent(copy);copy.recycle();planCompact(SystemClock.elapsedRealtime());return handled;
    }
    public void compactNavigation(){
        if(!navigation||!running||isCompact())return;
        long now=SystemClock.elapsedRealtime();resumeFrom=frame(now);resumeAt=now;compactOnly=false;compactAt=now;dismissAt=Long.MAX_VALUE;reboundAt=-1;postInvalidateOnAnimation();
    }
    private void expandNavigation(){
        if(navigationSnapshot==null)return;long now=SystemClock.elapsedRealtime();IslandMotion.Frame from=frame(now);
        navigationDetails=true;compactOnly=false;touchCompact=false;configureGeometry();navExpanded.update(navigationSnapshot,true);
        resumeFrom=from;resumeAt=now;appeared=now-(long)(IslandMotion.OPEN+IslandMotion.SETTLE);reboundAt=now+220;planCompact(now);
        if(onGeometryChanged!=null)onGeometryChanged.run();postInvalidateOnAnimation();
    }
    public boolean dispatchNavigationTouch(MotionEvent event){
        if(!navigation||isCompact())return false;IslandMotion.Frame f=frame(SystemClock.elapsedRealtime());
        MotionEvent copy=MotionEvent.obtain(event);copy.setLocation((event.getRawX()-(f.centerX()-f.width()/2))*faceWidth/f.width(),(event.getRawY()-f.top())*faceHeight/f.height());
        boolean handled=navExpanded.getView().dispatchTouchEvent(copy);copy.recycle();if(navigationDetails)planCompact(SystemClock.elapsedRealtime());return handled;
    }
    public boolean containsIsland(float x,float y){
        IslandMotion.Frame f=frame(SystemClock.elapsedRealtime());if(f.alpha()<=0)return false;
        float left=f.centerX()-f.width()/2,r=cornerRadius(f);
        if(x<left||x>left+f.width()||y<f.top()||y>f.top()+f.height())return false;
        float nx=Math.max(left+r,Math.min(left+f.width()-r,x)),ny=Math.max(f.top()+r,Math.min(f.top()+f.height()-r,y));
        return (x-nx)*(x-nx)+(y-ny)*(y-ny)<=r*r;
    }
    public boolean closeButtonContains(float x,float y){
        if(navigation||activity)return false;
        IslandMotion.Frame f=frame(SystemClock.elapsedRealtime());if(f.compact()>.2f||f.contentAlpha()<.5f)return false;
        float rx=dp(24)*f.width()/faceWidth,ry=dp(24)*f.height()/faceHeight;
        float dx=(x-(f.centerX()+f.width()/2-dp(40)*f.width()/faceWidth))/rx,dy=(y-(f.top()+f.height()/2))/ry;
        return dx*dx+dy*dy<=1;
    }
    private IslandMotion.Frame frame(long now){
        if(closingAt>=0&&now>=closingAt)return IslandMotion.closing(now-closingAt,geometry(),closeFrom);
        IslandMotion.Frame target=compactOnly?IslandMotion.openingCompact(now-appeared,geometry()):compactAt>=0&&now>=compactAt?IslandMotion.compacting(now-compactAt,geometry()):IslandMotion.opening(now-appeared,geometry());
        IslandMotion.Frame result=resumeAt>=0?IslandMotion.blend(resumeFrom,target,IslandMotion.ease((now-resumeAt)/220.0)):target;
        return reboundAt>=0?IslandMotion.rebound(result,now-reboundAt):result;
    }
    @Override protected void dispatchDraw(Canvas canvas){
        long now=SystemClock.elapsedRealtime();
        if(guideUntil>0){
            if(now>=guideUntil){guideUntil=0;setVisibility(INVISIBLE);return;}
            Paint marker=new Paint(Paint.ANTI_ALIAS_FLAG);marker.setColor(Color.rgb(10,132,255));marker.setStyle(Paint.Style.STROKE);marker.setStrokeWidth(dp(2));
            float r=seedHeight/2+dp(4);canvas.drawCircle(cameraX,cameraY,r,marker);
            canvas.drawLine(cameraX-r-dp(5),cameraY,cameraX+r+dp(5),cameraY,marker);canvas.drawLine(cameraX,cameraY-r-dp(5),cameraX,cameraY+r+dp(5),marker);
            marker.setStyle(Paint.Style.FILL);marker.setTextSize(dp(12));float labelX=Math.max(dp(8),Math.min(getWidth()-dp(140),cameraX+r+dp(10)));float labelY=Math.max(dp(14),cameraY+dp(5));canvas.drawText("Точка появления",labelX,labelY,marker);
            postInvalidateDelayed(Math.max(1,guideUntil-now));return;
        }
        if(!running)return;
        if(pendingStart){pendingStart=false;appeared=now;planCompact(now);}
        if(now>=dismissAt&&closingAt<0)dismissAnimated();
        if(closingAt>=0&&now-closingAt>IslandMotion.CLOSE+settings.blur()){hideNotice();return;}
        boolean small=compactOnly||(compactAt>=0&&now>=compactAt+IslandMotion.MORPH);
        if(small!=touchCompact){touchCompact=small;if(small&&navigation)navExpanded.pauseMap();if(small&&activity)activityExpanded.pause();if(onGeometryChanged!=null)onGeometryChanged.run();}
        IslandMotion.Frame current=frame(now),trail=frame(now-settings.blur());boolean moving=distance(current,trail)>.1f;
        if(moving&&settings.blur()>0){
            blurBounds.setEmpty();for(int i=0;i<5;i++){IslandMotion.Frame f=frame(now-settings.blur()*i/4);samples[i]=f;blurBounds.union(f.centerX()-f.width()/2,f.top(),f.centerX()+f.width()/2,f.top()+f.height());}blurBounds.inset(-dp(1),-dp(1));
            int accumulation=canvas.saveLayer(blurBounds,null);int remainder=255;
            for(int i=4;i>=0;i--){int alpha=i==0?remainder:51;remainder-=alpha;add.setAlpha(alpha);int sample=canvas.saveLayer(blurBounds,add);drawFrame(canvas,samples[i]);canvas.restoreToCount(sample);}canvas.restoreToCount(accumulation);
        }else drawFrame(canvas,current);
        boolean settling=now-appeared<IslandMotion.OPEN+IslandMotion.SETTLE||(compactAt>=0&&now>=compactAt&&now-compactAt<IslandMotion.MORPH+IslandMotion.SETTLE)||(reboundAt>=0&&now<reboundAt+IslandMotion.SETTLE);
        if(moving||settling||closingAt>=0||(resumeAt>=0&&now-resumeAt<220))postInvalidateOnAnimation();
        else if(complete){long next=compactAt>now?compactAt:dismissAt;if(next!=Long.MAX_VALUE)postInvalidateDelayed(Math.max(1,next-now));}
    }
    private static float distance(IslandMotion.Frame a,IslandMotion.Frame b){return Math.abs(a.width()-b.width())+Math.abs(a.height()-b.height())+Math.abs(a.top()-b.top())+100*(Math.abs(a.alpha()-b.alpha())+Math.abs(a.contentAlpha()-b.contentAlpha())+Math.abs(a.compact()-b.compact()));}
    private float cornerRadius(IslandMotion.Frame f){
        float baseHeight=faceHeight+(compactHeight-faceHeight)*f.compact();
        float baseRadius=faceRadius+(compactHeight/2-faceRadius)*f.compact();
        return Math.min(Math.min(f.width(),f.height())/2,baseRadius*f.height()/Math.max(1,baseHeight));
    }
    private void drawFrame(Canvas canvas,IslandMotion.Frame f){
        if(f.alpha()<=0)return;float left=f.centerX()-f.width()/2,r=cornerRadius(f);
        black.setAlpha(Math.round(255*f.alpha()));canvas.drawRoundRect(left,f.top(),left+f.width(),f.top()+f.height(),r,r,black);
        if(navigation){drawContentView(canvas,f,navExpanded.getView(),faceWidth,faceHeight,1-f.compact());drawContentView(canvas,f,navCompact.getView(),compactWidth,compactHeight,f.compact());}
        else if(activity){drawContentView(canvas,f,activityExpanded.getView(),faceWidth,faceHeight,1-f.compact());drawContentView(canvas,f,activityCompact.getView(),compactWidth,compactHeight,f.compact());}
        else{drawContent(canvas,f,expanded,faceWidth,faceHeight,1-f.compact());drawContent(canvas,f,compact,compactWidth,compactHeight,f.compact());}
    }
    private void drawContent(Canvas canvas,IslandMotion.Frame f,IslandContent content,float width,float height,float opacity){
        drawContentView(canvas,f,content.getView(),width,height,opacity);
    }
    private void drawContentView(Canvas canvas,IslandMotion.Frame f,View content,float width,float height,float opacity){
        if(f.contentAlpha()*opacity<=0)return;float left=f.centerX()-f.width()/2,r=cornerRadius(f);
        int save=canvas.saveLayerAlpha(left,f.top(),left+f.width(),f.top()+f.height(),Math.round(255*f.alpha()*f.contentAlpha()*opacity));
        clip.reset();clip.addRoundRect(left,f.top(),left+f.width(),f.top()+f.height(),r,r,Path.Direction.CW);canvas.clipPath(clip);
        canvas.translate(left,f.top());canvas.scale(f.width()/width,f.height()/height);drawChild(canvas,content,getDrawingTime());canvas.restoreToCount(save);
    }
    @Override protected void onDetachedFromWindow(){hideNotice();expanded.close();compact.close();navExpanded.close();navCompact.close();activityExpanded.close();activityCompact.close();super.onDetachedFromWindow();}
}
