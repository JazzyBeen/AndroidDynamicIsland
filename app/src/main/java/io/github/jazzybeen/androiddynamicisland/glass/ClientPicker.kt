package io.github.jazzybeen.androiddynamicisland.glass

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener
import io.github.jazzybeen.androiddynamicisland.vpn.VpnClientBridge
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ClientPicker {
    @JvmStatic fun show(activity:Activity,onSelected:Runnable){
        val dialog=ComponentDialog(activity)
        val view=ComposeView(dialog.context)
        view.setContent {
            val backdrop=rememberCanvasBackdrop{drawRect(Brush.verticalGradient(listOf(Color(0xFF29292B),Color(0xFF101012))))}
            var clients by remember{mutableStateOf<List<VpnClientBridge.Client>?>(null)}
            LaunchedEffect(Unit){clients=withContext(Dispatchers.IO){VpnClientBridge.discover(activity)}}
            Column(Modifier.fillMaxWidth().padding(20.dp).liquidSurface(backdrop,32f,.04f).padding(24.dp)){
                Label("VPN-приложение",23,Color.White,true)
                Spacer(Modifier.height(10.dp));Label("Выбери установленный клиент. Его ключи и серверы остаются в нём.",13,Color(0xFFAEAEAF))
                Spacer(Modifier.height(16.dp))
                val available=clients
                if(available==null)Label("Поиск приложений…",13,Color(0xFFAEAEAF))
                else if(available.isEmpty())Label("VPN-приложения не найдены. Сначала установи и настрой свой VPN-клиент.",13,Color(0xFFAEAEAF))
                else LazyColumn(Modifier.heightIn(max=360.dp)){
                    items(available,key={it.packageName()}){client->
                        Row(Modifier.fillMaxWidth().clickable {
                            IslandSettings(activity).vpnPackage(client.packageName())
                            VpnCoordinator.get(activity).clientChanged()
                            VpnNotificationListener.refreshSelection()
                            IslandAccessibilityService.current()?.configureClient()
                            dialog.dismiss();onSelected.run()
                        }.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){VpnIcon(35,client.packageName());Spacer(Modifier.width(14.dp));Label(client.label(),15,Color.White)}
                    }
                }
                Spacer(Modifier.height(18.dp));Box(Modifier.fillMaxWidth().height(44.dp).clickable{dialog.dismiss()},contentAlignment=Alignment.Center){Label("Отмена",13,Color(0xFFAEAEAF))}
            }
        }
        dialog.setContentView(view);dialog.window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT));dialog.window?.setDimAmount(.82f);dialog.show()
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
