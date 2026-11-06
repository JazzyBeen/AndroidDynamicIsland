package io.github.jazzybeen.androiddynamicisland.overlay

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.ui.IslandFont
import io.github.jazzybeen.androiddynamicisland.ui.Palette

/**
 * Island colours. Material 3 islands stay in dark tones even in light mode: the capsule grows
 * out of the camera hole, so a light fill would outline the cutout instead of hiding it.
 */
class IslandLook private constructor(
    val material:Boolean,val fill:Int,val text:Color,val secondary:Color,val accent:Color,val positive:Color,
    val negative:Color,val action:Color,val onAction:Color,val track:Color,val chip:Color,val font:FontFamily
){
    val fillColor:Color get()=Color(fill)
    companion object{
        private val classic=IslandLook(false,android.graphics.Color.BLACK,Color.White,Color(0xFF8E8E93),Color(0xFF64D2FF),Color(0xFF30D158),Color(0xFFFF6961),Color(0xFFFF3B30),Color.White,Color(0xFF38383A),Color(0xFF252528),IslandFont)
        private var cachedKey=-1;private var cached:IslandLook=classic
        @JvmStatic fun from(context:Context):IslandLook{
            val settings=IslandSettings(context);if(!settings.material())return classic
            val accent=settings.accent();if(accent==cachedKey)return cached
            val h=Palette.hue(accent)
            cached=IslandLook(true,Palette.tone(h,.22f,9),Palette.color(h,.1f,92),Palette.color(h,.12f,76),Palette.color(h,.75f,80),Palette.color(h,.75f,80),
                Color(0xFFF2B8B5),Palette.color(h,.3f,28),Palette.color(h,.6f,90),Palette.color(h,.15f,24),Palette.color(h,.2f,18),FontFamily.Default)
            cachedKey=accent;return cached
        }
    }
}
