package io.github.jazzybeen.androiddynamicisland.glass

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge

/** Compose glass over a short-lived, opt-in image of the window underneath. */
class LiquidIsland(context:Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry=LifecycleRegistry(this)
    private val saved=SavedStateRegistryController.create(this)
    override val lifecycle:Lifecycle get()=registry
    override val savedStateRegistry:SavedStateRegistry get()=saved.savedStateRegistry
    override val viewModelStore=ViewModelStore()
    val view=ComposeView(context)
    private var title by mutableStateOf("VPN")
    private var message by mutableStateOf("Подключение…")
    private var complete by mutableStateOf(false)
    private var connected by mutableStateOf(false)
    private var cameraSpace by mutableFloatStateOf(30f)
    private var cornerRadius by mutableFloatStateOf(32f)
    private var clientPackage by mutableStateOf(VpnClientBridge.selectedPackage(context))
    private var background by mutableStateOf<ImageBitmap?>(null)
    private var materialLeft by mutableFloatStateOf(0f)
    private var materialTop by mutableFloatStateOf(0f)
    init {
        saved.performAttach(); saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            val image=background
            val imageLeft=materialLeft
            val imageTop=materialTop
            val backdrop=rememberLayerBackdrop()
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius.dp)).layerBackdrop(backdrop)) {
                    if(image!=null)drawImage(image,topLeft=Offset(-imageLeft,-imageTop))
                    else drawRect(Brush.linearGradient(listOf(Color(0xFF252527),Color(0xFF121214),Color(0xFF09090A))))
                }
                // Backdrop 1.x caches its draw node. Replace only the material when the source frame changes.
                key(image) { Box(Modifier.fillMaxSize().liquidSurface(backdrop,cornerRadius,.025f,island=true)) }
                Row(Modifier.align(Alignment.BottomStart).padding(start=19.dp,end=19.dp,bottom=18.dp).padding(top=cameraSpace.dp),verticalAlignment=Alignment.CenterVertically) {
                    VpnIcon(36,clientPackage);Spacer(Modifier.width(13.dp))
                    Column { Label(title,15,Color.White,true,shadow=true);Label(message,11,if(!complete)Color(0xFFD1D1D6)else if(connected)Color(0xFF9CE5BE)else Color(0xFFC7C7CC),shadow=true) }
                }
            }
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START);registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun update(title:String,message:String,complete:Boolean,connected:Boolean,cameraSpace:Float,cornerRadius:Float) {
        this.title=title;this.message=message;this.complete=complete;this.connected=connected;this.cameraSpace=cameraSpace;this.cornerRadius=cornerRadius
        clientPackage=VpnClientBridge.selectedPackage(view.context)
    }
    fun attachToRoot(root:android.view.View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
    }
    fun setBackdrop(bitmap:Bitmap?,left:Float,top:Float){background=bitmap?.asImageBitmap();materialLeft=left;materialTop=top}
    fun close() { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);view.disposeComposition();viewModelStore.clear() }
}
