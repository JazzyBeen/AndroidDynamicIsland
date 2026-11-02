package io.github.jazzybeen.androiddynamicisland.glass

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

object LiquidDialogs {
    @JvmStatic fun show(activity:Activity,title:String,message:String,positive:String,onPositive:Runnable,negative:String,onNegative:Runnable?) {
        val dialog=ComponentDialog(activity)
        var handled=false
        val view=ComposeView(dialog.context)
        view.setContent {
            val backdrop=rememberCanvasBackdrop { drawRect(Brush.linearGradient(listOf(Color(0xFF242426),Color(0xFF161618),Color(0xFF101012)))) }
            Column(Modifier.fillMaxWidth().padding(20.dp).liquidSurface(backdrop,30f,.06f).padding(24.dp)) {
                Label(title,23,Color.White,true)
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f,false).verticalScroll(rememberScrollState())) {Label(message,13,Color(0xFFD1D1D6))}
                Spacer(Modifier.height(22.dp))
                Box(Modifier.fillMaxWidth().height(50.dp).liquidSurface(backdrop,25f,.2f).clickable{handled=true;dialog.dismiss();onPositive.run()},contentAlignment=Alignment.Center){Label(positive,13,Color.White,true)}
                Box(Modifier.fillMaxWidth().height(44.dp).clickable{handled=true;dialog.dismiss();onNegative?.run()},contentAlignment=Alignment.Center){Label(negative,12,Color(0xFF99999F))}
            }
        }
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        dialog.window?.setDimAmount(.82f)
        dialog.setOnDismissListener {if(!handled)onNegative?.run()}
        dialog.show()
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
