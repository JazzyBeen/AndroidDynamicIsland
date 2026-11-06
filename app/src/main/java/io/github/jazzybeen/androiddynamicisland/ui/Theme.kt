package io.github.jazzybeen.androiddynamicisland.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/** Six seed colours expanded into Material 3 tonal roles. Tones follow the M3 role table (80/20/30/90 dark, 40/100/90/10 light). */
object Palette{
    val names=listOf("Синий","Фиолетовый","Розовый","Оранжевый","Зелёный","Бирюзовый")
    private val hues=floatArrayOf(218f,268f,330f,24f,140f,182f)
    fun hue(index:Int)=hues[index.coerceIn(0,hues.size-1)]
    fun tone(hue:Float,chroma:Float,tone:Int):Int{
        val l=tone/100f;val c=(1-abs(2*l-1))*chroma;val h=((hue%360)+360)%360/60f;val x=c*(1-abs(h%2-1));val m=l-c/2
        val (r,g,b)=when(h.toInt()){0->Triple(c,x,0f);1->Triple(x,c,0f);2->Triple(0f,c,x);3->Triple(0f,x,c);4->Triple(x,0f,c);else->Triple(c,0f,x)}
        fun ch(v:Float)=((v+m)*255).roundToInt().coerceIn(0,255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
    fun color(hue:Float,chroma:Float,tone:Int)=Color(tone(hue,chroma,tone))
    fun swatch(index:Int)=color(hue(index),.62f,52)
    fun scheme(index:Int,dark:Boolean):ColorScheme{
        val h=hue(index);val t=h+60f
        fun p(c:Float,v:Int)=color(h,c,v)
        fun q(c:Float,v:Int)=color(t,c,v)
        return if(dark)darkColorScheme(
            primary=p(.75f,80),onPrimary=p(.6f,20),primaryContainer=p(.5f,30),onPrimaryContainer=p(.8f,90),inversePrimary=p(.6f,40),
            secondary=p(.25f,80),onSecondary=p(.25f,20),secondaryContainer=p(.2f,30),onSecondaryContainer=p(.3f,90),
            tertiary=q(.45f,80),onTertiary=q(.4f,20),tertiaryContainer=q(.35f,30),onTertiaryContainer=q(.5f,90),
            background=p(.12f,6),onBackground=p(.08f,90),surface=p(.12f,6),onSurface=p(.08f,90),surfaceVariant=p(.12f,30),onSurfaceVariant=p(.12f,80),
            surfaceTint=p(.75f,80),inverseSurface=p(.08f,90),inverseOnSurface=p(.1f,20),
            error=Color(0xFFF2B8B5),onError=Color(0xFF601410),errorContainer=Color(0xFF8C1D18),onErrorContainer=Color(0xFFF9DEDC),
            outline=p(.08f,60),outlineVariant=p(.1f,30),scrim=Color.Black,
            surfaceBright=p(.1f,24),surfaceDim=p(.12f,6),surfaceContainerLowest=p(.12f,4),surfaceContainerLow=p(.12f,10),surfaceContainer=p(.12f,12),surfaceContainerHigh=p(.12f,17),surfaceContainerHighest=p(.12f,22)
        )else lightColorScheme(
            primary=p(.6f,40),onPrimary=Color.White,primaryContainer=p(.8f,90),onPrimaryContainer=p(.6f,12),inversePrimary=p(.7f,80),
            secondary=p(.2f,40),onSecondary=Color.White,secondaryContainer=p(.35f,90),onSecondaryContainer=p(.25f,12),
            tertiary=q(.4f,40),onTertiary=Color.White,tertiaryContainer=q(.6f,90),onTertiaryContainer=q(.5f,12),
            background=p(.3f,98),onBackground=p(.1f,10),surface=p(.3f,98),onSurface=p(.1f,10),surfaceVariant=p(.2f,90),onSurfaceVariant=p(.1f,30),
            surfaceTint=p(.6f,40),inverseSurface=p(.08f,20),inverseOnSurface=p(.2f,95),
            error=Color(0xFFB3261E),onError=Color.White,errorContainer=Color(0xFFF9DEDC),onErrorContainer=Color(0xFF410E0B),
            outline=p(.08f,50),outlineVariant=p(.12f,80),scrim=Color.Black,
            surfaceBright=p(.3f,98),surfaceDim=p(.12f,87),surfaceContainerLowest=Color.White,surfaceContainerLow=p(.25f,96),surfaceContainer=p(.2f,94),surfaceContainerHigh=p(.18f,92),surfaceContainerHighest=p(.15f,90)
        )
    }
}

/** Colour and shape tokens shared by both styles, so pages never branch on raw hex values. */
@Immutable class Look(
    val material:Boolean,val background:Color,val card:Color,val text:Color,val secondary:Color,
    val accent:Color,val onAccent:Color,val positive:Color,val danger:Color,val divider:Color,
    val tonal:Color,val onTonal:Color,val font:FontFamily
)
private val classicLook=Look(false,Color.Black,Color(0xFF1C1C1E),Color(0xFFF5F5F7),Color(0xFF98989F),Color(0xFF0A84FF),Color.White,Color(0xFF30D158),Color(0xFFFF6961),Color.White.copy(alpha=.08f),Color(0xFF1C1C1E),Color(0xFFF5F5F7),IslandFont)
private val classicScheme=darkColorScheme(primary=Color(0xFF0A84FF),onPrimary=Color.White,surface=Color(0xFF1C1C1E),background=Color.Black,onSurface=Color(0xFFF5F5F7),onSurfaceVariant=Color(0xFF98989F),surfaceContainerHigh=Color(0xFF1C1C1E),surfaceContainer=Color(0xFF1C1C1E))
val LocalLook=staticCompositionLocalOf{classicLook}

object AppStyle{
    fun material(context:Context)=IslandSettings(context).material()
    /** Whether the app window should draw dark system-bar icons. */
    fun lightBars(context:Context):Boolean{
        if(!material(context))return false
        return context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK!=android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}

@Composable fun AppTheme(revision:Int=0,content:@Composable ()->Unit){
    val context=LocalContext.current;val dark=isSystemInDarkTheme()
    val settings=remember{IslandSettings(context)}
    val material=remember(revision){settings.material()};val accent=remember(revision){settings.accent()}
    val scheme=remember(material,accent,dark){if(material)Palette.scheme(accent,dark)else classicScheme}
    val look=remember(scheme,material){if(!material)classicLook else Look(true,scheme.surface,scheme.surfaceContainer,scheme.onSurface,scheme.onSurfaceVariant,scheme.primary,scheme.onPrimary,scheme.primary,scheme.error,scheme.outlineVariant,scheme.secondaryContainer,scheme.onSecondaryContainer,FontFamily.Default)}
    MaterialTheme(colorScheme=scheme){CompositionLocalProvider(LocalLook provides look,content=content)}
}
