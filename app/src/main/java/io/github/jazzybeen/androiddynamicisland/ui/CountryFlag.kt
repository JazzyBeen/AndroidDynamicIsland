package io.github.jazzybeen.androiddynamicisland.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import io.github.jazzybeen.androiddynamicisland.data.VpnLocation

/** Crisp circular flags for common stripe layouts; Android's flag glyph covers other countries. */
@Composable fun CountryFlag(code:String,edge:Int=46){
    Canvas(Modifier.size(edge.dp)){
        drawIntoCanvas{CountryFlagPainter.draw(it.nativeCanvas,code,0f,0f,size.width)}
    }
}
