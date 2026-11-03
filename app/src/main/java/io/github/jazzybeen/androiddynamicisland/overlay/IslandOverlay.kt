package io.github.jazzybeen.androiddynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Opaque black island in a touch-through accessibility window at the real screen origin. */
class IslandOverlay(service: AccessibilityService) : AutoCloseable {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val view = IslandView(service)
    private var attached = true
    private var notice = 0

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
    }

    fun show(title: String, text: String, complete: Boolean, connected: Boolean) {
        if (!attached || view.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) return
        notice++
        view.showNotice(title,text,complete,connected)
    }

    fun hide() { notice++;view.hideNotice() }
    override fun close() {
        if (!attached) return
        attached = false
        view.hideNotice()
        windows.removeView(view)
    }
}
