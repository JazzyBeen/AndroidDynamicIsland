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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import io.github.jazzybeen.androiddynamicisland.overlay.IslandLook
import io.github.jazzybeen.androiddynamicisland.system.AssistantController
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import java.util.function.Consumer

val IslandFont=FontFamily(Font(R.font.inter_regular),Font(R.font.inter_bold,FontWeight.Bold))
private val White:Color @Composable @ReadOnlyComposable get()=LocalLook.current.text
private val Secondary:Color @Composable @ReadOnlyComposable get()=LocalLook.current.secondary
private val Card:Color @Composable @ReadOnlyComposable get()=LocalLook.current.card
private val Blue:Color @Composable @ReadOnlyComposable get()=LocalLook.current.accent
private val Green:Color @Composable @ReadOnlyComposable get()=LocalLook.current.positive
private val Divider:Color @Composable @ReadOnlyComposable get()=LocalLook.current.divider
private val Material:Boolean @Composable @ReadOnlyComposable get()=LocalLook.current.material

object AppUi {
    @JvmStatic fun create(activity:Activity,settings:IslandSettings,action:Consumer<String>):View=ComposeView(activity).apply{
        val revision=mutableIntStateOf(0);tag=revision
        setContent{AppTheme(revision.intValue){Screen(settings,action,revision.intValue)}}
    }
    @JvmStatic fun refresh(view:View){(view.tag as? MutableIntState)?.let{it.intValue++}}
}

private val tabs=listOf("Остров","VPN","События","Настройки")

@Composable private fun Screen(settings:IslandSettings,action:Consumer<String>,revision:Int){
    val context=LocalContext.current
    val status by remember{VpnCoordinator.get(context)}.state.collectAsState()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    BackHandler(tab!=0){tab=0}
    val enabled=remember(revision){settings.enabled()}
    val embedded=remember(revision){settings.embeddedVpn()}
    val client=VpnClientBridge.iconPackage(context)
    val look=LocalLook.current
    Scaffold(containerColor=look.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
        if(look.material)NavigationBar(windowInsets=WindowInsets.navigationBars){
            tabs.forEachIndexed{index,label->NavigationBarItem(selected=tab==index,onClick={tab=index},icon={AppGlyph(index,LocalContentColor.current)},label={Text(label)})}
        }else Column(Modifier.background(look.background).navigationBarsPadding()){
            HorizontalDivider(color=Color.White.copy(alpha=.1f),thickness=.5.dp)
            Row(Modifier.fillMaxWidth().height(65.dp)){
                tabs.forEachIndexed{index,label->
                    Column(Modifier.weight(1f).fillMaxHeight().clickable{tab=index}.padding(top=10.dp),horizontalAlignment=Alignment.CenterHorizontally){
                        AppGlyph(index,if(tab==index)Blue else Secondary)
                        Spacer(Modifier.height(4.dp));Label(label,10,if(tab==index)Blue else Secondary)
                    }
                }
            }
        }
    }){padding->
        Crossfade(tab,modifier=Modifier.fillMaxSize().padding(padding).statusBarsPadding(),label="pages"){page->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=if(look.material)16.dp else 20.dp).padding(top=22.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(if(look.material)16.dp else 20.dp)){
                when(page){
                    0->{
                        PageHeader("Dynamic Island","Важное — в одном месте")
                        Box(Modifier.fillMaxWidth().height(142.dp).background(Card,RoundedCornerShape(28.dp)).padding(12.dp),contentAlignment=Alignment.Center){
                            IslandPreview(Modifier.fillMaxWidth().height(104.dp),client,status.connected,revision)
                        }
                        Group{
                            SettingRow("Остров в фоне",if(enabled)"Готов к работе" else "Выключен",icon=0){AppSwitch(enabled){action.accept("enabled")}}
                        }
                        Group{
                            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                                VpnIcon(38,client);Spacer(Modifier.width(13.dp))
                                Column(Modifier.weight(1f)){Label(VpnClientBridge.label(context),16,bold=true);Spacer(Modifier.height(3.dp));Label(status.message,12,if(status.connected)Green else Secondary)}
                            }
                            HorizontalDivider(Modifier.padding(start=18.dp),color=Divider)
                            TextRow("Настроить VPN",Blue){tab=1}
                        }
                        PrimaryButton("Показать остров",primary=false){action.accept("preview")}
                        Caption("Удержи питание. Остров покажет подключение или отключение выбранного VPN.")
                    }
                    1->{
                        PageHeader("VPN","Твоё подключение под контролем")
                        Group{
                            Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){VpnIcon(46,client);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Label(VpnClientBridge.label(context),18,bold=true);Label(if(embedded)"Встроенный VPN · без открытия клиента" else if(client.isEmpty())"Добавь подключение" else "Внешний VPN-клиент",12,Secondary)}}
                            HorizontalDivider(Modifier.padding(start=20.dp),color=Divider)
                            TextRow(if(embedded)"Изменить профиль" else "Настроить встроенный VPN",Blue){action.accept("embeddedSetup")}
                            TextRow("Выбрать внешнее приложение",Secondary){action.accept("pickClient")}
                        }
                        Group{
                            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(9.dp).background(if(status.connected)Green else Secondary,CircleShape))
                                Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Label(if(status.connected)"VPN подключён" else "VPN отключён",17,bold=true);Spacer(Modifier.height(5.dp));Label(status.message,13,Secondary)}
                                if(status.busy)CircularProgressIndicator(Modifier.size(20.dp),color=if(look.material)Blue else White,strokeWidth=2.dp)
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
                    2->ActivitiesPage(action,revision)
                    else->SettingsPage(settings,action,revision)
                }
            }
        }
    }
}

@Composable private fun ColumnScope.SettingsPage(settings:IslandSettings,action:Consumer<String>,revision:Int){
    val context=LocalContext.current
    PageHeader("Настройки","Всё на своём месте")
    Section("УПРАВЛЕНИЕ"){
        ActionRow("Кнопка питания",if(AssistantController.isSelected(context))"Через ассистента" else "Меню выключения"){action.accept("assistant")}
        ActionRow("Быстрые настройки","Добавить плитку"){action.accept("tile")}
    }
    Caption("Нажми «Кнопка питания» — там пошаговая инструкция для твоего телефона, как вызывать остров без меню выключения.")
    Section("ВНЕШНИЙ ВИД"){
        var material by remember(revision){mutableStateOf(settings.material())}
        var accent by remember(revision){mutableIntStateOf(settings.accent())}
        SettingRow("Material Design 3","Стиль Android, цветные темы и острова"){AppSwitch(material){material=it;settings.material(it);action.accept("theme")}}
        if(material){
            HorizontalDivider(Modifier.padding(horizontal=16.dp),color=Divider)
            Column(Modifier.padding(horizontal=16.dp,vertical=14.dp)){
                Row{Label("Цвет",14,modifier=Modifier.weight(1f));Label(Palette.names[accent],12,Secondary)}
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Palette.names.indices.forEach{index->
                        val selected=index==accent
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel=Palette.names[index]){accent=index;settings.accent(index);action.accept("theme")}.border(if(selected)3.dp else 0.dp,if(selected)White else Color.Transparent,CircleShape).padding(if(selected)6.dp else 2.dp).background(Palette.swatch(index),CircleShape),contentAlignment=Alignment.Center){
                            if(selected)Canvas(Modifier.size(16.dp)){drawLine(Color.White,Offset(size.width*.12f,size.height*.55f),Offset(size.width*.4f,size.height*.82f),2.dp.toPx(),StrokeCap.Round);drawLine(Color.White,Offset(size.width*.4f,size.height*.82f),Offset(size.width*.9f,size.height*.2f),2.dp.toPx(),StrokeCap.Round)}
                        }
                    }
                }
            }
        }
    }
    Caption("Material 3 перекрашивает приложение и острова в выбранный цвет и следует светлой или тёмной теме телефона. Острова остаются тёмными, чтобы скрывать камеру; анимация и размытие не меняются.")
    Section("ТОЧКА ПОЯВЛЕНИЯ"){
        var automatic by remember(revision){mutableStateOf(settings.autoOrigin())}
        SettingRow("Определять камеру автоматически",null){AppSwitch(automatic){automatic=it;settings.autoOrigin(it);action.accept("originGuide")}}
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
        SettingRow("Изменения соединения",null){AppSwitch(connectionAlerts){connectionAlerts=it;settings.connectionAlerts(it)}}
        SettingRow("Нет передачи данных","После 20 секунд без трафика"){AppSwitch(idleAlerts){idleAlerts=it;settings.idleAlerts(it)}}
        TextRow("Проверить оповещение",Blue){action.accept("eventPreview")}
    }
    Caption("Маленькие оповещения видны около двух секунд. Нулевой трафик — информация о простое; повторные сообщения ограничены, чтобы не отвлекать.")
    Section("ФОРМА ОСТРОВА"){
        var corners by remember(revision){mutableStateOf(settings.autoCorners())}
        SettingRow("Углы как у экрана",null){AppSwitch(corners){corners=it;settings.autoCorners(it)}}
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
    Caption("Версия 0.4.0\nБез снимков экрана и микрофона. Интернет используется только для VPN.")
}

@Composable fun Label(text:String,size:Int=15,color:Color=White,bold:Boolean=false,modifier:Modifier=Modifier){
    val look=LocalLook.current
    Text(text,modifier,color,fontFamily=look.font,fontSize=size.sp,lineHeight=(size*1.35).sp,fontWeight=if(!bold)FontWeight.Normal else if(look.material)FontWeight.Medium else FontWeight.Bold)
}
@Composable internal fun PageHeader(title:String,subtitle:String){Column{Label(title,32,bold=!Material);Spacer(Modifier.height(8.dp));Label(subtitle,14,Secondary)}}
@Composable internal fun Caption(text:String){Label(text,12,Secondary,modifier=Modifier.padding(horizontal=if(Material)16.dp else 3.dp))}
@Composable fun Group(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if(Material)24.dp else 20.dp)).background(Card),content=content)}
@Composable internal fun Section(title:String,content:@Composable ColumnScope.()->Unit){
    Column{
        if(Material)Label(title.lowercase().replaceFirstChar{it.uppercase()},14,Blue,bold=true,modifier=Modifier.padding(start=16.dp,bottom=8.dp))
        else Label(title,11,Secondary,modifier=Modifier.padding(start=14.dp,bottom=9.dp))
        Group(content)
    }
}
@Composable internal fun SettingRow(title:String,detail:String?,icon:Int?=null,content:@Composable ()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=66.dp).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
        if(icon!=null){AppGlyph(icon,White);Spacer(Modifier.width(12.dp))}
        Column(Modifier.weight(1f)){Label(title,15);if(detail!=null){Spacer(Modifier.height(3.dp));Label(detail,12,Secondary)}};content()
    }
}
@Composable internal fun AppSwitch(checked:Boolean,enabled:Boolean=true,onChange:(Boolean)->Unit){
    if(Material)Switch(checked,onChange,enabled=enabled)
    else Switch(checked,onChange,enabled=enabled,colors=SwitchDefaults.colors(checkedTrackColor=Green,checkedThumbColor=Color.White))
}
@Composable private fun TextRow(title:String,color:Color=White,action:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Label(title,15,color,modifier=Modifier.weight(1f));if(!Material)Label("›",23,Secondary)}}
@Composable internal fun ActionRow(title:String,detail:String?,action:()->Unit){
    val material=Material
    Row(Modifier.fillMaxWidth().clickable(onClick=action).heightIn(min=57.dp).padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically){
        if(material){Column(Modifier.weight(1f)){Label(title,15);if(detail!=null){Spacer(Modifier.height(2.dp));Label(detail,12,Secondary)}}}
        else{Label(title,14,modifier=Modifier.weight(1f));if(detail!=null)Label(detail,11,Secondary,modifier=Modifier.padding(start=8.dp,end=8.dp));Label("›",20,Secondary)}
    }
}
@Composable fun PrimaryButton(text:String,primary:Boolean=true,enabled:Boolean=true,action:()->Unit){
    if(Material){
        val modifier=Modifier.fillMaxWidth().height(52.dp)
        if(primary)Button(action,modifier,enabled=enabled){Label(text,15,MaterialTheme.colorScheme.onPrimary,true)}
        else FilledTonalButton(action,modifier,enabled=enabled){Label(text,15,MaterialTheme.colorScheme.onSecondaryContainer,true)}
        return
    }
    val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).98f else 1f,spring(stiffness=650f),label="press")
    Box(Modifier.fillMaxWidth().height(52.dp).graphicsLayer{scaleX=scale;scaleY=scale;alpha=if(enabled)1f else .45f}.background(if(primary)White else Card,RoundedCornerShape(16.dp)).clickable(interactionSource=interactions,indication=null,enabled=enabled,onClick=action),contentAlignment=Alignment.Center){Label(text,15,if(primary)Color.Black else White,true)}
}
@Composable fun SecondaryAction(text:String,action:()->Unit){
    if(Material)TextButton(action,Modifier.fillMaxWidth()){Label(text,14,Blue,true)}
    else Box(Modifier.fillMaxWidth().height(44.dp).clickable(onClick=action),contentAlignment=Alignment.Center){Label(text,14,Secondary)}
}
@Composable private fun SnapSlider(title:String,initial:Int,min:Int,max:Int,unit:String,onValue:(Int)->Unit){
    var value by remember{mutableFloatStateOf(initial.toFloat())}
    val colors=if(Material)SliderDefaults.colors() else SliderDefaults.colors(thumbColor=White,activeTrackColor=White,inactiveTrackColor=Color.White.copy(alpha=.12f))
    Column(Modifier.padding(horizontal=16.dp,vertical=12.dp)){Row{Label(title,13,modifier=Modifier.weight(1f));Label("${value.toInt()} $unit",12,Secondary)};Slider(value,{value=kotlin.math.round(it);onValue(value.toInt())},valueRange=min.toFloat()..max.toFloat(),steps=max-min-1,colors=colors)}
}
@Composable fun VpnIcon(edge:Int=36,packageName:String?=null){
    val context=LocalContext.current;val selected=packageName?:VpnClientBridge.iconPackage(context);val tint=White
    val pixelEdge=kotlin.math.round(edge*context.resources.displayMetrics.density).toInt().coerceIn(96,256)
    val bitmap=remember(selected,pixelEdge){try{val d=context.packageManager.getApplicationIcon(selected);Bitmap.createBitmap(pixelEdge,pixelEdge,Bitmap.Config.ARGB_8888).also{d.setBounds(0,0,pixelEdge,pixelEdge);d.draw(NativeCanvas(it))}.asImageBitmap()}catch(_:Exception){null}}
    if(bitmap!=null)Image(bitmap,null,Modifier.size(edge.dp))else Canvas(Modifier.size(edge.dp)){
        drawArc(tint,-45f,270f,false,Offset(size.width*.15f,size.height*.15f),androidx.compose.ui.geometry.Size(size.width*.7f,size.height*.7f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        drawLine(tint,Offset(size.width*.5f,size.height*.05f),Offset(size.width*.5f,size.height*.5f),2.dp.toPx(),StrokeCap.Round)
    }
}
@Composable fun IslandPreview(modifier:Modifier=Modifier,client:String?=null,connected:Boolean=false,revision:Int=0){
    val context=LocalContext.current;val settings=remember{IslandSettings(context)}
    val island=remember(revision){IslandLook.from(context)}
    val insets=(context as? Activity)?.window?.decorView?.rootWindowInsets;val density=context.resources.displayMetrics.density
    val s31=android.os.Build.VERSION.SDK_INT>=31
    val radius=IslandShape.radius((if(s31)insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?:0 else 0)/density,(if(s31)insets?.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius?:0 else 0)/density,settings.autoCorners(),settings.corners().toFloat())
    Box(modifier.background(island.fillColor,RoundedCornerShape(radius.dp))){
        Row(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            CountryFlag(settings.vpnCountryCode(),46);Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)){
                Text(settings.vpnCountry(),color=island.secondary,fontFamily=island.font,fontSize=13.sp,maxLines=1)
                Text(if(connected)settings.vpnCity()else"Готов к работе",color=island.text,fontFamily=island.font,fontSize=19.sp,fontWeight=if(island.material)FontWeight.Medium else FontWeight.Bold,maxLines=1)
            }
            CloseBadge(island,44)
        }
    }
}
/** Close control: a red disc in the classic style, a tonal icon button in Material 3. */
@Composable fun CloseBadge(island:IslandLook,edge:Int){
    Canvas(Modifier.size(edge.dp)){
        drawCircle(island.action)
        val inset=size.width*(if(island.material).36f else .34f)
        drawLine(island.onAction,Offset(inset,inset),Offset(size.width-inset,size.height-inset),2.dp.toPx(),StrokeCap.Round)
        drawLine(island.onAction,Offset(size.width-inset,inset),Offset(inset,size.height-inset),2.dp.toPx(),StrokeCap.Round)
    }
}
@Composable private fun AppGlyph(kind:Int,color:Color){val hole=Card;Canvas(Modifier.size(24.dp)){
    val stroke=Stroke(1.8.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round)
    when(kind){
        0->drawRoundRect(color,Offset(size.width*.08f,size.height*.28f),androidx.compose.ui.geometry.Size(size.width*.84f,size.height*.44f),CornerRadius(size.height*.22f),style=stroke)
        1->{val p=Path().apply{moveTo(size.width*.5f,size.height*.08f);lineTo(size.width*.83f,size.height*.22f);lineTo(size.width*.8f,size.height*.57f);quadraticTo(size.width*.75f,size.height*.78f,size.width*.5f,size.height*.92f);quadraticTo(size.width*.25f,size.height*.78f,size.width*.2f,size.height*.57f);lineTo(size.width*.17f,size.height*.22f);close()};drawPath(p,color,style=stroke)}
        2->{val p=Path().apply{moveTo(size.width*.22f,size.height*.7f);lineTo(size.width*.22f,size.height*.45f);quadraticTo(size.width*.22f,size.height*.14f,size.width*.5f,size.height*.14f);quadraticTo(size.width*.78f,size.height*.14f,size.width*.78f,size.height*.45f);lineTo(size.width*.78f,size.height*.7f);lineTo(size.width*.87f,size.height*.78f);lineTo(size.width*.13f,size.height*.78f);close()};drawPath(p,color,style=stroke);drawLine(color,Offset(size.width*.42f,size.height*.9f),Offset(size.width*.58f,size.height*.9f),1.8.dp.toPx(),StrokeCap.Round)}
        else->{for(i in 0..2){val y=size.height*(.22f+i*.28f);val x=size.width*(if(i==1).35f else .65f);drawLine(color,Offset(size.width*.12f,y),Offset(size.width*.88f,y),1.8.dp.toPx(),StrokeCap.Round);drawCircle(hole,3.4.dp.toPx(),Offset(x,y));drawCircle(color,3.4.dp.toPx(),Offset(x,y),style=stroke)}}
    }
}}
