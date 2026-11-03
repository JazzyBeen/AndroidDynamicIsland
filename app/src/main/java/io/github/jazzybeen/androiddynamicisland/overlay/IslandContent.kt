package io.github.jazzybeen.androiddynamicisland.overlay

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.ui.CountryFlag
import io.github.jazzybeen.androiddynamicisland.ui.IslandFont

/** Two content layouts share one animated silhouette; neither samples the screen behind it. */
class IslandContent(context:Context,private val compact:Boolean):LifecycleOwner,SavedStateRegistryOwner,ViewModelStoreOwner{
    private val registry=LifecycleRegistry(this)
    private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore()
    val view=ComposeView(context)
    private var message by mutableStateOf("")
    private var complete by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var country by mutableStateOf("VPN")
    private var city by mutableStateOf("Мой сервер")
    private var code by mutableStateOf("")
    private var response by mutableIntStateOf(-1)
    private var info by mutableStateOf("")
    init{
        saved.performAttach();saved.performRestore(null);registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        attachToRoot(view);view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent{
            Box(Modifier.fillMaxSize().background(Color.Black,RoundedCornerShape(50))){
                if(compact){
                    Row(Modifier.fillMaxSize().padding(start=7.dp,end=13.dp),verticalAlignment=Alignment.CenterVertically){
                        CountryFlag(code,26);Spacer(Modifier.weight(1f))
                        // Keep the middle clear for the physical camera, including multiword alerts.
                        Text(info.ifBlank{if(connected&&response>0)"$response мс"else if(connected)"Вкл."else "Выкл."},modifier=Modifier.widthIn(max=76.dp),fontFamily=IslandFont,color=if(info.isNotBlank()&&!connected)Color(0xFFFF6961)else Color.White,fontSize=if(info.isBlank())15.sp else 12.sp,lineHeight=if(info.isBlank())20.sp else 14.sp,maxLines=if(info.isBlank())1 else 2,overflow=TextOverflow.Ellipsis)
                    }
                }else{
                    Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
                        CountryFlag(code,48);Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text(country,fontFamily=IslandFont,color=Color(0xFF8E8E93),fontSize=13.sp,lineHeight=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(if(complete&&connected)city else message,fontFamily=IslandFont,fontWeight=FontWeight.SemiBold,color=Color.White,fontSize=19.sp,lineHeight=23.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(10.dp))
                        Canvas(Modifier.size(48.dp)){
                            drawCircle(Color(0xFFFF3B30))
                            val inset=size.width*.34f
                            drawLine(Color.White,Offset(inset,inset),Offset(size.width-inset,size.height-inset),2.dp.toPx(),StrokeCap.Round)
                            drawLine(Color.White,Offset(size.width-inset,inset),Offset(inset,size.height-inset),2.dp.toPx(),StrokeCap.Round)
                        }
                    }
                }
            }
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    @JvmOverloads fun update(text:String,complete:Boolean,connected:Boolean,notice:String=""){
        this.message=text;this.complete=complete;this.connected=connected
        info=notice
        val settings=IslandSettings(view.context);country=settings.vpnCountry();city=settings.vpnCity();code=settings.vpnCountryCode();response=settings.responseTime()
    }
    fun attachToRoot(root:android.view.View){root.setViewTreeLifecycleOwner(this);root.setViewTreeSavedStateRegistryOwner(this);root.setViewTreeViewModelStoreOwner(this)}
    fun close(){registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);view.disposeComposition();viewModelStore.clear()}
}
