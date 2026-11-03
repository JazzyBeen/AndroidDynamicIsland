package io.github.jazzybeen.androiddynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.WindowManager

/** Opaque black island in a touch-through accessibility window at the real screen origin. */
class IslandOverlay(service: AccessibilityService) : AutoCloseable {
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

    init {
        view.visibility = View.INVISIBLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, (220 * service.resources.displayMetrics.density).toInt(),
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
        touchGuard.setOnTouchListener{guard,event->
            when(event.actionMasked){
                MotionEvent.ACTION_OUTSIDE->{dismissedByTouch=true;view.dismissAnimated();false}
                MotionEvent.ACTION_DOWN->{
                    val bounds=view.restBounds()
                    val x=event.x+bounds.left;val y=event.y+bounds.top
                    if(!view.containsIsland(x,y)||view.closeButtonContains(x,y)){dismissedByTouch=true;view.dismissAnimated()}
                    else if(view.isCompact())view.expandNotice()
                    true
                }
                MotionEvent.ACTION_UP->{guard.performClick();true}
                else->true
            }
        }
        view.setOnNoticeHidden{touchGuard.visibility=View.INVISIBLE}
        view.setOnDismissStarted{touchGuard.visibility=View.INVISIBLE}
        view.setOnGeometryChanged{updateTouchBounds()}
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
        view.showNotice(title,text,complete,connected)
        updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }

    fun beginNotice(){dismissedByTouch=false}
    fun showCompact(text:String,connected:Boolean){
        if(!attached||view.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE)return
        dismissedByTouch=false;view.showCompactNotice(text,connected);updateTouchBounds();touchGuard.visibility=View.VISIBLE
    }
    fun showOriginGuide(){if(attached){dismissedByTouch=false;view.showOriginGuide()}}
    fun hide() { notice++;dismissedByTouch=false;view.hideNotice() }
    override fun close() {
        if (!attached) return
        attached = false
        view.hideNotice()
        windows.removeView(touchGuard)
        windows.removeView(view)
    }
}
