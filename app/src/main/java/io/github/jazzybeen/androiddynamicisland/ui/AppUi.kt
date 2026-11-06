package io.github.jazzybeen.androiddynamicisland.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.view.RoundedCorner
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jazzybeen.androiddynamicisland.R
import io.github.jazzybeen.androiddynamicisland.core.IslandShape
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.AssistantController
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import java.util.function.Consumer

val IslandFont=FontFamily(Font(R.font.inter_regular),Font(R.font.inter_bold,FontWeight.Bold))
private val White=Color(0xFFF5F5F7)
private val Secondary=Color(0xFF98989F)
private val Card=Color(0xFF1C1C1E)
private val Blue=Color(0xFF0A84FF)
private val Green=Color(0xFF30D158)

object AppUi {
    @JvmStatic fun create(activity:Activity,settings:IslandSettings,action:Consumer<String>):View=ComposeView(activity).apply{
        val revision=mutableIntStateOf(0);tag=revision
        setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Blue,surface=Card,background=Color.Black,onSurface=White)){
            Screen(settings,action,revision.intValue)
        }}
    }
    @JvmStatic fun refresh(view:View){(view.tag as? MutableIntState)?.let{it.intValue++}}
}

@Composable private fun Screen(settings:IslandSettings,action:Consumer<String>,revision:Int){
    val context=LocalContext.current
    val status by remember{VpnCoordinator.get(context)}.state.collectAsState()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    BackHandler(tab!=0){tab=0}
    val enabled=remember(revision){settings.enabled()}
    val embedded=remember(revision){settings.embeddedVpn()}
    val client=VpnClientBridge.iconPackage(context)
    Scaffold(containerColor=Color.Black,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        Column(Modifier.background(Color.Black).navigationBarsPadding()){
            HorizontalDivider(color=Color.White.copy(alpha=.1f),thickness=.5.dp)
            Row(Modifier.fillMaxWidth().height(65.dp)){
                listOf("Остров","VPN","Карты","События","Настройки").forEachIndexed{index,label->
                    Column(Modifier.weight(1f).fillMaxHeight().clickable{tab=index}.padding(top=10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        AppGlyph(index,if(tab==index)Blue else Secondary)
                        Spacer(Modifier.height(4.dp));Label(label,10,if(tab==index)Blue else Secondary)
                    }
                }
            }
        }
    }){padding->
        Crossfade(tab,modifier=Modifier.fillMaxSize().padding(padding).statusBarsPadding(),label="navigation"){page->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=22.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                when(page){
                    0->{
                        PageHeader("Dynamic Island","Важное — в одном месте")
                        Box(Modifier.fillMaxWidth().height(142.dp).background(Card,RoundedCornerShape(28.dp)).padding(12.dp),contentAlignment=Alignment.Center){
                            IslandPreview(Modifier.fillMaxWidth().height(104.dp),client,status.connected)
                        }
                        Group{
                            SettingRow("Остров в фоне",if(enabled)"Готов к работе" else "Выключен",icon=0){
                                Switch(enabled,{action.accept("enabled")},colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))
                            }
                        }
                        Group{
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                                VpnIcon(38,client);Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)){Label(VpnClientBridge.label(context),16,bold=true);Spacer(Modifier.height(3.dp));Label(status.message,12,if(status.connected)Green else Secondary)}
                            }
                            HorizontalDivider(Modifier.padding(start=18.dp),color=Color.White.copy(alpha=.08f))
                            TextRow("Настроить VPN",Blue){tab=1}
                        }
                        PrimaryButton("Показать остров",primary=false){action.accept("preview")}
                        Caption("Удержи питание. Остров покажет подключение или отключение выбранного VPN.")
                    }
                    1->{
                        PageHeader("VPN","Твоё подключение под контролем")
                        Group{
                            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){VpnIcon(46,client);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Label(VpnClientBridge.label(context),18,bold=true);Label(if(embedded)"Встроенный VPN · без открытия клиента" else if(client.isEmpty())"Добавь подключение" else "Внешний VPN-клиент",12,Secondary)}}
                            HorizontalDivider(Modifier.padding(start=20.dp),color=Color.White.copy(alpha=.08f))
                            TextRow(if(embedded)"Изменить профиль" else "Настроить встроенный VPN",Blue){action.accept("embeddedSetup")}
                            TextRow("Выбрать внешнее приложение",Secondary){action.accept("pickClient")}
                        }
                        Group{
                            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(9.dp).background(if(status.connected)Green else Secondary,RoundedCornerShape(50)))
                                Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Label(if(status.connected)"VPN подключён" else "VPN отключён",17,bold=true);Spacer(Modifier.height(5.dp));Label(status.message,13,Secondary)}
                                if(status.busy)CircularProgressIndicator(Modifier.size(20.dp),color=White,strokeWidth=2.dp)
                            }
                        }
                        PrimaryButton(if(status.busy)"Выполняется…" else if(status.connected)"Отключить VPN" else "Подключить VPN",enabled=!status.busy){action.accept("toggleVpn")}
                        if(!embedded)PrimaryButton("Открыть VPN-приложение",primary=false){action.accept("client")}
                        Caption(if(embedded)"Профиль хранится зашифрованным на этом устройстве. После первого разрешения VPN включается без открытия окон." else "Внешний клиент может открывать своё окно. Для полностью фонового подключения настрой встроенный VPN.")
                        if(embedded)PrimaryButton("Удалить сохранённый профиль",primary=false){action.accept("forgetProfile")}
                        Section("ДОСТУПЫ"){
                            ActionRow("Специальные возможности",if(IslandAccessibilityService.current()!=null)"Подключены" else "Нужно включить"){action.accept("accessibility")}
                            if(!embedded)ActionRow("Уведомления VPN",if(Permissions.listener(context))"Разрешены" else "Настроить"){action.accept("listener")}
                        }
                    }
                    2->NavigationPage(action,revision)
                    3->ActivitiesPage(action,revision)
                    else->{
                        PageHeader("Настройки","Всё на своём месте")
                        Section("УПРАВЛЕНИЕ"){
                            ActionRow("Кнопка питания",if(AssistantController.isSelected(context))"Через ассистента" else "Меню выключения"){action.accept("assistant")}
                            ActionRow("Быстрые настройки","Добавить плитку"){action.accept("tile")}
                        }
                        Caption("Чтобы вызывать остров без меню выключения, выбери его системным ассистентом и включи вызов ассистента удержанием питания в настройках телефона.")
                        Section("ТОЧКА ПОЯВЛЕНИЯ"){
                            var automatic by remember(revision){mutableStateOf(settings.autoOrigin())}
                            SettingRow("Определять камеру автоматически",null){Switch(automatic,{automatic=it;settings.autoOrigin(it);action.accept("originGuide")},colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))}
                            if(!automatic){
                                SnapSlider("По горизонтали",settings.originX(),0,100,"%"){settings.originX(it);action.accept("originGuide")}
                                SnapSlider("От верхнего края",settings.originY(),0,100,"dp"){settings.originY(it);action.accept("originGuide")}
                            }
                            TextRow("Показать маркер",Blue){action.accept("originGuide")}
                            TextRow("Проверить появление",Blue){action.accept("originPreview")}
                        }
                        Caption("Совмести синий маркер с центром физической камеры. Остров закреплён на вырезе: при раскрытии и сжатии его верхняя граница остаётся на месте.")
                        Section("КОРОТКИЕ ОПОВЕЩЕНИЯ"){
                            var connectionAlerts by remember(revision){mutableStateOf(settings.connectionAlerts())}
                            var idleAlerts by remember(revision){mutableStateOf(settings.idleAlerts())}
                            SettingRow("Изменения соединения",null){Switch(connectionAlerts,{connectionAlerts=it;settings.connectionAlerts(it)},colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))}
                            SettingRow("Нет передачи данных","После 20 секунд без трафика"){Switch(idleAlerts,{idleAlerts=it;settings.idleAlerts(it)},colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))}
                            TextRow("Проверить оповещение",Blue){action.accept("eventPreview")}
                        }
                        Caption("Маленькие оповещения видны около двух секунд. Нулевой трафик — информация о простое; повторные сообщения ограничены, чтобы не отвлекать.")
                        Section("ОФОРМЛЕНИЕ"){
                            var corners by remember(revision){mutableStateOf(settings.autoCorners())}
                            SettingRow("Углы как у экрана",null){Switch(corners,{corners=it;settings.autoCorners(it)},colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))}
                            if(!corners)SnapSlider("Скругление",settings.corners(),12,56,"dp"){settings.corners(it)}
                            SnapSlider("Отступы от краёв",settings.margin(),4,24,"dp"){settings.margin(it)}
                            SnapSlider("Размытие движения",settings.blur(),0,48,"мс"){settings.blur(it)}
                        }
                        Section("РАБОТА В ФОНЕ"){
                            ActionRow("Уведомления",if(Permissions.notifications(context))"Разрешены" else "Настроить"){action.accept("notifications")}
                            ActionRow("Специальные возможности",if(IslandAccessibilityService.current()!=null)"Работают" else "Включить"){action.accept("accessibility")}
                            ActionRow("Оптимизация батареи",if(Permissions.battery(context))"Без ограничений" else "Настроить"){action.accept("battery")}
                            ActionRow("Автозапуск",null){action.accept("autostart")}
                        }
                        Caption("Разреши фоновую работу острова и VPN-клиента. На некоторых телефонах нужно также включить автозапуск.")
                        Section("ПРИЛОЖЕНИЕ"){
                            ActionRow("Настроить заново",null){action.accept("setup")}
                            ActionRow("Информация",null){action.accept("details")}
                        }
                        Caption("Версия 0.3.0\nБез снимков экрана и микрофона. Интернет используется для VPN и карты, когда она открыта.")
                    }
                }
            }
        }
    }
}

@Composable fun Label(text:String,size:Int=15,color:Color=White,bold:Boolean=false,modifier:Modifier=Modifier){
    Text(text,modifier,color,fontFamily=IslandFont,fontSize=size.sp,lineHeight=(size*1.35).sp,fontWeight=if(bold)FontWeight.Bold else FontWeight.Normal)
}
@Composable internal fun PageHeader(title:String,subtitle:String){Column{Label(title,32,bold=true);Spacer(Modifier.height(8.dp));Label(subtitle,14,Secondary)}}
@Composable internal fun Caption(text:String){Label(text,12,Secondary,modifier=Modifier.padding(horizontal=3.dp))}
@Composable fun Group(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().background(Card,RoundedCornerShape(20.dp)),content=content)}
@Composable internal fun Section(title:String,content:@Composable ColumnScope.()->Unit){Column{Label(title,11,Secondary,modifier=Modifier.padding(start=14.dp,bottom=9.dp));Group(content)}}
@Composable internal fun SettingRow(title:String,detail:String?,icon:Int?=null,content:@Composable ()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=66.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
        if(icon!=null){AppGlyph(icon,White);Spacer(Modifier.width(12.dp))}
        Column(Modifier.weight(1f)){Label(title,15);if(detail!=null){Spacer(Modifier.height(3.dp));Label(detail,12,Secondary)}};content()
    }
}
@Composable private fun TextRow(title:String,color:Color=White,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Label(title,15,color,modifier=Modifier.weight(1f));Label("›",23,Secondary)}}
@Composable internal fun ActionRow(title:String,detail:String?,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).heightIn(min=57.dp).padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){Label(title,14,modifier=Modifier.weight(1f));if(detail!=null)Label(detail,11,Secondary,modifier=Modifier.padding(start=8.dp,end=8.dp));Label("›",20,Secondary)}}
@Composable fun PrimaryButton(text:String,primary:Boolean=true,enabled:Boolean=true,action:()->Unit){
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).98f else 1f,spring(stiffness=650f),label="press")
    Box(Modifier.fillMaxWidth().height(52.dp).graphicsLayer{scaleX=scale;scaleY=scale;alpha=if(enabled)1f else .45f}.background(if(primary)White else Card,RoundedCornerShape(16.dp)).clickable(interactionSource=interactions,indication=null,enabled=enabled,onClick=action),contentAlignment=Alignment.Center){Label(text,15,if(primary)Color.Black else White,true)}
}
@Composable private fun SnapSlider(title:String,initial:Int,min:Int,max:Int,unit:String,onValue:(Int)->Unit){
    var value by remember{mutableFloatStateOf(initial.toFloat())}
    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)){Row{Label(title,13,modifier=Modifier.weight(1f));Label("${value.toInt()} $unit",12,Secondary)};Slider(value,{value=kotlin.math.round(it);onValue(value.toInt())},valueRange=min.toFloat()..max.toFloat(),steps=max-min-1,colors=SliderDefaults.colors(thumbColor=White,activeTrackColor=White,inactiveTrackColor=Color.White.copy(alpha=.12f)))}
}
@Composable fun VpnIcon(edge:Int=36,packageName:String?=null){
    val context=LocalContext.current;val selected=packageName?:VpnClientBridge.iconPackage(context)
    val pixelEdge=kotlin.math.round(edge*context.resources.displayMetrics.density).toInt().coerceIn(96,256)
    val bitmap=remember(selected,pixelEdge){try{val d=context.packageManager.getApplicationIcon(selected);Bitmap.createBitmap(pixelEdge,pixelEdge,Bitmap.Config.ARGB_8888).also{d.setBounds(0,0,pixelEdge,pixelEdge);d.draw(NativeCanvas(it))}.asImageBitmap()}catch(_:Exception){null}}
    if(bitmap!=null)Image(bitmap,null,Modifier.size(edge.dp))else Canvas(Modifier.size(edge.dp)){
        drawArc(White, -45f,270f,false,Offset(size.width*.15f,size.height*.15f),androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.7f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        drawLine(White,Offset(size.width*.5f,size.height*.05f),Offset(size.width*.5f,size.height*.5f),2.dp.toPx(),StrokeCap.Round)
    }
}
@Composable fun IslandPreview(modifier:Modifier=Modifier,client:String?=null,connected:Boolean=false){
    val context=LocalContext.current;val settings=remember{IslandSettings(context)}
    val insets=(context as? Activity)?.window?.decorView?.rootWindowInsets;val density=context.resources.displayMetrics.density
    val s31=android.os.Build.VERSION.SDK_INT>=31
    val radius=IslandShape.radius((if(s31)insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?:0 else 0)/density,(if(s31)insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?:0 else 0)/density,settings.autoCorners(),settings.corners().toFloat())
    Box(modifier.background(Color.Black,RoundedCornerShape(radius.dp))){
        Row(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            CountryFlag(settings.vpnCountryCode(),46);Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){Label(settings.vpnCountry(),13,Secondary);Label(if(connected)settings.vpnCity()else"Готов к работе",19,bold=true)}
            Box(Modifier.size(44.dp).background(Color(0xFFFF3B30),RoundedCornerShape(50)),contentAlignment=Alignment.Center){Label("×",32)}
        }
    }
}
@Composable private fun AppGlyph(kind:Int,color:Color){Canvas(Modifier.size(24.dp)){
    val stroke=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
    when(kind){
        0->drawRoundRect(color,Offset(size.width*.08f,size.height*.28f),androidx.compose.ui.geometry.Size(size.width*.84f,size.height*.44f),CornerRadius(size.height*.22f),style=stroke)
        1->{val p=Path().apply{moveTo(size.width*.5f,size.height*.08f);lineTo(size.width*.83f,size.height*.22f);lineTo(size.width*.8f,size.height*.57f);quadraticTo(size.width*.75f,size.height*.78f,size.width*.5f,size.height*.92f);quadraticTo(size.width*.25f,size.height*.78f,size.width*.2f,size.height*.57f);lineTo(size.width*.17f,size.height*.22f);close()};drawPath(p,color,style=stroke)}
        2->{val p=Path().apply{moveTo(size.width*.15f,size.height*.48f);lineTo(size.width*.85f,size.height*.12f);lineTo(size.width*.53f,size.height*.87f);lineTo(size.width*.45f,size.height*.55f);close()};drawPath(p,color,style=stroke)}
        else->{for(i in 0..2){val y=size.height*(.22f+i*.28f);drawLine(color,Offset(size.width*.12f,y),Offset(size.width*.88f,y),1.8.dp.toPx(),StrokeCap.Round);drawCircle(Card,3.4.dp.toPx(),Offset(size.width*(if(i==1).35f else .65f),y));drawCircle(color,3.4.dp.toPx(),Offset(size.width*(if(i==1).35f else .65f),y),style=stroke)}}
    }
}}
