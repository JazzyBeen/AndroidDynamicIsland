package io.github.jazzybeen.androiddynamicisland.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.IslandAccessibilityService
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener
import io.github.jazzybeen.androiddynamicisland.vpn.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ClientPicker{
    fun show(activity:Activity,onSelected:()->Unit){
        val dialog=ComponentDialog(activity)
        dialog.setContentView(ComposeView(dialog.context).apply{setContent{AppTheme{
            val look=LocalLook.current
            var clients by remember{mutableStateOf<List<VpnClientBridge.Client>?>(null)}
            LaunchedEffect(Unit){clients=withContext(Dispatchers.IO){VpnClientBridge.discover(activity)}}
            Column(Modifier.fillMaxWidth().padding(20.dp).background(if(look.material)androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh else look.card,RoundedCornerShape(28.dp)).padding(24.dp)){
                Label("VPN-приложение",24,bold=!look.material);Spacer(Modifier.height(8.dp));Label("Выбери установленный клиент",13,look.secondary);Spacer(Modifier.height(20.dp))
                val available=clients
                if(available==null)Label("Поиск…",14)
                else if(available.isEmpty())Label("Сначала установи и настрой VPN-клиент.",14)
                else LazyColumn(Modifier.heightIn(max=360.dp)){items(available,key={it.packageName()}){client->
                    Row(Modifier.fillMaxWidth().clickable{
                        IslandSettings(activity).apply{embeddedVpn(false);vpnPackage(client.packageName())};VpnCoordinator.get(activity).clientChanged();VpnNotificationListener.refreshSelection();IslandAccessibilityService.current()?.configureClient();dialog.dismiss();onSelected()
                    }.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){VpnIcon(38,client.packageName());Spacer(Modifier.width(14.dp));Label(client.label(),16)}
                }}
                Spacer(Modifier.height(12.dp));Box(Modifier.fillMaxWidth().height(44.dp).clickable{dialog.dismiss()},contentAlignment=Alignment.Center){Label("Отмена",14,look.accent)}
            }
        }}})
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.window?.setDimAmount(.75f);dialog.show();dialog.window?.setLayout(-1,-2)
    }
}
