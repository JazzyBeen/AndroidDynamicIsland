package io.github.jazzybeen.androiddynamicisland.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.github.jazzybeen.androiddynamicisland.data.VpnLocation

/** One flag renderer for Compose capsules and native satellite activities. Called on the UI thread. */
object CountryFlagPainter{
    private fun colors(vararg values:Long)=values.map{it.toInt()}.toIntArray()
    private val palettes=mapOf(
        "NL" to colors(0xFFAE1C28,0xFFFFFFFF,0xFF21468B),"RU" to colors(0xFFFFFFFF,0xFF0039A6,0xFFD52B1E),
        "DE" to colors(0xFF111111,0xFFDD0000,0xFFFFCE00),"FR" to colors(0xFF002654,0xFFFFFFFF,0xFFED2939),
        "IT" to colors(0xFF009246,0xFFFFFFFF,0xFFCE2B37),"IE" to colors(0xFF169B62,0xFFFFFFFF,0xFFFF883E),
        "UA" to colors(0xFF0057B7,0xFFFFD700),"PL" to colors(0xFFFFFFFF,0xFFDC143C),
        "AT" to colors(0xFFED2939,0xFFFFFFFF,0xFFED2939),"EE" to colors(0xFF4891D9,0xFF111111,0xFFFFFFFF)
    )
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val clip=Path()
    fun draw(canvas:Canvas,code:String,left:Float,top:Float,edge:Float){
        val saved=canvas.save();clip.reset();clip.addCircle(left+edge/2,top+edge/2,edge/2,Path.Direction.CW);canvas.clipPath(clip)
        val upper=code.uppercase();val stripes=palettes[upper]
        if(stripes!=null){val vertical=upper in setOf("FR","IT","IE");stripes.forEachIndexed{i,color->paint.color=color;val a=edge*i/stripes.size;val b=edge*(i+1)/stripes.size+1
            if(vertical)canvas.drawRect(left+a,top,left+b,top+edge,paint)else canvas.drawRect(left,top+a,left+edge,top+b,paint)
        }}else{paint.color=android.graphics.Color.WHITE;paint.textSize=edge*1.13f;paint.textAlign=Paint.Align.CENTER;canvas.drawText(VpnLocation.emoji(code),left+edge/2,top+edge/2-(paint.ascent()+paint.descent())/2,paint)}
        canvas.restoreToCount(saved)
    }
}
