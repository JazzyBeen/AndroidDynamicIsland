package io.github.jazzybeen.androiddynamicisland

import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService
import io.github.jazzybeen.androiddynamicisland.system.IslandService

/** Deterministic, non-personal background for checking real overlay refraction. Debug APK only. */
class GlassBackdropActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        IslandSettings(this).apply { introduced(true); enabled(true); vpnPackage(""); liveBackdrop(true) }
        IslandService.ensureStarted(this)
        setContent {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF777B78))
                val cell = 32f
                var x = 0f
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha=.65f), Offset(x,0f), Offset(x,size.height),2f)
                    x += cell
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.Black.copy(alpha=.55f), Offset(0f,y), Offset(size.width,y),2f)
                    y += cell
                }
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE;textSize=42f }
                drawContext.canvas.nativeCanvas.drawText("BACKGROUND  •  0123456789",70f,220f,paint)
                drawContext.canvas.nativeCanvas.drawText("Local glass refraction test",70f,size.height*.65f,paint)
            }
        }
        handler.postDelayed({ showWhenReady(android.os.SystemClock.elapsedRealtime()+15000) },1500)
        handler.postDelayed({ IslandAccessibilityService.current()?.hideIsland() },45000)
    }
    private fun showWhenReady(deadline:Long){
        val service=IslandAccessibilityService.current()
        if(service!=null)service.showIsland("Dynamic Island","Стекло · локальный фон",false,true)
        else if(android.os.SystemClock.elapsedRealtime()<deadline)handler.postDelayed({showWhenReady(deadline)},200)
    }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null);IslandAccessibilityService.current()?.hideIsland();super.onDestroy() }
}
