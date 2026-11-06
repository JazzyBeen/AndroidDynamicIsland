package io.github.jazzybeen.androiddynamicisland.activities

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.delay
import io.github.jazzybeen.androiddynamicisland.ui.IslandFont

class ActivityContent(context:Context,private val compact:Boolean):LifecycleOwner,SavedStateRegistryOwner,ViewModelStoreOwner {
    private val registry=LifecycleRegistry(this);private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore();val view=ComposeView(context)
    private var card by mutableStateOf<ActivityCard?>(null)
    private var active by mutableStateOf(false)
    var onCollapse:Runnable?=null
    init{
        saved.performAttach();saved.performRestore(null);registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        view.setViewTreeLifecycleOwner(this);view.setViewTreeSavedStateRegistryOwner(this);view.setViewTreeViewModelStoreOwner(this)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent{val c=card?:return@setContent
            MaterialTheme(colorScheme=darkColorScheme(primary=Color.White)){
                if(compact)Row(Modifier.fillMaxSize().background(Color.Black).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
                    Artwork(c,25);Spacer(Modifier.weight(1f));if(c.media!=null)MediaGlyph(if(c.media!!.playing)"levels"else"pause",Color(0xFF30D158),Modifier.size(22.dp))else Text("●",color=Color.White,fontSize=12.sp)
                }else Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal=18.dp).padding(top=28.dp,bottom=14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Artwork(c,48);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){
                            Text(c.title,fontFamily=IslandFont,color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp));Text(if(c.kind==ActivityKind.MEDIA)c.text.ifBlank{c.app}else c.app,fontFamily=IslandFont,color=Color.Gray,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        };Box(Modifier.size(36.dp).clickable{onCollapse?.run()},contentAlignment=Alignment.Center){Text("×",color=Color.Gray,fontSize=24.sp)}
                    }
                    val media=c.media
                    if(media!=null)MediaControls(media,active)else{
                        Spacer(Modifier.height(12.dp));Text(c.text,fontFamily=IslandFont,color=Color(0xFFDDDDDF),fontSize=14.sp,maxLines=4,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                        if(c.open!=null)Text("Открыть",fontFamily=IslandFont,color=Color(0xFF64D2FF),fontSize=13.sp,modifier=Modifier.padding(top=8.dp).clickable{runCatching{c.open.send()};ActivityHub.dismiss()})
                    }
                }
            }
        };registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun update(value:ActivityCard){card=value;active=true}
    fun pause(){active=false}
    fun close(){active=false;registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);view.disposeComposition();viewModelStore.clear()}
}
@Composable private fun Artwork(card:ActivityCard,edge:Int){
    val bitmap=card.image
    if(bitmap!=null)Image(bitmap.asImageBitmap(),null,Modifier.size(edge.dp).clip(RoundedCornerShape((edge*.25f).dp)))
    else Box(Modifier.size(edge.dp).background(Color(0xFF252528),RoundedCornerShape(9.dp)),contentAlignment=Alignment.Center){Text(card.app.take(1),color=Color.White,fontSize=(edge*.5).sp)}
}
@Composable private fun MediaControls(state:MediaState,active:Boolean){
    var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
    var drag by remember{mutableStateOf<Float?>(null)}
    var pending by remember{mutableStateOf<Pair<Long,Long>?>(null)}
    LaunchedEffect(active,state.playing,state.updated){if(active){do{now=SystemClock.elapsedRealtime();delay(250)}while(state.playing||pending!=null)}}
    val position=state.positionAt(now)
    val optimistic=pending?.takeIf{now-it.second<1800&&kotlin.math.abs(position-it.first)>1500}?.first
    val value=drag?:((optimistic?:position).toFloat())
    Spacer(Modifier.height(5.dp))
    Slider(value=value.coerceIn(0f,maxOf(1,state.duration).toFloat()),onValueChange={drag=it},onValueChangeFinished={drag?.let{val target=it.toLong();pending=target to SystemClock.elapsedRealtime();MediaSource.active?.command("seek",target)};drag=null},valueRange=0f..maxOf(1,state.duration).toFloat(),enabled=state.canSeek&&state.duration>0,modifier=Modifier.height(32.dp),colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White,inactiveTrackColor=Color(0xFF38383A)))
    Row(Modifier.fillMaxWidth()){Text(time(value.toLong()),color=Color.Gray,fontSize=10.sp);Spacer(Modifier.weight(1f));Text(if(state.duration>0)time(state.duration)else"Прямой эфир",color=Color.Gray,fontSize=10.sp)}
    Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){
        Control("previous",state.canPrevious,"Предыдущий трек")
        Control(if(state.playing)"pause"else"play",if(state.playing)state.canPause else state.canPlay,if(state.playing)"Пауза"else"Воспроизвести")
        Control("next",state.canNext,"Следующий трек")
    }
}
private fun time(ms:Long):String{val seconds=ms.coerceAtLeast(0)/1000;return "%d:%02d".format(seconds/60,seconds%60)}
@Composable private fun Control(action:String,enabled:Boolean,label:String){
    Box(Modifier.size(48.dp).clickable(enabled=enabled,onClickLabel=label){MediaSource.active?.command(action)},contentAlignment=Alignment.Center){MediaGlyph(action,if(enabled)Color.White else Color(0xFF444448),Modifier.size(27.dp))}
}
@Composable private fun MediaGlyph(kind:String,color:Color,modifier:Modifier){Canvas(modifier){
    val w=size.width;val h=size.height
    fun triangle(x:Float,flip:Boolean=false){val p=Path();p.moveTo(w*x,h*.15f);p.lineTo(w*(x+if(flip)-.36f else .36f),h*.5f);p.lineTo(w*x,h*.85f);p.close();drawPath(p,color)}
    when(kind){
        "pause"->{drawRoundRect(color,Offset(w*.2f,h*.12f),androidx.compose.ui.geometry.Size(w*.2f,h*.76f),androidx.compose.ui.geometry.CornerRadius(w*.04f));drawRoundRect(color,Offset(w*.6f,h*.12f),androidx.compose.ui.geometry.Size(w*.2f,h*.76f),androidx.compose.ui.geometry.CornerRadius(w*.04f))}
        "play"->{val p=Path().apply{moveTo(w*.23f,h*.1f);lineTo(w*.85f,h*.5f);lineTo(w*.23f,h*.9f);close()};drawPath(p,color)}
        "previous"->{triangle(.51f,true);triangle(.86f,true);drawRect(color,Offset(w*.08f,h*.17f),androidx.compose.ui.geometry.Size(w*.1f,h*.66f))}
        "next"->{triangle(.14f);triangle(.49f);drawRect(color,Offset(w*.82f,h*.17f),androidx.compose.ui.geometry.Size(w*.1f,h*.66f))}
        else->for(i in 0..3){val height=h*(if(i%2==0).5f else .9f);drawLine(color,Offset(w*(.15f+i*.23f),(h-height)/2),Offset(w*(.15f+i*.23f),(h+height)/2),w*.1f,StrokeCap.Round)}
    }
}}
