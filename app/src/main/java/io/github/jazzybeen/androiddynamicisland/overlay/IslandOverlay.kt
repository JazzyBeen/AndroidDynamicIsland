package io.github.jazzybeen.androiddynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager
import android.app.KeyguardManager
import android.os.PowerManager
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.activities.*

/** Opaque island in a touch-through accessibility window at the real screen origin. */
class IslandOverlay(service: AccessibilityService) : AutoCloseable {
    private val service=service
    private val windows = service.getSystemService(WindowManager::class.java)
    private val view = IslandView(service)
    private val touchGuard=View(service).apply{
        visibility=View.INVISIBLE
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    private val touchParams=WindowManager.LayoutParams(
        1,1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT
    ).apply{
        gravity=Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        title="Dynamic Island outside touch"
    }
    private var attached = true
    private var notice = 0
    private var dismissedByTouch=false
    private var vpnMessage="";private var vpnConnected=false;private var vpnComplete=true
    private lateinit var satellite:VpnSatellite
    private var switching=false
    private var satelliteRequest=0
    private var unsubscribeActivities:(()->Unit)?=null

    init {
        view.visibility = View.INVISIBLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (480 * service.resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
            title = "Android Dynamic Island overlay"
        }
        windows.addView(view, params)
        windows.addView(touchGuard,touchParams)
        satellite=VpnSatellite(service,{view.splitActivities(false)}){
            satellite.hide();switching=true;view.showNotice("VPN",vpnMessage,vpnComplete,vpnConnected);switching=false
            updateTouchBounds();touchGuard.visibility=View.VISIBLE
        }
        touchGuard.setOnTouchListener{guard,event->
            when(event.actionMasked){
                MotionEvent.ACTION_OUTSIDE->{collapseOrDismiss();false}
                MotionEvent.ACTION_DOWN->{
                    val bounds=view.restBounds()
                    val x=event.x+bounds.left;val y=event.y+bounds.top
                    if(!view.containsIsland(x,y)||view.closeButtonContains(x,y))collapseOrDismiss()
                    else if(view.isCompact())view.expandNotice()
                    else if(view.activityActive())view.dispatchActivityTouch(event)
                    true
                }
                MotionEvent.ACTION_UP->{if(view.activityActive())view.dispatchActivityTouch(event);guard.performClick();true}
                else->{if(view.activityActive())view.dispatchActivityTouch(event);true}
            }
        }
        view.setOnNoticeHidden{touchGuard.visibility=View.INVISIBLE;if(!switching&&canShow())view.post{resumeActivities()}}
        view.setOnDismissStarted{touchGuard.visibility=View.INVISIBLE}
        view.setOnGeometryChanged{updateTouchBounds()}
        unsubscribeActivities=ActivityHub.listen{card,announce->
            if(!canShow())return@listen
            if(ActivityHub.notice!=null&&card!=null){satellite.hide();view.splitActivities(false);view.showActivity(card,announce);updateTouchBounds();touchGuard.visibility=View.VISIBLE}
            else if(card!=null){if(view.noticeVisible()&&!view.activityActive())return@listen;view.showActivity(card,announce);updateTouchBounds();touchGuard.visibility=View.VISIBLE}
            else if(view.activityActive())view.clearActivity()
        }
        resumeActivities()
    }
    private fun collapseOrDismiss(){if(view.activityActive()){if(ActivityHub.notice!=null)ActivityHub.dismiss()else view.compactActivity()}else{dismissedByTouch=true;view.dismissAnimated()}}
    private fun canShow()=attached&&IslandSettings(service).enabled()&&service.getSystemService(PowerManager::class.java).isInteractive&&!service.getSystemService(KeyguardManager::class.java).isKeyguardLocked&&view.resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE
    /** While media owns the island, VPN events appear in a separate circle beside it. */
    private fun showSatellite(){
        if(!canShow())return
        val request=++satelliteRequest
        view.splitActivities(true)
        if(view.activityActive()&&!view.isCompact())view.compactActivity()
        view.postDelayed({if(request==satelliteRequest&&ActivityHub.media.value!=null&&canShow())satellite.show(view.satelliteBounds(),vpnMessage,vpnConnected,!vpnComplete)},320)
    }

    private fun updateTouchBounds(){
        if(!attached)return
        val bounds=view.restBounds();if(bounds.isEmpty)return
        if(touchParams.x==bounds.left&&touchParams.y==bounds.top&&touchParams.width==bounds.width()&&touchParams.height==bounds.height())return
        touchParams.x=bounds.left;touchParams.y=bounds.top;touchParams.width=bounds.width();touchParams.height=bounds.height()
        windows.updateViewLayout(touchGuard,touchParams)
    }

    fun show(title: String, text: String, complete: Boolean, connected: Boolean) {
        if (!attached || dismissedByTouch || view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
        notice++
        vpnMessage=text;vpnComplete=complete;vpnConnected=connected
        if(ActivityHub.media.value!=null&&!(view.noticeVisible()&&!view.activityActive())){showSatellite();return}
        view.showNotice(title,text,complete,connected)
        updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }

    fun beginNotice(){dismissedByTouch=false}
    fun showCompact(text:String,connected:Boolean){
        if(!attached||view.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)return
        vpnMessage=text;vpnConnected=connected;vpnComplete=true
        if(ActivityHub.media.value!=null){if(view.noticeVisible()&&!view.activityActive()){view.showNotice("VPN",text,true,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE}else showSatellite();return}
        dismissedByTouch=false;view.showCompactNotice(text,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }
    fun showOriginGuide(){if(attached){dismissedByTouch=false;view.showOriginGuide()}}
    fun resumeActivities(){
        if(!canShow())return
        val notice=ActivityHub.notice;val media=ActivityHub.media.value
        when{notice!=null->view.showActivity(notice,true);media!=null->view.showActivity(media,false);else->return}
        updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }
    fun hide() { notice++;satelliteRequest++;dismissedByTouch=false;satellite.hide();switching=true;view.hideNotice();switching=false }
    override fun close() {
        if (!attached) return
        attached = false
        unsubscribeActivities?.invoke();satellite.close()
        view.hideNotice()
        windows.removeView(touchGuard)
        windows.removeView(view)
    }
}
