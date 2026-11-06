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
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationHub
import io.github.jazzybeen.androiddynamicisland.navigation.NavigationSnapshot
import io.github.jazzybeen.androiddynamicisland.activities.*

/** Opaque black island in a touch-through accessibility window at the real screen origin. */
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
    private var navigation:NavigationSnapshot?=null
    private var vpnMessage="";private var vpnConnected=false;private var vpnComplete=true
    private lateinit var satellite:VpnSatellite
    private var switching=false
    private var satelliteRequest=0
    private var unsubscribe:(()->Unit)?=null
    private var unsubscribeActivities:(()->Unit)?=null
    private var satelliteMedia=false
    private var vpnSatelliteUntil=0L
    private fun satelliteHide(){satellite.hide()}

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
        satellite=VpnSatellite(service,{view.splitActivities(false);view.postDelayed({if(!switching&&android.os.SystemClock.elapsedRealtime()>=vpnSatelliteUntil)showMediaSatellite()},300)}){
            val media=ActivityHub.media.value
            if(satelliteMedia&&media!=null){satellite.hide();view.showActivity(media,true);view.postDelayed({if(navigation!=null&&ActivityHub.notice==null)resumeNavigation()},7500)}
            else{satelliteHide();switching=true;view.showNotice("VPN",vpnMessage,vpnComplete,vpnConnected);switching=false}
            updateTouchBounds();touchGuard.visibility=View.VISIBLE
        }
        touchGuard.setOnTouchListener{guard,event->
            when(event.actionMasked){
                MotionEvent.ACTION_OUTSIDE->{if(view.navigationActive())view.compactNavigation()else if(view.activityActive()){if(ActivityHub.notice!=null)ActivityHub.dismiss()else view.compactActivity()}else{dismissedByTouch=true;view.dismissAnimated()};false}
                MotionEvent.ACTION_DOWN->{
                    val bounds=view.restBounds()
                    val x=event.x+bounds.left;val y=event.y+bounds.top
                    if(!view.containsIsland(x,y)||view.closeButtonContains(x,y)){if(view.navigationActive())view.compactNavigation()else if(view.activityActive()){if(ActivityHub.notice!=null)ActivityHub.dismiss()else view.compactActivity()}else{dismissedByTouch=true;view.dismissAnimated()}}
                    else if(view.isCompact())view.expandNotice()
                    else if(view.navigationActive())view.dispatchNavigationTouch(event)
                    else if(view.activityActive())view.dispatchActivityTouch(event)
                    true
                }
                MotionEvent.ACTION_UP->{if(view.navigationActive())view.dispatchNavigationTouch(event)else if(view.activityActive())view.dispatchActivityTouch(event);guard.performClick();true}
                else->{if(view.navigationActive())view.dispatchNavigationTouch(event)else if(view.activityActive())view.dispatchActivityTouch(event);true}
            }
        }
        view.setOnNoticeHidden{touchGuard.visibility=View.INVISIBLE;if(!switching&&canShowNavigation())view.post{resumeNavigation()}}
        view.setOnDismissStarted{touchGuard.visibility=View.INVISIBLE}
        view.setOnGeometryChanged{updateTouchBounds()}
        unsubscribe=NavigationHub.listen{value,announce->
            navigation=value
            if(value==null){satellite.hide();if(view.navigationActive())view.clearNavigation()else resumeNavigation()}
            else if(canShowNavigation()){
                if(ActivityHub.notice!=null||view.noticeVisible()&&!view.navigationActive()&&!view.activityActive()&&!announce)return@listen
                if(announce){satelliteRequest++;satellite.hide();view.splitActivities(false)}
                dismissedByTouch=false;view.showNavigation(value,announce);updateTouchBounds();touchGuard.visibility=View.VISIBLE
            }
        }
        unsubscribeActivities=ActivityHub.listen{card,announce->
            if(!canShowNavigation())return@listen
            if(ActivityHub.notice!=null&&card!=null){satellite.hide();view.splitActivities(false);view.showActivity(card,announce);updateTouchBounds();touchGuard.visibility=View.VISIBLE}
            else if(navigation!=null){if(view.activityActive())resumeNavigation();showMediaSatellite()}
            else if(card!=null){if(view.noticeVisible()&&!view.activityActive()&&!view.navigationActive())return@listen;view.showActivity(card,announce);updateTouchBounds();touchGuard.visibility=View.VISIBLE}
            else if(view.activityActive())view.clearActivity()
        }
        resumeNavigation()
    }
    private fun canShowNavigation()=attached&&IslandSettings(service).enabled()&&service.getSystemService(PowerManager::class.java).isInteractive&&!service.getSystemService(KeyguardManager::class.java).isKeyguardLocked&&view.resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE
    private fun showSatellite(){
        if(!canShowNavigation())return
        vpnSatelliteUntil=android.os.SystemClock.elapsedRealtime()+if(vpnComplete)4800 else 30000
        satelliteMedia=false
        val request=++satelliteRequest
        view.splitActivities(true)
        if(view.navigationActive()&&!view.isCompact())view.compactNavigation()else if(view.activityActive()&&!view.isCompact())view.compactActivity()
        view.postDelayed({if(request==satelliteRequest&&(navigation!=null||ActivityHub.media.value!=null)&&canShowNavigation())satellite.show(view.satelliteBounds(),vpnMessage,vpnConnected,!vpnComplete)},320)
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
        if(navigation!=null||ActivityHub.media.value!=null){if(view.noticeVisible()&&!view.navigationActive()&&!view.activityActive()){view.showNotice(title,text,complete,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE}else showSatellite();return}
        view.showNotice(title,text,complete,connected)
        updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }

    fun beginNotice(){dismissedByTouch=false}
    fun showCompact(text:String,connected:Boolean){
        if(!attached||view.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)return
        vpnMessage=text;vpnConnected=connected;vpnComplete=true
        if(navigation!=null||ActivityHub.media.value!=null){if(view.noticeVisible()&&!view.navigationActive()&&!view.activityActive()){view.showNotice("VPN",text,true,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE}else showSatellite();return}
        dismissedByTouch=false;view.showCompactNotice(text,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }
    fun showOriginGuide(){if(attached){dismissedByTouch=false;view.showOriginGuide()}}
    private fun showMediaSatellite(){val media=ActivityHub.media.value?:run{if(satelliteMedia)satellite.hide();return};if(navigation==null||!canShowNavigation()||ActivityHub.notice!=null||android.os.SystemClock.elapsedRealtime()<vpnSatelliteUntil)return;satelliteMedia=true;val request=++satelliteRequest;view.splitActivities(true);view.postDelayed({if(request==satelliteRequest&&navigation!=null&&canShowNavigation()&&ActivityHub.notice==null)satellite.showMedia(view.satelliteBounds(),media)},320)}
    fun resumeNavigation(){
        if(!canShowNavigation())return
        val notice=ActivityHub.notice;val nav=navigation;val media=ActivityHub.media.value
        when{notice!=null->view.showActivity(notice,true);nav!=null->{view.showNavigation(nav,false);showMediaSatellite()};media!=null->view.showActivity(media,false);else->return}
        updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }
    fun hide() { notice++;satelliteRequest++;dismissedByTouch=false;satellite.hide();switching=true;view.hideNotice();switching=false }
    override fun close() {
        if (!attached) return
        attached = false
        unsubscribe?.invoke();unsubscribeActivities?.invoke();navigation=null;satellite.close()
        view.hideNotice()
        windows.removeView(touchGuard)
        windows.removeView(view)
    }
}
