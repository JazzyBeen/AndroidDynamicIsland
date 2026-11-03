package io.github.jazzybeen.androiddynamicisland.overlay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import io.github.jazzybeen.androiddynamicisland.ui.Label
import io.github.jazzybeen.androiddynamicisland.ui.VpnIcon
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge

/** An opaque black Compose surface; it never requests or samples the screen behind it. */
class IslandContent(context:Context):LifecycleOwner,SavedStateRegistryOwner,ViewModelStoreOwner{
    private val registry=LifecycleRegistry(this)
    private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore()
    val view=ComposeView(context)
    private var title by mutableStateOf("VPN")
    private var message by mutableStateOf("")
    private var complete by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var cameraSpace by mutableFloatStateOf(30f)
    private var radius by mutableFloatStateOf(32f)
    private var client by mutableStateOf(VpnClientBridge.selectedPackage(context))
    init{
        saved.performAttach();saved.performRestore(null);registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        attachToRoot(view)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent{
            Box(Modifier.fillMaxSize().background(Color.Black,RoundedCornerShape(radius.dp))){
                Row(Modifier.align(Alignment.BottomStart).padding(start=20.dp,end=20.dp,bottom=18.dp).padding(top=cameraSpace.dp),verticalAlignment=Alignment.CenterVertically){
                    VpnIcon(36,client);Spacer(Modifier.width(13.dp))
                    Column{Label(title,15,Color.White,true);Spacer(Modifier.height(3.dp));Label(message,12,if(complete&&connected)Color(0xFF30D158)else Color(0xFFB0B0B7))}
                }
            }
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun update(title:String,message:String,complete:Boolean,connected:Boolean,cameraSpace:Float,cornerRadius:Float){this.title=title;this.message=message;this.complete=complete;this.connected=connected;this.cameraSpace=cameraSpace;radius=cornerRadius;client=VpnClientBridge.selectedPackage(view.context)}
    fun attachToRoot(root:android.view.View){root.setViewTreeLifecycleOwner(this);root.setViewTreeSavedStateRegistryOwner(this);root.setViewTreeViewModelStoreOwner(this)}
    fun close(){registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);view.disposeComposition();viewModelStore.clear()}
}
