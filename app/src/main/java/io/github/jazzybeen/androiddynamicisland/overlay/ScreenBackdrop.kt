package io.github.jazzybeen.androiddynamicisland.overlay

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityWindowInfo
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import java.util.concurrent.Executors

/** Opt-in, memory-only top-band capture. Window screenshots exclude our overlay on API 34+. */
class ScreenBackdrop(
    private val service: AccessibilityService,
    private val onFrame: (Bitmap?) -> Unit
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var active = false
    private var generation = 0
    private var initial: (() -> Unit)? = null
    private val tick = Runnable { capture() }
    private val initialTimeout = Runnable {
        val ready=initial
        if(ready!=null){generation++;initial=null;onFrame(null);ready()}
    }

    fun start(ready: () -> Unit) {
        if (active) { ready(); return }
        active = true
        generation++
        initial = ready
        if(Build.VERSION.SDK_INT>=34){initial=null;ready()}
        else main.postDelayed(initialTimeout,2000)
        capture()
    }

    fun stop() {
        active = false
        generation++
        initial = null
        main.removeCallbacks(tick)
        main.removeCallbacks(initialTimeout)
        onFrame(null)
    }

    private fun capture() {
        if (!active) return
        val token = generation
        if (!IslandSettings(service).liveBackdrop() ||
            service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            finish(token, null, false)
            return
        }
        val metrics = service.resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        var target: AccessibilityWindowInfo? = null
        if (Build.VERSION.SDK_INT >= 34) {
            // App events are deliberately package-filtered. Do not reuse a cached foreground window.
            service.clearCache()
            val bandBottom = (220 * metrics.density).toInt()
            target = service.windows.filter {
                val bounds=Rect();it.getBoundsInScreen(bounds)
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && bounds.top < bandBottom && bounds.bottom > 0
            }
                .maxByOrNull { it.layer + if (it.isFocused) 10000 else 0 }
            if (target == null) { finish(token, null, true); return }
            target.getBoundsInScreen(screen)
        }
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                if (!active || generation != token) { result.hardwareBuffer.close();return }
                worker.execute {
                    val bitmap = try { crop(result, screen) } catch (_: Exception) { null }
                    main.post { finish(token, bitmap, Build.VERSION.SDK_INT >= 34) }
                }
            }
            override fun onFailure(errorCode: Int) {
                // Secure/unsupported windows remain protected. Never retain a previous app's pixels.
                main.post { finish(token, null, Build.VERSION.SDK_INT >= 34) }
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) service.takeScreenshotOfWindow(target!!.id, service.mainExecutor, callback)
            else service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } catch (_: RuntimeException) { finish(token, null, false) }
    }

    private fun crop(result: AccessibilityService.ScreenshotResult, bounds: Rect): Bitmap? {
        val buffer = result.hardwareBuffer
        var wrapped: Bitmap? = null
        var software: Bitmap? = null
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return null
            software = wrapped.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            val metrics = service.resources.displayMetrics
            val band = Bitmap.createBitmap(metrics.widthPixels, (220 * metrics.density).toInt()
                .coerceAtMost(metrics.heightPixels), Bitmap.Config.ARGB_8888)
            Canvas(band).apply {
                translate(bounds.left.toFloat(), bounds.top.toFloat())
                scale(bounds.width().toFloat() / software.width, bounds.height().toFloat() / software.height)
                drawBitmap(software, 0f, 0f, null)
            }
            return band
        } finally {
            software?.recycle()
            wrapped?.recycle()
            buffer.close()
        }
    }

    private fun finish(token: Int, bitmap: Bitmap?, repeat: Boolean) {
        if (!active || token != generation) { bitmap?.recycle(); return }
        onFrame(bitmap)
        val ready = initial
        initial = null
        main.removeCallbacks(initialTimeout)
        ready?.invoke()
        if (repeat && active) main.postDelayed(tick, 500)
    }

    override fun close() { stop(); worker.shutdown() }
}
