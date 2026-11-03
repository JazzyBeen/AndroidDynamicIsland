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
        val circle=Path().apply{addOval(Rect(Offset.Zero,size))}
        clipPath(circle){
            val stripes=when(code.uppercase()){
                "NL"->listOf(0xFFAE1C28,0xFFFFFFFF,0xFF21468B)
                "RU"->listOf(0xFFFFFFFF,0xFF0039A6,0xFFD52B1E)
                "DE"->listOf(0xFF111111,0xFFDD0000,0xFFFFCE00)
                "FR"->listOf(0xFF002654,0xFFFFFFFF,0xFFED2939)
                "IT"->listOf(0xFF009246,0xFFFFFFFF,0xFFCE2B37)
                "IE"->listOf(0xFF169B62,0xFFFFFFFF,0xFFFF883E)
                "UA"->listOf(0xFF0057B7,0xFFFFD700)
                "PL"->listOf(0xFFFFFFFF,0xFFDC143C)
                "AT"->listOf(0xFFED2939,0xFFFFFFFF,0xFFED2939)
                "EE"->listOf(0xFF4891D9,0xFF111111,0xFFFFFFFF)
                else->emptyList()
            }
            if(stripes.isNotEmpty()){
                val vertical=code.uppercase() in setOf("FR","IT","IE")
                stripes.forEachIndexed{index,color->
                    val start=index.toFloat()/stripes.size
                    drawRect(Color(color),if(vertical)Offset(size.width*start,0f)else Offset(0f,size.height*start),if(vertical)Size(size.width/stripes.size+1,size.height)else Size(size.width,size.height/stripes.size+1))
                }
            }else drawIntoCanvas{canvas->
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{textSize=size.height*1.13f;textAlign=Paint.Align.CENTER}
                canvas.nativeCanvas.drawText(VpnLocation.emoji(code),size.width/2,size.height/2-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2,paint)
            }
        }
    }
}
