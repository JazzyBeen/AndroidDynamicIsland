package io.github.jazzybeen.androiddynamicisland.core

import io.github.jazzybeen.androiddynamicisland.ui.Palette
import org.junit.Assert.*
import org.junit.Test

class PaletteTest {
    private fun luminance(argb:Int)=((argb shr 16 and 255)*299+(argb shr 8 and 255)*587+(argb and 255)*114)/1000
    @Test fun toneExtremesAreBlackAndWhite(){
        assertEquals(0xFF000000.toInt(),Palette.tone(218f,.8f,0))
        assertEquals(0xFFFFFFFF.toInt(),Palette.tone(218f,.8f,100))
    }
    @Test fun higherToneIsLighterForEverySeed(){
        for(i in Palette.names.indices){val h=Palette.hue(i);assertTrue(luminance(Palette.tone(h,.7f,80))>luminance(Palette.tone(h,.7f,40)))}
    }
    @Test fun outOfRangeAccentFallsBackToAValidSeed(){assertEquals(Palette.hue(5),Palette.hue(42),0f);assertEquals(Palette.hue(0),Palette.hue(-3),0f)}
    @Test fun sixAccentsAreOffered(){assertEquals(6,Palette.names.size)}
}
