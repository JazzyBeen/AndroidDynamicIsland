package io.github.jazzybeen.androiddynamicisland.navigation

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.jazzybeen.androiddynamicisland.ui.IslandFont

class NavigationContent(context:Context,private val compact:Boolean):LifecycleOwner,SavedStateRegistryOwner,ViewModelStoreOwner{
    private val registry=LifecycleRegistry(this);private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore();val view=ComposeView(context)
    private var snapshot by mutableStateOf<NavigationSnapshot?>(null)
    private var details by mutableStateOf(false)
    private var routeRevision by mutableIntStateOf(0)
    var onCollapse:Runnable?=null
    init{
        saved.performAttach();saved.performRestore(null);registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);attachToRoot(view)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent{
            val s=snapshot?:return@setContent
            if(compact)BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)){
                val textWidth=maxOf(26.dp,maxWidth/2-25.dp)
                Row(Modifier.fillMaxSize().padding(start=10.dp,end=12.dp),verticalAlignment=Alignment.CenterVertically){
                    ManeuverIcon(s.maneuver,Modifier.size(23.dp));Spacer(Modifier.weight(1f));Text(s.compactLabel,fontFamily=IslandFont,color=if(s.stale)Color.Gray else Color.White,fontSize=13.sp,maxLines=1,modifier=Modifier.widthIn(max=textWidth),overflow=TextOverflow.Ellipsis)
                }
            }else Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal=18.dp).padding(top=28.dp,bottom=14.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    ManeuverIcon(s.maneuver,Modifier.size(30.dp));Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){Text(if(s.demo)"ДЕМОНСТРАЦИЯ"else if(s.key=="shared")"ПРЕДПРОСМОТР"else if(s.stale)"ОЖИДАЕМ ОБНОВЛЕНИЕ"else "ЯНДЕКС КАРТЫ",color=Color(0xFF8E8E93),fontSize=10.sp,fontFamily=IslandFont);Spacer(Modifier.height(4.dp));Text(if(s.stale)"Подсказка пока не обновилась"else s.instruction,color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold,fontFamily=IslandFont,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    Spacer(Modifier.width(8.dp));Box(Modifier.size(28.dp).background(Color(0xFF29292D),RoundedCornerShape(50)).clickable{onCollapse?.run()},contentAlignment=Alignment.Center){Text("×",color=Color.White,fontSize=23.sp)}
                }
                if(details){
                    Spacer(Modifier.height(12.dp))
                    var legs by remember(routeRevision){mutableStateOf<List<RouteLeg>>(emptyList())}
                    val route=remember(routeRevision){NavigationHub.route};val keyPresent=remember(routeRevision){MapKeyStore(view.context).read().isNotBlank()}
                    val listOnly=s.transfer&&route==null&&!keyPresent
                    if(!listOnly)Box(Modifier.fillMaxWidth().height(if(keyPresent&&!s.demo)156.dp else 132.dp).background(Color(0xFF151517),RoundedCornerShape(16.dp))){
                        when{
                            s.demo->Column(Modifier.padding(15.dp)){Text("Предпросмотр интерфейса",color=Color.White,fontFamily=IslandFont,fontSize=14.sp);Spacer(Modifier.height(10.dp));Text("В демонстрации нет настоящей карты и маршрута.",color=Color.Gray,fontFamily=IslandFont,fontSize=12.sp)}
                            !keyPresent->Column(Modifier.padding(15.dp)){Text("Карта ещё не подключена",color=Color.White,fontFamily=IslandFont,fontSize=14.sp);Spacer(Modifier.height(8.dp));Text("Добавь бесплатный ключ MapKit в разделе «Навигация». Подсказки из уведомлений уже работают без ключа.",color=Color.Gray,fontFamily=IslandFont,fontSize=12.sp)}
                            
                            else->key(routeRevision){AndroidView(factory={MapRouteView(it,route){result->legs=result}},modifier=Modifier.fillMaxSize(),onRelease={it.dispose()})}
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){
                        if(route!=null)Text("Маршрут MapKit · может отличаться от маршрута в Картах",color=Color(0xFF8E8E93),fontFamily=IslandFont,fontSize=10.sp)
                        val rows=if(legs.isNotEmpty())legs else if(s.transfer)s.lines.map{RouteLeg(it,"",android.graphics.Color.GRAY)}else emptyList()
                        rows.forEachIndexed{index,leg->Row(Modifier.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(22.dp).background(Color(leg.color or (0xFF shl 24)),RoundedCornerShape(50)),contentAlignment=Alignment.Center){Text("${index+1}",color=Color.White,fontSize=10.sp)};Spacer(Modifier.width(10.dp));Column{Text(leg.title,color=Color.White,fontFamily=IslandFont,fontSize=13.sp);if(leg.detail.isNotBlank())Text(leg.detail,color=Color.Gray,fontFamily=IslandFont,fontSize=11.sp)}}}
                        if(rows.isEmpty()&&s.detail.isNotBlank())Text(s.detail,color=Color.Gray,fontFamily=IslandFont,fontSize=12.sp)
                    }
                }else if(s.detail.isNotBlank())Text(s.detail,color=Color(0xFF8E8E93),fontSize=12.sp,fontFamily=IslandFont,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=8.dp))
            }
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun update(value:NavigationSnapshot,expandedDetails:Boolean){snapshot=value;details=expandedDetails;routeRevision=NavigationHub.routeRevision}
    fun pauseMap(){details=false}
    fun attachToRoot(root:android.view.View){root.setViewTreeLifecycleOwner(this);root.setViewTreeSavedStateRegistryOwner(this);root.setViewTreeViewModelStoreOwner(this)}
    fun close(){registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);view.disposeComposition();viewModelStore.clear()}
}

@Composable fun ManeuverIcon(kind:Maneuver,modifier:Modifier=Modifier){
    Canvas(modifier){
        val c=Color(0xFF64D2FF);val w=size.width;val h=size.height;val stroke=Stroke(w*.105f,cap=StrokeCap.Round,join=StrokeJoin.Round)
        val p=Path()
        when(kind){
            Maneuver.LEFT->{p.moveTo(w*.65f,h*.85f);p.lineTo(w*.65f,h*.32f);p.lineTo(w*.16f,h*.32f);p.moveTo(w*.38f,h*.1f);p.lineTo(w*.16f,h*.32f);p.lineTo(w*.38f,h*.55f)}
            Maneuver.RIGHT->{p.moveTo(w*.35f,h*.85f);p.lineTo(w*.35f,h*.32f);p.lineTo(w*.84f,h*.32f);p.moveTo(w*.62f,h*.1f);p.lineTo(w*.84f,h*.32f);p.lineTo(w*.62f,h*.55f)}
            Maneuver.TRANSFER->{p.moveTo(w*.12f,h*.3f);p.lineTo(w*.88f,h*.3f);p.lineTo(w*.65f,h*.1f);p.moveTo(w*.88f,h*.7f);p.lineTo(w*.12f,h*.7f);p.lineTo(w*.35f,h*.9f)}
            Maneuver.ARRIVAL->{drawCircle(c,w*.35f,Offset(w*.5f,h*.5f),style=stroke);p.moveTo(w*.3f,h*.5f);p.lineTo(w*.45f,h*.65f);p.lineTo(w*.72f,h*.36f)}
            Maneuver.UTURN->{p.moveTo(w*.25f,h*.85f);p.lineTo(w*.25f,h*.35f);p.cubicTo(w*.25f,-h*.05f,w*.78f,-h*.05f,w*.78f,h*.35f);p.lineTo(w*.78f,h*.7f);p.moveTo(w*.58f,h*.5f);p.lineTo(w*.78f,h*.7f);p.lineTo(w*.97f,h*.5f)}
            else->{p.moveTo(w*.5f,h*.9f);p.lineTo(w*.5f,h*.1f);p.moveTo(w*.2f,h*.4f);p.lineTo(w*.5f,h*.1f);p.lineTo(w*.8f,h*.4f)}
        };drawPath(p,c,style=stroke)
    }
}
