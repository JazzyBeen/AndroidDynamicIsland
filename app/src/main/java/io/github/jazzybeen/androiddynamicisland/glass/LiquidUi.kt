package io.github.jazzybeen.androiddynamicisland.glass

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.view.View
import android.view.RoundedCorner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.jazzybeen.androiddynamicisland.R
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.core.IslandShape
import io.github.jazzybeen.androiddynamicisland.ui.Permissions
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import java.util.function.Consumer

private val Ink = Color(0xFFF5F5F7)
private val Muted = Color(0xFF99999F)
val IslandFont = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold))

fun Modifier.liquidSurface(backdrop: Backdrop, radius: Float = 28f, wash: Float = .06f, island:Boolean=false): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(radius.dp) },
    effects = { vibrancy(); blur(1.5.dp.toPx()); lens(26.dp.toPx(), 38.dp.toPx(), depthEffect=true,chromaticAberration=false); opacity(if(island).96f else .8f) },
    highlight = { Highlight(width = 1.4.dp, blurRadius=.7.dp, alpha=.95f,style=HighlightStyle.Default(intensity=.85f,falloff=1.6f)) },
    innerShadow = { InnerShadow(radius=8.dp,offset=DpOffset(0.dp,3.dp),color=Color.Black.copy(alpha=.28f)) },
    onDrawSurface = {
        drawRect(Color.Black.copy(alpha=if(island).04f else .1f))
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha=wash+.09f),Color.White.copy(alpha=.015f),Color.Black.copy(alpha=.12f))))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha=.13f),Color.Transparent),center=Offset(size.width*.12f,-size.height*.45f),radius=size.width*.65f))
        val inset=2.5.dp.toPx()
        val innerRadius=(radius.dp.toPx()-inset).coerceAtLeast(0f)
        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha=.23f),Color.Transparent,Color.White.copy(alpha=.08f))),topLeft=Offset(inset,inset),size=Size((size.width-2*inset).coerceAtLeast(0f),(size.height-2*inset).coerceAtLeast(0f)),cornerRadius=CornerRadius(innerRadius),style=Stroke(3.dp.toPx()))
        if(island)drawRect(Brush.verticalGradient(0f to Color.Black,.24f to Color.Black,.6f to Color.Black.copy(alpha=.48f),1f to Color.Transparent))
    }
)

object LiquidUi {
    @JvmStatic fun create(activity: Activity, settings: IslandSettings, action: Consumer<String>): View = ComposeView(activity).apply {
        val revision=mutableIntStateOf(0)
        tag=revision
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFD1D1D6),secondary=Color(0xFFC7C7CC),surface = Color(0xFF111113), onSurface = Ink)) { Screen(settings, action,revision.intValue) } }
    }
    @JvmStatic fun refresh(view:View){(view.tag as? MutableIntState)?.let{it.intValue++}}
}

@Composable private fun Screen(settings: IslandSettings, action: Consumer<String>, revision:Int) {
    val context = LocalContext.current
    val clientPackage=VpnClientBridge.selectedPackage(context)
    val backdrop = rememberLayerBackdrop()
    var enabled by remember(revision) { mutableStateOf(settings.enabled()) }
    var power by remember(revision) { mutableStateOf(settings.powerShortcut()) }
    var autoCorners by remember(revision) { mutableStateOf(settings.autoCorners()) }
    var manualCorners by remember(revision) { mutableIntStateOf(settings.corners()) }
    val liveBackdrop=remember(revision){settings.liveBackdrop()}
    val insets=(context as? Activity)?.window?.decorView?.rootWindowInsets
    val density=context.resources.displayMetrics.density
    val radius=IslandShape.radius(
        (insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?:0)/density,
        (insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?:0)/density,
        autoCorners,manualCorners.toFloat())
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            drawRect(Color.Black)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("DYNAMIC ISLAND", 11, Muted, true, Modifier.weight(1f))
                Label("ANDROID", 10, Color(0xFFD1D1D6), true)
            }
            Spacer(Modifier.height(8.dp))
            Text("Dynamic Island", color = Ink, fontFamily = IslandFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp)
            Label("Управление VPN одним жестом.", 14, Muted)
            Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.Center) {
                PreviewPill(Modifier.fillMaxWidth().height(112.dp),radius,clientPackage)
            }
            GlassCard(backdrop) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VpnIcon(40,clientPackage)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Label("VPN",18,Ink,true); Spacer(Modifier.height(4.dp)); Label(VpnClientBridge.label(context),12,Muted) }
                    Box(Modifier.size(8.dp).background(if (enabled) Color(0xFF82DFB0) else Muted, RoundedCornerShape(50)))
                }
                Spacer(Modifier.height(20.dp))
                GlassButton("Выбрать VPN-приложение",backdrop) { action.accept("pickClient") }
                Spacer(Modifier.height(10.dp))
                GlassButton(if(enabled) "Переключить VPN" else "Настроить остров",backdrop, true) { action.accept(if(enabled) "toggleVpn" else "setup") }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { GlassButton("Анимация",backdrop) { action.accept("preview") } }
                    Box(Modifier.weight(1f)) { GlassButton(if(enabled) "Остановить" else "Включить",backdrop) { action.accept("enabled"); enabled = settings.enabled() } }
                }
            }
            Label("Долгое нажатие питания → меню выключения → VPN-клиент. Если запускается ассистент, измени назначение кнопки в настройках телефона.",12,Muted, modifier = Modifier.padding(horizontal = 4.dp))
            SectionTitle("Необходимые доступы")
            GlassCard(backdrop) {
                PermissionItem("Уведомления", "Тихая служба в фоне", Permissions.notifications(context)) { action.accept("setup") }
                PermissionItem("Доступ к уведомлениям", "Только выбранный VPN-клиент", Permissions.listener(context)) { action.accept("listener") }
                PermissionItem("Специальные возможности", "Меню питания и показ острова", Permissions.accessibility(context)) { action.accept("accessibility") }
                PermissionItem("Работа без ограничений", "Исключение оптимизации батареи", Permissions.battery(context)) { action.accept("battery") }
                PermissionItem("VPN-приложение", "Выбери и настрой своё подключение", VpnClientBridge.installed(context)) { action.accept("pickClient") }
            }
            SectionTitle("Ощущается как твой")
            GlassCard(backdrop) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Меню питания",14,Ink, modifier = Modifier.weight(1f))
                    Switch(power,{power=it;settings.powerShortcut(it)},colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF34C759),checkedThumbColor=Color.White))
                }
                GlassSlider("Размытие в движении",settings.blur(),0,48,"мс") { settings.blur(it) }
                GlassSlider("Отступы от краёв",settings.margin(),4,24,"dp") { settings.margin(it) }
                Row(verticalAlignment=Alignment.CenterVertically){Label("Углы как у экрана",14,Ink,modifier=Modifier.weight(1f));Switch(autoCorners,{autoCorners=it;settings.autoCorners(it)},colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF34C759),checkedThumbColor=Color.White))}
                if(!autoCorners)GlassSlider("Скругление",manualCorners,12,56,"dp"){manualCorners=it;settings.corners(it)}
                Row(verticalAlignment=Alignment.CenterVertically){Label("Живой фон под островом",14,Ink,modifier=Modifier.weight(1f));Switch(liveBackdrop,{action.accept("liveBackdrop")},colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF34C759),checkedThumbColor=Color.White))}
                Label("Преломляет фон приложения под островом. Локальный кадр используется только в памяти, пока остров виден. Защищённые экраны недоступны.",11,Muted)
                Spacer(Modifier.height(8.dp)); GlassButton("Добавить плитку в шторку",backdrop) { action.accept("tile") }
            }
            SectionTitle("Фон под контролем")
            GlassCard(backdrop) {
                Label("Работа в фоне",17,Ink,true)
                Spacer(Modifier.height(8.dp))
                Label("Разреши фоновую работу острова и VPN-клиента. Некоторые прошивки требуют автозапуска и закрепления в недавних. После принудительной остановки приложение нужно открыть снова.",12,Muted)
                Spacer(Modifier.height(14.dp)); GlassButton("Автозапуск",backdrop) { action.accept("autostart") }
                Spacer(Modifier.height(10.dp)); GlassButton("Информация о приложении",backdrop) { action.accept("details") }
            }
            Label("Если Android блокирует службу: информация о приложении → ⋮ → «Разрешить ограниченные настройки». Это действие выполняется вручную.",12,Muted,modifier=Modifier.padding(horizontal=4.dp))
            GlassCard(backdrop) {
                Label("Локально. Без VPN-ключа.",17,Ink,true)
                Spacer(Modifier.height(8.dp)); Label("Нет разрешения на интернет. Команды обрабатывают только системное меню и выбранный VPN-клиент. Подключение может ненадолго открыть VPN-клиент. Остров не скрывает обязательное уведомление фоновой службы.\n\nЖивое преломление включается отдельно. Изображение фона не записывается в файлы и не отправляется; после скрытия острова кадр освобождается.",12,Muted)
            }
            Label("0.1.0  ·  Dynamic Island",11,Muted,modifier=Modifier.padding(vertical=16.dp))
        }
    }
}

@Composable private fun GlassCard(backdrop: Backdrop, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().liquidSurface(backdrop).padding(20.dp), content = content)
}
@Composable private fun SectionTitle(text:String) { Label(text,12,Muted,true,Modifier.padding(start=4.dp,top=14.dp)) }
@Composable fun Label(text:String,size:Int=14,color:Color=Ink,bold:Boolean=false,modifier:Modifier=Modifier,shadow:Boolean=false) {
    Text(text,modifier,color,fontFamily=IslandFont,fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,fontSize=size.sp,lineHeight=(size*1.45).sp,style=TextStyle(shadow=if(shadow)Shadow(Color.Black.copy(alpha=.8f),Offset(0f,1.5f),4f)else null))
}
@Composable private fun GlassButton(text:String,backdrop:Backdrop,primary:Boolean=false,onClick:()->Unit) {
    val interaction=remember{MutableInteractionSource()}; val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).97f else 1f,spring(dampingRatio=.7f,stiffness=650f),label="button")
    Box(Modifier.fillMaxWidth().height(49.dp).graphicsLayer{scaleX=scale;scaleY=scale}.liquidSurface(backdrop,26f,if(primary).17f else .055f).clickable(interactionSource=interaction,indication=null,onClick=onClick),contentAlignment=Alignment.Center) { Label(text,13,Ink,true) }
}
@Composable private fun PermissionItem(title:String,detail:String,enabled:Boolean,click:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Label(title,13,Ink);Label(detail,11,Muted)}
        Label(if(enabled) "✓" else "↗",19,if(enabled)Color(0xFF8EE2B6)else Color(0xFFD1D1D6))
    }
}
@Composable private fun GlassSlider(title:String,initial:Int,low:Int,high:Int,unit:String,set:(Int)->Unit) {
    var value by remember{mutableFloatStateOf(initial.toFloat())}
    Spacer(Modifier.height(15.dp)); Row {Label(title,12,Muted,modifier=Modifier.weight(1f));Label("${value.toInt()} $unit",12,Ink,true)}
    Slider(value,{value=kotlin.math.round(it);set(value.toInt())},valueRange=low.toFloat()..high.toFloat(),steps=high-low-1,colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color(0xFFD1D1D6),inactiveTrackColor=Color.White.copy(alpha=.1f)))
}
@Composable fun VpnIcon(edge:Int=36,packageName:String?=null) {
    val context=LocalContext.current
    val selected=packageName?:VpnClientBridge.selectedPackage(context)
    val image=remember(selected) { try { val d=context.packageManager.getApplicationIcon(selected);Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also{d.setBounds(0,0,96,96);d.draw(AndroidCanvas(it))}.asImageBitmap() } catch(_:Exception){null} }
    if(image!=null) Image(image,null,Modifier.size(edge.dp)) else Canvas(Modifier.size(edge.dp)){val bounds=this.size;drawCircle(Color.White.copy(alpha=.9f),radius=bounds.minDimension*.37f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()));drawLine(Color.White,Offset(bounds.width*.5f,bounds.height*.12f),Offset(bounds.width*.5f,bounds.height*.5f),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}
}
@Composable fun PreviewPill(modifier:Modifier=Modifier,radius:Float=32f,clientPackage:String?=null) {
    val backdrop=rememberCanvasBackdrop {
        drawRect(Brush.linearGradient(listOf(Color(0xFF242426),Color(0xFF171719),Color(0xFF0B0B0C))))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha=.3f),Color.Transparent),center=Offset(size.width*.25f,-20f),radius=size.width*.6f),size.width*.6f,Offset(size.width*.25f,-20f))
    }
    Box(modifier.liquidSurface(backdrop,radius,.045f,island=true)) {
        // The empty upper centre is reserved for the real front camera in the overlay.
        Row(Modifier.align(Alignment.BottomStart).padding(start=20.dp,end=18.dp,bottom=18.dp),verticalAlignment=Alignment.CenterVertically){VpnIcon(35,clientPackage);Spacer(Modifier.width(13.dp));Column{Label("VPN",15,Ink,true);Label("Готов к подключению",11,Color(0xFFBFBFC4))}}
    }
}
