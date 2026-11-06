package io.github.jazzybeen.androiddynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.*
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.data.VpnLocation
import io.github.jazzybeen.androiddynamicisland.ui.CountryFlagPainter
import io.github.jazzybeen.androiddynamicisland.core.IslandMotion

/** A separately touchable circular activity, never a full-screen input blocker. */
class VpnSatellite(service:AccessibilityService,private val onHidden:()->Unit,private val onClick:()->Unit):AutoCloseable{
    private val windows=service.getSystemService(WindowManager::class.java)
    private val handler=Handler(Looper.getMainLooper());private var animator:ValueAnimator?=null;private var attached=true
    private var shown=false;private var connected=false;private var busy=false;private var phase=0f
    private var mediaImage:Bitmap?=null;private var mediaMode=false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val settings=IslandSettings(service)
    private val widget=object:View(service){
        override fun onDraw(canvas:Canvas){
            val r=width/2f;paint.style=Paint.Style.FILL;paint.color=Color.BLACK;canvas.drawCircle(r,r,r,paint)
            val edge=width*.72f
            if(mediaMode){val bitmap=mediaImage;if(bitmap!=null){val save=canvas.save();val clip=Path().apply{addCircle(r,r,edge/2,Path.Direction.CW)};canvas.clipPath(clip);canvas.drawBitmap(bitmap,null,RectF(r-edge/2,r-edge/2,r+edge/2,r+edge/2),paint);canvas.restoreToCount(save)}else{paint.color=Color.WHITE;paint.textSize=width*.5f;canvas.drawText("♪",width*.28f,height*.7f,paint)}}else CountryFlagPainter.draw(canvas,settings.vpnCountryCode(),(width-edge)/2,(height-edge)/2,edge)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=width*.045f;paint.color=if(connected)Color.rgb(48,209,88)else Color.rgb(255,105,97)
            if(busy){paint.color=Color.rgb(255,214,10);canvas.drawArc(3f,3f,width-3f,height-3f,phase,220f,false,paint);phase=(phase+8)%360;postInvalidateDelayed(32)}else canvas.drawCircle(r,r,r-paint.strokeWidth,paint)
        }
    }.apply{visibility=View.INVISIBLE;scaleX=.05f;scaleY=.05f;alpha=0f;isClickable=true;contentDescription="Состояние VPN";setOnClickListener{onClick()}}
    private val params=WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT).apply{
        gravity=Gravity.TOP or Gravity.LEFT;layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;setFitInsetsTypes(0);title="Dynamic Island VPN activity"
    }
    private val expiry=Runnable{hide()}
    init{windows.addView(widget,params)}
    fun show(bounds:Rect,message:String,connected:Boolean,busy:Boolean){
        mediaMode=false;mediaImage=null
        showInternal(bounds,"VPN: $message",connected,busy,false)
    }
    fun showMedia(bounds:Rect,card:io.github.jazzybeen.androiddynamicisland.activities.ActivityCard){mediaMode=true;mediaImage=card.image;showInternal(bounds,card.title,card.media?.playing==true,false,true)}
    private fun showInternal(bounds:Rect,message:String,connected:Boolean,busy:Boolean,persistent:Boolean){
        if(!attached||bounds.isEmpty)return;this.connected=connected;this.busy=busy;widget.contentDescription=message
        params.x=bounds.left;params.y=bounds.top;params.width=bounds.width();params.height=bounds.height();windows.updateViewLayout(widget,params)
        widget.invalidate();handler.removeCallbacks(expiry);if(!busy&&!persistent)handler.postDelayed(expiry,4500)
        if(!shown){shown=true;widget.visibility=View.VISIBLE;animate(true)}
    }
    private fun animate(enter:Boolean){animator?.cancel();val start=widget.scaleX.takeIf{it>0&&widget.visibility==View.VISIBLE}?:.05f
        animator=ValueAnimator.ofFloat(0f,1f).apply{duration=if(enter)420 else 260;addUpdateListener{val t=it.animatedValue as Float;val p=IslandMotion.ease(if(enter)t*420/240.0 else t.toDouble()).toFloat();val pulse=if(enter)IslandMotion.settle(t*420-240.0).toFloat()else 0f;val scale=if(enter)start+(1-start)*p else start*(1-p)
            widget.scaleX=scale*(1-.018f*pulse);widget.scaleY=scale*(1+.065f*pulse);widget.alpha=if(enter)minOf(1f,t*4)else 1-p
            val blur=kotlin.math.sin(p*Math.PI).toFloat()*settings.blur()/4f;if(android.os.Build.VERSION.SDK_INT>=31){if(blur>.3f)widget.setRenderEffect(RenderEffect.createBlurEffect(blur,blur,Shader.TileMode.CLAMP))else widget.setRenderEffect(null)}
            if(!enter&&t>=1){widget.visibility=View.INVISIBLE;if(android.os.Build.VERSION.SDK_INT>=31)widget.setRenderEffect(null)}
        };start()}
    }
    fun hide(){handler.removeCallbacks(expiry);if(shown){shown=false;onHidden();animate(false)}}
    override fun close(){attached=false;animator?.cancel();handler.removeCallbacksAndMessages(null);windows.removeView(widget)}
}
